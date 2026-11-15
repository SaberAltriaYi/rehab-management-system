package cn.iocoder.yudao.module.rehab.service.hep;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RehabHepIssuerSafetyTest {
    @Test
    void requiresExplicitHttpsIssuerRatherThanObsoleteHttpDefault() {
        assertTrue(RehabHepTokenService.validHttpsIssuer("https://127.0.0.1:18443"));
        assertTrue(RehabHepTokenService.validHttpsIssuer("https://core.example.invalid"));
        for (String value : new String[] {
                "", "http://127.0.0.1:48080", "http://127.0.0.1:18443",
                "https://user:password@127.0.0.1:18443",
                "https://127.0.0.1:18443/admin-api",
                "https://127.0.0.1:18443?token=secret", "https://127.0.0.1:18443#fragment",
                "not-a-url"
        }) {
            assertFalse(RehabHepTokenService.validHttpsIssuer(value), value);
        }
        assertFalse(RehabHepTokenService.validHttpsIssuer(null));
    }

    @Test
    void missingIssuerCannotStartSignerEvenWhenKeyPathIsMissing() {
        assertThrows(IllegalStateException.class, () -> new RehabHepTokenService("", ""));
        assertThrows(IllegalStateException.class,
                () -> new RehabHepTokenService("", "http://127.0.0.1:48080"));
    }
}
