package cn.iocoder.yudao.server;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashCliTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldEncodePassword() {
        String password = "valid-test-1234";

        String encoded = PasswordHashCli.encode(password);

        assertTrue(encoder.matches(password, encoded));
    }

    @Test
    void shouldRejectEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHashCli.encode(""));
    }

    @Test
    void shouldRejectPasswordShorterThan12Characters() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHashCli.encode("short-pass"));
    }

    @Test
    void shouldRejectPasswordLongerThan16Characters() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHashCli.encode("12345678901234567"));
    }
}
