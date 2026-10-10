package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseStatusVerifierTest {
    private static final String TENANT = "f12b2687-09a1-40b5-b47a-747d75ce5551";
    private static final String DEVICE = "d94123bf-2c94-4af4-9d5b-7ab8ce690509";
    private static final String KID = "test-owner";
    private static final long NOW = 1791600000000L;
    private Ed25519PrivateKeyParameters key;
    private byte[] pub;

    @BeforeEach
    public void setUp() {
        key = new Ed25519PrivateKeyParameters(new SecureRandom());
        pub = key.generatePublicKey().getEncoded();
    }
    private String time(long ms) {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));
    }
    private LinkedHashMap<String, Object> claims() {
        LinkedHashMap<String, Object> c = new LinkedHashMap<>();
        c.put("schemaVersion", 1);
        c.put("tenantId", TENANT);
        c.put("installationId", DEVICE);
        c.put("status", "ACTIVE");
        c.put("revocationEpoch", 7);
        c.put("observedAt", time(NOW - 1000));
        c.put("expiresAt", time(NOW + 3600000));
        return c;
    }
    private String token(Map<String, Object> claims) {
        try {
            String payload = new ObjectMapper().writeValueAsString(claims);
            String text = "RHR1." + KID + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            byte[] msg = text.getBytes(StandardCharsets.US_ASCII);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, key);
            signer.update(msg, 0, msg.length);
            return text + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature());
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private Map<String, Object> verify(String token) {
        return LicenseStatusVerifier.verify(token, KID, pub, TENANT, DEVICE, NOW, NOW - 2000);
    }
    private void rejected(String code, String token) {
        assertEquals(code, assertThrows(LicenseLeaseVerifier.LicenseFailure.class,
                () -> verify(token)).getCode());
    }
    @Test public void activeOwnerProofHasValidSignature() {
        assertEquals(7, verify(token(claims())).get("revocationEpoch"));
    }
    @Test public void signedRevocationCannotBeMistakenForActive() {
        Map<String, Object> c = claims();
        c.put("status", "REVOKED");
        assertEquals("REVOKED", verify(token(c)).get("status"));
    }
    @Test public void tenantBindingAndDeviceBindingProtected() {
        Map<String, Object> c = claims();
        c.put("installationId", TENANT);
        rejected("STATUS_BINDING_MISMATCH", token(c));
    }
    @Test public void expiredStatusFailsClosed() {
        Map<String, Object> c = claims();
        c.put("expiresAt", time(NOW));
        rejected("STATUS_PROOF_EXPIRED", token(c));
    }
    @Test public void statusProofCannotLastMoreThan72Hours() {
        Map<String, Object> c = claims();
        c.put("expiresAt", time(NOW + LicenseLeaseVerifier.MAX_LEASE_MS + 1000));
        rejected("INVALID_STATUS_PERIOD", token(c));
    }
    @Test public void extraOrMissingClaimsRejected() {
        Map<String, Object> c = claims();
        c.remove("revocationEpoch");
        rejected("NON_CANONICAL_STATUS", token(c));
        c = claims();
        c.put("extra", true);
        rejected("NON_CANONICAL_STATUS", token(c));
    }
    @Test public void signedInvalidStatusRejected() {
        Map<String, Object> c = claims();
        c.put("status", "UNKNOWN");
        rejected("INVALID_STATUS_CLAIMS", token(c));
    }
    @Test public void tamperingWithStatusOrIssuerFails() {
        String original = token(claims());
        String[] parts = original.split("\\.");
        parts[2] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"status\":\"ACTIVE\"}".getBytes(StandardCharsets.UTF_8));
        rejected("INVALID_STATUS_SIGNATURE", String.join(".", parts));
        rejected("UNTRUSTED_STATUS_ISSUER", original.replace("RHR1.test-owner", "RHR1.attacker"));
    }
    @Test public void trustedClockRollbackRejected() {
        assertEquals("INVALID_STATUS_CONTEXT", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () -> LicenseStatusVerifier.verify(
                        token(claims()), KID, pub, TENANT, DEVICE, NOW, NOW + 3600000)).getCode());
    }
    @Test public void node22OwnerSignedRhr1IsVerifiedByJava8BouncyCastle() {
        // One-time synthetic Node.js crypto.sign Ed25519 fixture; the private
        // key was discarded and no actual tenant or owner key is included.
        byte[] nodePublic = Base64.getUrlDecoder().decode(
                "JfbX7p5EDMkq664jYm_8XlKBcblA6CbyPx8POV9U1wo");
        String nodeToken = "RHR1.test-owner."
                + "eyJzY2hlbWFWZXJzaW9uIjoxLCJ0ZW5hbnRJZCI6ImYxMmIyNjg3LTA5YTEtNDBiNS1iNDdhLTc0N2Q3NWNlNTU1MSIsImluc3RhbGxhdGlvbklkIjoiZDk0MTIzYmYtMmM5NC00YWY0LTlkNWItN2FiOGNlNjkwNTA5Iiwic3RhdHVzIjoiQUNUSVZFIiwicmV2b2NhdGlvbkVwb2NoIjozLCJvYnNlcnZlZEF0IjoiMjAyNi0xMC0xMFQwMjozOTo1NS4wMDBaIiwiZXhwaXJlc0F0IjoiMjAyNi0xMC0xMFQwMzo0MDowMC4wMDBaIn0"
                + ".PMfQE5Z_CwwZX1awwR0CuwnNb-qWhXCK3Z4DRmQFIBXHtsyqWRSlSo3sYo6HI4AUlp6OBBz1p39pLgGxE9rbBQ";
        Map<String, Object> claims = LicenseStatusVerifier.verify(nodeToken, KID,
                nodePublic, TENANT, DEVICE, NOW, NOW - 5000);
        assertEquals("ACTIVE", claims.get("status"));
        assertEquals(3, claims.get("revocationEpoch"));
    }
}
