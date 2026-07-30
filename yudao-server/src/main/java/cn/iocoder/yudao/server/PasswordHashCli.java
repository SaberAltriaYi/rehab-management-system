package cn.iocoder.yudao.server;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 局域网首次安装使用的 BCrypt 哈希工具。
 *
 * <p>密码只从标准输入读取，避免出现在进程参数或 Compose 环境中。</p>
 */
public final class PasswordHashCli {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordHashCli() {
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = reader.readLine();
        System.out.println(encode(password));
    }

    static String encode(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalArgumentException("password exceeds BCrypt 72-byte limit");
        }
        return ENCODER.encode(password);
    }
}
