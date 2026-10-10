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
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.*;

/**
 * RHR1 cryptographically signed owner status. The owner status can be revoked
 * without editing a clinic DB row. Offline readers are bounded by 72h expiry.
 */
public final class LicenseStatusVerifier {
    private static final long SKEW_MS = 5L * 60 * 1000;
    private static final List<String> FIELDS = Arrays.asList("schemaVersion", "tenantId",
            "installationId", "status", "revocationEpoch", "observedAt", "expiresAt");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private LicenseStatusVerifier() {}
    private static LicenseLeaseVerifier.LicenseFailure fail(String code) {
        return new LicenseLeaseVerifier.LicenseFailure(code);
    }
    private static long parseTime(Object value) {
        if (!(value instanceof String) || !((String) value).matches(
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")) {
            throw fail("INVALID_STATUS_TIME");
        }
        try {
            long result = Instant.parse((String) value).toEpochMilli();
            String canonical = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(result));
            if (!canonical.equals(value)) throw fail("INVALID_STATUS_TIME");
            return result;
        } catch (java.time.DateTimeException ex) {
            throw fail("INVALID_STATUS_TIME");
        }
    }
    public static Map<String, Object> verify(String token, String issuerKid, byte[] rawPublicKey,
                                              String tenantId, String installationId, long nowMs,
                                              long lastTrustedTimeMs) {
        if (issuerKid == null || !issuerKid.matches("[A-Za-z0-9._-]{1,48}")
                || rawPublicKey == null || rawPublicKey.length != 32
                || tenantId == null || installationId == null
                || nowMs < 0 || lastTrustedTimeMs < 0
                || nowMs < lastTrustedTimeMs - SKEW_MS) {
            throw fail("INVALID_STATUS_CONTEXT");
        }
        if (token == null || token.length() > 8192) throw fail("INVALID_STATUS_PROOF");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4 || !"RHR1".equals(parts[0])
                || !issuerKid.equals(parts[1])) throw fail("UNTRUSTED_STATUS_ISSUER");
        if (!parts[2].matches("[A-Za-z0-9_-]+") || !parts[3].matches("[A-Za-z0-9_-]+"))
            throw fail("INVALID_STATUS_PROOF");
        final byte[] payload, signature;
        try {
            payload = Base64.getUrlDecoder().decode(parts[2]);
            signature = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException ex) { throw fail("INVALID_STATUS_PROOF"); }
        if (payload.length > 4096 || signature.length != 64
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(payload).equals(parts[2])
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(parts[3]))
            throw fail("INVALID_STATUS_PROOF");
        byte[] signed = (parts[0] + "." + parts[1] + "." + parts[2])
                .getBytes(StandardCharsets.US_ASCII);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(rawPublicKey, 0));
        verifier.update(signed, 0, signed.length);
        if (!verifier.verifySignature(signature)) throw fail("INVALID_STATUS_SIGNATURE");
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException ex) { throw fail("INVALID_STATUS_PAYLOAD"); }
        final LinkedHashMap<String, Object> claims;
        try {
            claims = MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
            if (claims == null || !FIELDS.equals(new ArrayList<>(claims.keySet()))
                    || !MAPPER.writeValueAsString(claims).equals(json))
                throw fail("NON_CANONICAL_STATUS");
        } catch (LicenseLeaseVerifier.LicenseFailure ex) { throw ex; }
        catch (Exception ex) { throw fail("INVALID_STATUS_PAYLOAD"); }
        if (!Integer.valueOf(1).equals(claims.get("schemaVersion"))
                || !tenantId.equals(claims.get("tenantId"))
                || !installationId.equals(claims.get("installationId"))) {
            throw fail("STATUS_BINDING_MISMATCH");
        }
        if (!Arrays.asList("ACTIVE", "SUSPENDED", "REVOKED", "EXPIRED", "REPLACED")
                .contains(claims.get("status"))) throw fail("INVALID_STATUS_CLAIMS");
        Object epoch = claims.get("revocationEpoch");
        if (!(epoch instanceof Integer || epoch instanceof Long)
                || ((Number) epoch).longValue() < 0) throw fail("INVALID_STATUS_CLAIMS");
        long observedAt = parseTime(claims.get("observedAt"));
        long expiresAt = parseTime(claims.get("expiresAt"));
        if (observedAt > nowMs + SKEW_MS || expiresAt <= observedAt
                || expiresAt - observedAt > LicenseLeaseVerifier.MAX_LEASE_MS) {
            throw fail("INVALID_STATUS_PERIOD");
        }
        if (nowMs >= expiresAt) throw fail("STATUS_PROOF_EXPIRED");
        return Collections.unmodifiableMap(claims);
    }
}
