package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** RHL1 cross-runtime wire format and negative security tests, synthetic keys only. */
public class LicenseLeaseVerifierTest {
    private static final long NOW = 1791600000000L;
    private static final String TENANT = "f12b2687-09a1-40b5-b47a-747d75ce5551";
    private static final String DEVICE = "d94123bf-2c94-4af4-9d5b-7ab8ce690509";
    private static final String KID = "synthetic-owner-v1";
    private Ed25519PrivateKeyParameters privateKey;
    private byte[] publicKey;

    @BeforeEach
    public void init() {
        privateKey = new Ed25519PrivateKeyParameters(new SecureRandom());
        publicKey = privateKey.generatePublicKey().getEncoded();
    }
    private String utc(long ms) {
        return java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));
    }
    private LinkedHashMap<String, Object> claims() {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1);
        value.put("mode", "offline");
        value.put("leaseId", "c1dd250a-5f98-45f4-89ae-713f6a0f876e");
        value.put("tenantId", TENANT);
        value.put("installationId", DEVICE);
        value.put("issuedAt", utc(NOW - 5000));
        value.put("notBefore", utc(NOW - 5000));
        value.put("expiresAt", utc(NOW + 3600000));
        value.put("entitlementExpiresAt", utc(NOW + 86400000));
        value.put("revocationEpoch", 3);
        value.put("seatLimit", 13);
        value.put("maxInstallations", 1);
        value.put("modules", Arrays.asList("motion", "rehab", "report"));
        return value;
    }
    private String signed(Map<String, Object> payload) {
        try {
            String json = new ObjectMapper().writeValueAsString(payload);
            String prefix = "RHL1." + KID + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = prefix.getBytes(StandardCharsets.US_ASCII);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, privateKey);
            signer.update(bytes, 0, bytes.length);
            return prefix + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
    private Map<String, Object> check(String token) {
        return LicenseLeaseVerifier.verify(token, KID, publicKey, TENANT, DEVICE, NOW, 3, NOW);
    }
    private void denied(String code, String token) {
        LicenseLeaseVerifier.LicenseFailure ex = assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> check(token));
        assertEquals(code, ex.getCode());
    }

    @Test public void validSignatureAndExplicitSeatLimit() {
        assertEquals(13, check(signed(claims())).get("seatLimit"));
    }
    @Test public void explicitlyUnlimitedSeatsAllowed() {
        LinkedHashMap<String, Object> input = claims();
        input.put("seatLimit", null);
        assertNull(check(signed(input)).get("seatLimit"));
    }
    @Test public void missingOrZeroSeatLimitDenied() {
        LinkedHashMap<String, Object> input = claims();
        input.remove("seatLimit");
        denied("NON_CANONICAL_PAYLOAD", signed(input));
        input = claims();
        input.put("seatLimit", 0);
        denied("INVALID_SEAT_LIMIT", signed(input));
    }
    @Test public void signatureTamperDenied() {
        String token = signed(claims());
        String[] split = token.split("\\.");
        split[2] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"seatLimit\":99999}".getBytes(StandardCharsets.UTF_8));
        denied("INVALID_SIGNATURE", String.join(".", split));
    }
    @Test public void wrongKeyIdAndWrongKeyDenied() {
        String signed = signed(claims());
        LicenseLeaseVerifier.LicenseFailure ex = assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                        signed, "wrong-issuer", publicKey, TENANT, DEVICE, NOW, 3, NOW));
        assertEquals("UNTRUSTED_ISSUER_KEY", ex.getCode());
        ex = assertThrows(LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                signed, KID, new Ed25519PrivateKeyParameters(new SecureRandom())
                        .generatePublicKey().getEncoded(), TENANT, DEVICE, NOW, 3, NOW));
        assertEquals("INVALID_SIGNATURE", ex.getCode());
    }
    @Test public void tenantAndDeviceBindingDenied() {
        String token = signed(claims());
        assertEquals("LICENSE_BINDING_MISMATCH", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                        token, KID, publicKey, DEVICE, DEVICE, NOW, 3, NOW)).getCode());
        assertEquals("LICENSE_BINDING_MISMATCH", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                        token, KID, publicKey, TENANT, TENANT, NOW, 3, NOW)).getCode());
    }
    @Test public void long72HourLeaseDenied() {
        LinkedHashMap<String, Object> input = claims();
        input.put("expiresAt", utc(NOW - 5000 + LicenseLeaseVerifier.MAX_LEASE_MS + 1));
        input.put("entitlementExpiresAt", utc(NOW + LicenseLeaseVerifier.MAX_LEASE_MS + 90000));
        denied("INVALID_LEASE_PERIOD", signed(input));
    }
    @Test public void expiredTokenDenied() {
        LinkedHashMap<String, Object> input = claims();
        input.put("expiresAt", utc(NOW));
        denied("LICENSE_EXPIRED", signed(input));
    }
    @Test public void revokedEpochDenied() {
        String token = signed(claims());
        assertEquals("REVOKED_LEASE", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                        token, KID, publicKey, TENANT, DEVICE, NOW, 4, NOW)).getCode());
    }
    @Test public void clockRollbackDenied() {
        String token = signed(claims());
        assertEquals("INVALID_VERIFICATION_CONTEXT", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseLeaseVerifier.verify(
                        token, KID, publicKey, TENANT, DEVICE, NOW, 3, NOW + 400000)).getCode());
    }
    @Test public void modulesMustBeSorted() {
        LinkedHashMap<String, Object> input = claims();
        input.put("modules", Arrays.asList("report", "motion"));
        denied("INVALID_MODULES", signed(input));
    }
    @Test public void extraFieldDenied() {
        LinkedHashMap<String, Object> input = claims();
        input.put("owner", true);
        denied("NON_CANONICAL_PAYLOAD", signed(input));
    }
    @Test public void malformedTokenDenied() {
        denied("UNTRUSTED_ISSUER_KEY", "unknown");
    }
    @Test public void node22SignedLeaseInteroperatesWithJava8BouncyCastleVerifier() {
        // Generated by Node v22 crypto.sign(null, RHL1-prefix, Ed25519).
        // Test-only ephemeral keypair; only the synthetic public key and
        // synthetic short lease are committed, never the private key.
        String rawKey = "wkf02RerH1dIPRuDdDjeul6axv8wb0WhDB4O9LZxNyI";
        String token = "RHL1.synthetic-owner-v1."
                + "eyJzY2hlbWFWZXJzaW9uIjoxLCJtb2RlIjoib2ZmbGluZSIsImxlYXNlSWQiOiJjMWRkMjUwYS01Zjk4LTQ1ZjQtODlhZS03MTNmNmEwZjg3NmUiLCJ0ZW5hbnRJZCI6ImYxMmIyNjg3LTA5YTEtNDBiNS1iNDdhLTc0N2Q3NWNlNTU1MSIsImluc3RhbGxhdGlvbklkIjoiZDk0MTIzYmYtMmM5NC00YWY0LTlkNWItN2FiOGNlNjkwNTA5IiwiaXNzdWVkQXQiOiIyMDI2LTEwLTEwVDAyOjM5OjU1LjAwMFoiLCJub3RCZWZvcmUiOiIyMDI2LTEwLTEwVDAyOjM5OjU1LjAwMFoiLCJleHBpcmVzQXQiOiIyMDI2LTEwLTEwVDAzOjQwOjAwLjAwMFoiLCJlbnRpdGxlbWVudEV4cGlyZXNBdCI6IjIwMjYtMTAtMTFUMDI6NDA6MDAuMDAwWiIsInJldm9jYXRpb25FcG9jaCI6Mywic2VhdExpbWl0IjoxMywibWF4SW5zdGFsbGF0aW9ucyI6MSwibW9kdWxlcyI6WyJtb3Rpb24iLCJyZWhhYiIsInJlcG9ydCJdfQ"
                + ".ntEqFYM3iZNFT2h7f38R6o6GmJZ3TizTO2P2osuaMT1zKHM87X7WFzGAD1IIeHTLv6QDoob-Bs_gqUk6-KoSCw";
        Map<String, Object> claims = LicenseLeaseVerifier.verify(token, KID,
                Base64.getUrlDecoder().decode(rawKey), TENANT, DEVICE, NOW, 3, NOW);
        assertEquals(13, claims.get("seatLimit"));
        assertEquals("offline", claims.get("mode"));
    }
}
