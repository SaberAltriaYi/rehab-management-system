package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantOwnerOverviewRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TenantOwnerOverviewService {

    @Resource
    private TenantService tenantService;

    public TenantOwnerOverviewRespVO getOverview() {
        return summarize(tenantService.getTenantList(), LocalDateTime.now());
    }

    static TenantOwnerOverviewRespVO summarize(List<TenantDO> tenants, LocalDateTime now) {
        TenantOwnerOverviewRespVO result = new TenantOwnerOverviewRespVO();
        result.setTotalTenants((long) tenants.size());
        result.setActiveTenants(tenants.stream()
                .filter(t -> CommonStatusEnum.isEnable(t.getStatus())).count());
        result.setDisabledTenants(tenants.stream()
                .filter(t -> CommonStatusEnum.isDisable(t.getStatus())).count());
        result.setExpiredTenants(tenants.stream()
                .filter(t -> !Objects.equals(t.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM))
                .filter(t -> t.getExpireTime() != null && !t.getExpireTime().isAfter(now)).count());
        result.setExpiringWithin30Days(tenants.stream()
                .filter(t -> !Objects.equals(t.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM))
                .filter(t -> t.getExpireTime() != null && t.getExpireTime().isAfter(now)
                        && !t.getExpireTime().isAfter(now.plusDays(30))).count());
        result.setTotalAccountQuota(tenants.stream().map(TenantDO::getAccountCount)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum());
        return result;
    }
}
