package cn.iocoder.yudao.module.rehab.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 康复计划创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabCarePlanCreateReqVO extends RehabCarePlanBaseVO {
}
