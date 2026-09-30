package cn.iocoder.yudao.module.rehab.service.app.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Offline-enrollment invitation primitive, NOT a login service. Expiry,
 * atomic redemption, rate limits and phone verification are separate gates;
 * patient endpoints remain closed. The key must be provisioned out of band.
 */
public final class PatientInvitationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PURPOSE = "rehab:patient-enrollment:v1:";
    private final SecretKeySpec hmacKey;

    public PatientInvitationTokens(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("患者邀请密钥至少需要 32 字节");
        }
        this.hmacKey = new SecretKeySpec(secret.clone(), "HmacSHA256");
    }

    /** 256 unpredictable bits; only disclose to the clinic-verified recipient. */
    public String generate() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Store this tenant-scoped digest, never the clear-text invitation. */
    public String digest(long tenantId, String invitation) {
        if (tenantId <= 0 || !validSyntax(invitation)) {
            throw new IllegalArgumentException("无效的邀请上下文");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] data = (PURPOSE + tenantId + ":" + invitation).getBytes(StandardCharsets.US_ASCII);
            byte[] bytes = mac.doFinal(data);
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                hex[2 * i] = Character.forDigit((bytes[i] >>> 4) & 0xf, 16);
                hex[2 * i + 1] = Character.forDigit(bytes[i] & 0xf, 16);
            }
            return new String(hex);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("邀请摘要算法不可用", ex);
        }
    }

    /** Comparison does not prove identity or consume the invitation. */
    public boolean matches(long tenantId, String invitation, String expectedDigest) {
        if (!validDigest(expectedDigest) || tenantId <= 0 || !validSyntax(invitation)) {
            return false;
        }
        byte[] expected = expectedDigest.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = digest(tenantId, invitation).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    // Digests stored by this class are lowercase hex. Reject malformed or
    // non-ASCII database values before comparing; US_ASCII would replace them.
    private boolean validDigest(String expectedDigest) {
        if (expectedDigest == null || expectedDigest.length() != 64) {
            return false;
        }
        for (int i = 0; i < expectedDigest.length(); i++) {
            char ch = expectedDigest.charAt(i);
            if (!(ch >= '0' && ch <= '9') && !(ch >= 'a' && ch <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private boolean validSyntax(String invitation) {
        if (invitation == null || invitation.length() != 43) {
            return false;
        }
        for (int i = 0; i < invitation.length(); i++) {
            char ch = invitation.charAt(i);
            if (!(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z') &&
                    !(ch >= '0' && ch <= '9') && ch != '_' && ch != '-') {
                return false;
            }
        }
        return true;
    }
}
