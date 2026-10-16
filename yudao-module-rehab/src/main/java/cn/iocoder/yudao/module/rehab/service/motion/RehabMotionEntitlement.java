package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.module.system.framework.license.TenantLicenseGuard;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * Tenant-scoped gate for the motion assessment module.
 *
 * <p>Development environments keep the existing local workflow usable when
 * signed license enforcement is disabled. Once enforcement is enabled, the
 * owner-signed module list is authoritative and all failures deny access.</p>
 */
@Component("rehabMotionEntitlement")
@Aspect
public class RehabMotionEntitlement {

    private static final String[] MODULE_KEYS = {"motion", "rehab-motion", "assessment-engine"};

    private final TenantLicenseGuard tenantLicenseGuard;

    public RehabMotionEntitlement(TenantLicenseGuard tenantLicenseGuard) {
        this.tenantLicenseGuard = tenantLicenseGuard;
    }

    public boolean enabled() {
        if (!tenantLicenseGuard.isEnforced()) {
            return true;
        }
        try {
            Map<String, Object> claims = tenantLicenseGuard.requireCurrentTenant();
            Object modules = claims.get("modules");
            if (!(modules instanceof Collection<?>)) {
                return false;
            }
            for (Object module : (Collection<?>) modules) {
                if (module == null) {
                    continue;
                }
                String value = module.toString().trim().toLowerCase();
                for (String key : MODULE_KEYS) {
                    if (key.equals(value)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    /** Apply the entitlement to every motion endpoint, including endpoints
     * that only read files or protocol metadata. */
    @Around("execution(* cn.iocoder.yudao.module.rehab.controller.admin.motion.RehabMotionController.*(..))")
    public Object requireEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!enabled()) {
            throw new AccessDeniedException("当前租户未开通动作评估模块");
        }
        return joinPoint.proceed();
    }
}
