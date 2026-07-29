package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 训练任务创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RehabExerciseTaskCreateReqVO extends RehabExerciseTaskBaseVO {
}
