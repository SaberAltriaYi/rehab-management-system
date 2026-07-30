package cn.iocoder.yudao.server;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashCliTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldEncodePassword() {
        String password = "9f3e7c2a6b1d4e8f";

        String encoded = PasswordHashCli.encode(password);

        assertTrue(encoder.matches(password, encoded));
    }

    @Test
    void shouldRejectEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHashCli.encode(""));
    }

    @Test
    void shouldRejectPasswordLongerThan72Utf8Bytes() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHashCli.encode("康康康康康康康康康康康康康康康康康康康康康康康康康"));
    }
}
