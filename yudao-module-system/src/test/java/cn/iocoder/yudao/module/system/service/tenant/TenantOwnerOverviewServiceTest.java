package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantOwnerOverviewRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantOwnerOverviewServiceTest {

    @Test
    void summarizeCountsTenantHealthAndQuota() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 9, 0);
        List<TenantDO> tenants = List.of(
                new TenantDO().setPackageId(TenantDO.PACKAGE_ID_SYSTEM)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setAccountCount(10),
                new TenantDO().setPackageId(1L)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setExpireTime(now.plusDays(20)).setAccountCount(20),
                new TenantDO().setPackageId(1L)
                        .setStatus(CommonStatusEnum.ENABLE.getStatus()).setExpireTime(now.plusDays(40)).setAccountCount(30),
                new TenantDO().setPackageId(1L)
                        .setStatus(CommonStatusEnum.DISABLE.getStatus()).setExpireTime(now.minusDays(1)).setAccountCount(40)
        );

        TenantOwnerOverviewRespVO result = TenantOwnerOverviewService.summarize(tenants, now);

        assertEquals(4L, result.getTotalTenants());
        assertEquals(3L, result.getActiveTenants());
        assertEquals(1L, result.getDisabledTenants());
        assertEquals(1L, result.getExpiredTenants());
        assertEquals(1L, result.getExpiringWithin30Days());
        assertEquals(100L, result.getTotalAccountQuota());
    }
}
