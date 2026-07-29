package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 患者分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabPatientPageReqVO extends PageParam {

    @Schema(description = "关键字（姓名/手机号/患者编号）", example = "张")
    private String keyword;

    @Schema(description = "当前治疗师用户编号", example = "105")
    private Long currentTherapistUserId;

    @Schema(description = "当前阶段", example = "待评估")
    private String currentStage;

    @Schema(description = "CRM 绑定状态", example = "bound")
    private String crmBindStatus;

    @Schema(description = "性别（1男 2女）")
    private Integer gender;

    @Schema(description = "来源渠道", example = "门诊")
    private String sourceChannel;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
