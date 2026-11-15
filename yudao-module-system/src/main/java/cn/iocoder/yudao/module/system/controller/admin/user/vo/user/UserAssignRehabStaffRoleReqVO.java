package cn.iocoder.yudao.module.system.controller.admin.user.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Schema(description = "管理后台 - 分配康复员工预设角色 Request VO")
@Data
public class UserAssignRehabStaffRoleReqVO {

    @Schema(description = "员工用户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "员工用户编号不能为空")
    private Long userId;

    @Schema(description = "预设角色编码：tenant_rehab_therapist（临床审核人须经独立资质核验授权）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "员工角色不能为空")
    @Pattern(regexp = "tenant_rehab_therapist", message = "机构管理员仅可分配康复治疗师角色")
    private String roleCode;
}
