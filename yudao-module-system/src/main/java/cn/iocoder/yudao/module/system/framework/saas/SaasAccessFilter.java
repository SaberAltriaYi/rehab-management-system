package cn.iocoder.yudao.module.system.framework.saas;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.system.service.saas.SaasTenantAccessService;
import cn.iocoder.yudao.module.system.service.saas.SaasPlatformAuthority;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

/** Runs after authentication and tenant-identity binding; never trusts client role claims. */
public class SaasAccessFilter extends OncePerRequestFilter {
    private final SaasTenantAccessService tenants;
    private final SaasPlatformAuthority platform;
    public SaasAccessFilter(SaasTenantAccessService tenants,SaasPlatformAuthority platform) {
        this.tenants=tenants; this.platform=platform;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws ServletException,IOException {
        String path=req.getRequestURI().substring(req.getContextPath().length());
        if (!path.startsWith("/admin-api/") && !path.startsWith("/app-api/")) { chain.doFilter(req,res); return; }
        // CAPTCHA is a pre-login challenge, not a tenant business operation.
        // The login form may request it before resolving the organization. Only
        // these exact POST endpoints bypass the subscription gate; their own
        // challenge validation/rate limiting and all login gates remain intact.
        boolean captchaRoute="POST".equals(req.getMethod())
                && ("/admin-api/system/captcha/get".equals(path)
                || "/admin-api/system/captcha/check".equals(path));
        boolean publicRoute=captchaRoute || "OPTIONS".equals(req.getMethod())
                || ("POST".equals(req.getMethod()) && "/admin-api/system/saas/register".equals(path))
                || ("POST".equals(req.getMethod()) && "/admin-api/system/auth/logout".equals(path))
                || ("GET".equals(req.getMethod()) && ("/admin-api/system/tenant/get-id-by-name".equals(path)
                || "/admin-api/system/tenant/get-by-website".equals(path)));
        if (!publicRoute) {
            try {
                if (path.startsWith("/admin-api/system/saas/platform/")
                        || path.startsWith("/admin-api/system/tenant/")
                        || path.startsWith("/admin-api/system/tenant-package/")) platform.requireAdmin();
                else tenants.requireCurrentTenant();
            } catch (RuntimeException denied) {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                ServletUtils.writeJSON(res,CommonResult.error(403,"机构订阅不可用或权限不足"));
                return;
            }
        }
        chain.doFilter(req,res);
    }
}
