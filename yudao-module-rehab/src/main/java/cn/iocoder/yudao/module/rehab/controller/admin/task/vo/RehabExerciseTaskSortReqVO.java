package cn.iocoder.yudao.module.rehab.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RehabExerciseTaskSortReqVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "计划编号不能为空")
    private Long planId;

    @Schema(description = "排序项", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "排序项不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "任务编号不能为空")
        private Long id;
        @NotNull(message = "排序值不能为空")
        private Integer sortOrder;
    }

}
