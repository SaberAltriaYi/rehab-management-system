package cn.iocoder.yudao.module.rehab.service.app.auth;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class PatientInvitationTokensTest {
    private static byte[] syntheticKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    @Test
    void generateAndDigestAreUnpredictableTenantBoundAndKeyBound() {
        PatientInvitationTokens tokens = new PatientInvitationTokens(syntheticKey((byte) 0x5a));
        String code = tokens.generate();
        assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(code, tokens.generate());
        String digest = tokens.digest(1L, code);
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertFalse(digest.contains(code));
        assertTrue(tokens.matches(1L, code, digest));
        assertFalse(tokens.matches(2L, code, digest));
        assertFalse(new PatientInvitationTokens(syntheticKey((byte) 0x2b)).matches(1L, code, digest));
    }

    @Test
    void malformedTokensAndWeakKeysFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new PatientInvitationTokens(new byte[31]));
        PatientInvitationTokens tokens = new PatientInvitationTokens(syntheticKey((byte) 0x5a));
        String code = tokens.generate();
        String digest = tokens.digest(1L, code);
        assertFalse(tokens.matches(1L, code + "x", digest));
        assertFalse(tokens.matches(1L, code, "invalid"));
        assertFalse(tokens.matches(1L, code, digest.substring(0, 63) + "g"));
        assertFalse(tokens.matches(1L, code, digest.substring(0, 63) + "\u0100"));
        assertFalse(tokens.matches(0L, code, digest));
        assertThrows(IllegalArgumentException.class, () -> tokens.digest(1L, "123"));
    }
}
