package cn.iocoder.yudao.module.system.framework.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Node.js OwnerLicensingService-issued RHL1 + RHR1 are verified by
 * Java/BouncyCastle. Run by desktop/scripts/run-l5-cross-runtime.sh with an
 * independent ephemeral fixture. No owner private key or clinical DB involved.
 */
public class L5NodeIssuedLeaseInteropTest {
    private static JsonNode fixture;
    private static byte[] issuer;
    private static String kid;
    private static long now;

    @BeforeAll
    static void loadFixture() throws Exception {
        String name = System.getenv("REHAB_L5_NODE_FIXTURE");
        if (name == null || name.isEmpty()) {
            Assumptions.assumeTrue(false, "Cross-runtime smoke requires ephemeral Node issuer fixture");
        }
        Path path = Paths.get(name).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(path));
        assertTrue(Files.size(path) < 30000);
        fixture = new ObjectMapper().readTree(Files.readAllBytes(path));
        assertEquals("rehab-l5-node-cross-runtime/1", fixture.path("protocol").asText());
        assertEquals(2, fixture.path("studios").size());
        kid = fixture.path("issuerKid").asText();
        issuer = Base64.getUrlDecoder().decode(fixture.path("publicKeyBase64url").asText());
        assertEquals(32, issuer.length);
        now = fixture.path("nowMs").asLong();
    }

    private JsonNode studio(int index) { return fixture.get("studios").get(index); }
    private Map<String, Object> lease(JsonNode studio, long time, long epoch, long previous) {
        return LicenseLeaseVerifier.verify(
                studio.get("token").asText(), kid, issuer,
                studio.get("tenantId").asText(), studio.get("installationId").asText(),
                time, epoch, previous);
    }
    private Map<String, Object> status(JsonNode studio, String proof, long time, long previous) {
        return LicenseStatusVerifier.verify(proof, kid, issuer,
                studio.get("tenantId").asText(), studio.get("installationId").asText(),
                time, previous);
    }

    @Test void nodeSignedFiniteLeaseAndStatusPassRealJavaVerifier() {
        JsonNode a = studio(0);
        assertEquals(2, lease(a, now, 0, now).get("seatLimit"));
        assertEquals("online", lease(a, now, 0, now).get("mode"));
        assertEquals("ACTIVE", status(a, a.get("statusProof").asText(), now, now).get("status"));
    }
    @Test void nodeSignedExplicitUnlimitedLeasePassesJavaAndKeepsNull() {
        JsonNode b = studio(1);
        assertNull(lease(b, now, 0, now).get("seatLimit"));
        assertEquals("ACTIVE", status(b, b.get("statusProof").asText(), now, now).get("status"));
    }
    @Test void signedTenantAndInstallationCannotBeCopiedBetweenStudios() {
        JsonNode a = studio(0), b = studio(1);
        LicenseLeaseVerifier.LicenseFailure wrongTenant = assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        LicenseLeaseVerifier.verify(a.get("token").asText(), kid, issuer,
                                b.get("tenantId").asText(), a.get("installationId").asText(),
                                now, 0, now));
        assertEquals("LICENSE_BINDING_MISMATCH", wrongTenant.getCode());
        LicenseLeaseVerifier.LicenseFailure wrongDevice = assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class, () ->
                        LicenseLeaseVerifier.verify(a.get("token").asText(), kid, issuer,
                                a.get("tenantId").asText(), b.get("installationId").asText(),
                                now, 0, now));
        assertEquals("LICENSE_BINDING_MISMATCH", wrongDevice.getCode());
    }
    @Test void revokedStatusIsValidlySignedButCannotGrantActiveAccess() {
        JsonNode b = studio(1);
        Map<String, Object> signedRevoked = status(b, fixture.get("revokedStatusProof").asText(),
                now + 1000, now);
        assertEquals("REVOKED", signedRevoked.get("status"));
        assertEquals(1, ((Number)signedRevoked.get("revocationEpoch")).intValue());
        LicenseLeaseVerifier.LicenseFailure revokedEpoch = assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class,
                () -> lease(b, now + 1000, 1, now));
        assertEquals("REVOKED_LEASE", revokedEpoch.getCode());
    }
    @Test void ownerProofReplaysExpireAndTimeRollbackFailsAcrossJavaBoundary() {
        JsonNode a = studio(0);
        assertEquals("LICENSE_EXPIRED", assertThrows(LicenseLeaseVerifier.LicenseFailure.class,
                () -> lease(a, now + LicenseLeaseVerifier.MAX_LEASE_MS, 0, now)).getCode());
        assertEquals("INVALID_VERIFICATION_CONTEXT",
                assertThrows(LicenseLeaseVerifier.LicenseFailure.class,
                        () -> lease(a, now, 0, now + 400000)).getCode());
        assertEquals("STATUS_PROOF_EXPIRED",
                assertThrows(LicenseLeaseVerifier.LicenseFailure.class,
                        () -> status(a, a.get("statusProof").asText(),
                                now + LicenseLeaseVerifier.MAX_LEASE_MS, now)).getCode());
    }
    @Test void forgedOwnerPayloadAndNonPinnedKeyFail() {
        JsonNode a = studio(0);
        String valid = a.get("token").asText();
        String[] sections = valid.split("\\.");
        sections[2] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"seatLimit\":999999}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("INVALID_SIGNATURE", assertThrows(LicenseLeaseVerifier.LicenseFailure.class,
                () -> LicenseLeaseVerifier.verify(String.join(".", sections), kid, issuer,
                        a.get("tenantId").asText(), a.get("installationId").asText(),
                        now, 0, now)).getCode());
        assertEquals("UNTRUSTED_ISSUER_KEY", assertThrows(
                LicenseLeaseVerifier.LicenseFailure.class,
                () -> LicenseLeaseVerifier.verify(valid, "attacker", issuer,
                        a.get("tenantId").asText(), a.get("installationId").asText(),
                        now, 0, now)).getCode());
    }
}
