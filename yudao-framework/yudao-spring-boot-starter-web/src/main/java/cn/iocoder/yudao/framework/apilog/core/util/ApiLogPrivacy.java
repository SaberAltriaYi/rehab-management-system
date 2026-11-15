package cn.iocoder.yudao.framework.apilog.core.util;

import org.springframework.web.method.HandlerMethod;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Health and authentication APIs retain audit metadata, never payloads. */
public final class ApiLogPrivacy {
    private ApiLogPrivacy() {
    }

    public static boolean omitPayload(String requestUri, HandlerMethod handler) {
        if (handler != null) {
            String name = handler.getBeanType().getName();
            if (name.startsWith("cn.iocoder.yudao.module.rehab.")
                    || name.startsWith("cn.iocoder.yudao.module.member.")) {
                return true;
            }
        }
        if (requestUri == null) {
            return true;
        }
        try {
            String path = URLDecoder.decode(requestUri, StandardCharsets.UTF_8.name()).toLowerCase(Locale.ROOT);
            return path.contains("/auth/") || path.endsWith("/auth")
                    || path.contains("/rehab/") || path.contains("/app-patient/")
                    || path.contains("/app-admin/") || path.contains("/member/");
        } catch (Exception ignored) {
            // Malformed routing input must not cause a credential-bearing body
            // to fall through to the general diagnostic logger.
            return true;
        }
    }
}
