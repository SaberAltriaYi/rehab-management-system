package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Exact RHL1 verifier shared by Node owner-control and Java business backend.
 * No private signer key is ever trusted or imported from the client/database.
 * Compatible with the existing Java 8 runtime (BouncyCastle 1.70).
 */
public final class LicenseLeaseVerifier {

    public static final long MAX_LEASE_MS = 72L * 60 * 60 * 1000;
    private static final long SKEW_MS = 5L * 60 * 1000;
    private static final Pattern KID = Pattern.compile("[a-zA-Z0-9._-]{1,48}");
    private static final Pattern B64 = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList(
            "schemaVersion", "mode", "leaseId", "tenantId", "installationId", "issuedAt",
            "notBefore", "expiresAt", "entitlementExpiresAt", "revocationEpoch",
            "seatLimit", "maxInstallations", "modules"));
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private LicenseLeaseVerifier() {}

    public static final class LicenseFailure extends RuntimeException {
        private final String code;
        public LicenseFailure(String code) { super(code); this.code = code; }
        public String getCode() { return code; }
    }

    private static LicenseFailure fail(String code) { return new LicenseFailure(code); }

    private static byte[] decode(String base64, int max) {
        if (!B64.matcher(base64).matches() || base64.length() > max * 2) throw fail("MALFORMED_LICENSE");
        final byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw fail("MALFORMED_LICENSE");
        }
        if (bytes.length > max || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(base64)) {
            throw fail("MALFORMED_LICENSE");
        }
        return bytes;
    }

    private static String uuid(Object value) {
        if (!(value instanceof String) || !UUID_PATTERN.matcher((String) value).matches()) {
            throw fail("INVALID_CLAIMS");
        }
        return (String) value;
    }

    private static long time(Object value) {
        if (!(value instanceof String) || !((String) value).matches(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")) {
            throw fail("INVALID_TIME");
        }
        try {
            long ms = Instant.parse((String) value).toEpochMilli();
            if (!Instant.ofEpochMilli(ms).toString().equals(
                    ((String) value).replaceAll("\\.000Z$", "Z"))) {
                // yyyy...SS.SSSZ is the protocol, not arbitrary fractional precision.
                if (!java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(ms)).equals(value)) {
                    throw fail("INVALID_TIME");
                }
            }
            return ms;
        } catch (DateTimeParseException ex) {
            throw fail("INVALID_TIME");
        }
    }

    private static long positiveInt(Object value, String code) {
        if (!(value instanceof Number) || !(value instanceof Integer || value instanceof Long)) {
            throw fail(code);
        }
        long parsed = ((Number) value).longValue();
        if (parsed < 1 || parsed > Integer.MAX_VALUE) throw fail(code);
        return parsed;
    }

    public static Map<String, Object> verify(String token, String keyId, byte[] rawPublicKey,
                                             String tenantUuid, String installationUuid,
                                             long nowMs, long trustedRevocationEpoch, long lastTrustedTimeMs) {
        if (keyId == null || !KID.matcher(keyId).matches() || rawPublicKey == null
                || rawPublicKey.length != 32 || nowMs < 0 || trustedRevocationEpoch < 0
                || lastTrustedTimeMs < 0 || nowMs < lastTrustedTimeMs - SKEW_MS) {
            throw fail("INVALID_VERIFICATION_CONTEXT");
        }
        uuid(tenantUuid);
        uuid(installationUuid);
        if (token == null || token.getBytes(StandardCharsets.UTF_8).length > 8192) throw fail("MALFORMED_LICENSE");
        String[] sections = token.split("\\.", -1);
        if (sections.length != 4 || !"RHL1".equals(sections[0])
                || !keyId.equals(sections[1])) throw fail("UNTRUSTED_ISSUER_KEY");
        byte[] signature = decode(sections[3], 64);
        byte[] payload = decode(sections[2], 4096);
        if (signature.length != 64) throw fail("INVALID_SIGNATURE");
        byte[] signed = (sections[0] + "." + sections[1] + "." + sections[2])
                .getBytes(StandardCharsets.US_ASCII);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(rawPublicKey, 0));
        verifier.update(signed, 0, signed.length);
        if (!verifier.verifySignature(signature)) throw fail("INVALID_SIGNATURE");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException ex) { throw fail("INVALID_PAYLOAD"); }
        final LinkedHashMap<String, Object> claims;
        try {
            claims = MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
            if (claims == null || !FIELDS.equals(new ArrayList<>(claims.keySet()))
                    || !MAPPER.writeValueAsString(claims).equals(json)) throw fail("NON_CANONICAL_PAYLOAD");
        } catch (LicenseFailure ex) {
            throw ex;
        } catch (Exception ex) {
            throw fail("INVALID_PAYLOAD");
        }
        if (!Integer.valueOf(1).equals(claims.get("schemaVersion")) ||
                !Arrays.asList("online", "offline").contains(claims.get("mode"))) throw fail("INVALID_CLAIMS");
        uuid(claims.get("leaseId"));
        if (!tenantUuid.equals(uuid(claims.get("tenantId")))
                || !installationUuid.equals(uuid(claims.get("installationId")))) {
            throw fail("LICENSE_BINDING_MISMATCH");
        }
        Object epoch = claims.get("revocationEpoch");
        if (!(epoch instanceof Number) || !(epoch instanceof Integer || epoch instanceof Long)
                || ((Number) epoch).longValue() < 0) throw fail("INVALID_CLAIMS");
        if (((Number) epoch).longValue() < trustedRevocationEpoch) throw fail("REVOKED_LEASE");
        Object seats = claims.get("seatLimit");
        if (seats != null) positiveInt(seats, "INVALID_SEAT_LIMIT");
        positiveInt(claims.get("maxInstallations"), "INVALID_CLAIMS");
        Object modules = claims.get("modules");
        if (!(modules instanceof List) || ((List<?>) modules).isEmpty()
                || ((List<?>) modules).size() > 64) throw fail("INVALID_MODULES");
        String previous = "";
        for (Object module : (List<?>) modules) {
            if (!(module instanceof String) || !MODULE.matcher((String) module).matches()
                    || previous.compareTo((String) module) >= 0) throw fail("INVALID_MODULES");
            previous = (String) module;
        }
        long issuedAt = time(claims.get("issuedAt"));
        long notBefore = time(claims.get("notBefore"));
        long expiry = time(claims.get("expiresAt"));
        long entitlementExpiry = time(claims.get("entitlementExpiresAt"));
        if (issuedAt > notBefore || expiry <= notBefore || expiry <= issuedAt
                || expiry - issuedAt > MAX_LEASE_MS || expiry > entitlementExpiry) {
            throw fail("INVALID_LEASE_PERIOD");
        }
        if (issuedAt > nowMs + SKEW_MS || nowMs < notBefore) throw fail("LICENSE_NOT_YET_VALID");
        if (nowMs >= expiry) throw fail("LICENSE_EXPIRED");
        return Collections.unmodifiableMap(claims);
    }
}
