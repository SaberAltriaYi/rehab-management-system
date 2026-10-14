package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "管理后台 - 系统拥有者租户总览")
@Data
public class TenantOwnerOverviewRespVO {

    @Schema(description = "租户总数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long totalTenants;

    @Schema(description = "启用租户数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long activeTenants;

    @Schema(description = "已停用租户数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long disabledTenants;

    @Schema(description = "已过期租户数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expiredTenants;

    @Schema(description = "未来 30 天内到期租户数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expiringWithin30Days;

    @Schema(description = "租户账号额度合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long totalAccountQuota;

}
