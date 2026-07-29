package cn.iocoder.yudao.module.rehab.controller.admin.app.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Schema(description = "管理端小程序 - 随访备注分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppAdminFollowupNotePageReqVO extends PageParam {

    @Schema(description = "患者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    @NotNull(message = "患者编号不能为空")
    private Long patientId;
}
