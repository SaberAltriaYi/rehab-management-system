package cn.iocoder.yudao.module.system.framework.license;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Applied AFTER authentication and the tenant security filter; protects
 * @PermitAll API routes, including login, refresh, attachments and reports.
 */
public final class TenantLicenseEnforcementFilter extends OncePerRequestFilter {
    private final TenantLicenseGuard license;

    public TenantLicenseEnforcementFilter(TenantLicenseGuard license) { this.license = license; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/admin-api/") && !path.startsWith("/app-api/")) {
            chain.doFilter(request, response);
            return;
        }
        // Strict maintenance whitelist; all regular @PermitAll business APIs
        // remain subject to license checks, including authentication endpoints.
        if ("OPTIONS".equals(request.getMethod())
                || "/admin-api/system/auth/logout".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Map<String, Object> claims = license.requireTenant(TenantContextHolder.getTenantId());
            if (license.isEnforced()) {
                verifyModule(path, claims);
            }
        } catch (RuntimeException denied) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            ServletUtils.writeJSON(response, CommonResult.error(403, "工作室授权许可无效或已过期"));
            return;
        }
        chain.doFilter(request, response);
    }

    private void verifyModule(String path, Map<String, Object> claims) {
        // A narrow feature gate in addition to normal Spring Security RBAC.
        // Other application modules still require a valid tenant license.
        String module = null;
        if (path.matches("^/(admin-api|app-api)/rehab/report(/.*)?$")) {
            module = "report";
        } else if (path.matches("^/(admin-api|app-api)/rehab/motion(/.*)?$")) {
            module = "motion";
        } else if (path.matches("^/(admin-api|app-api)/rehab(/.*)?$")) {
            module = "rehab";
        }
        if (module != null && (!(claims.get("modules") instanceof List)
                || !((List<?>) claims.get("modules")).contains(module))) {
            throw new LicenseLeaseVerifier.LicenseFailure("LICENSE_MODULE_NOT_AUTHORIZED");
        }
    }
}
