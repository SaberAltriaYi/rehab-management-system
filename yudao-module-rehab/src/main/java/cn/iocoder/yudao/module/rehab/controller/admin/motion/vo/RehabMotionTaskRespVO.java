package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 动作评估任务 Response VO")
@Data
public class RehabMotionTaskRespVO {

    private Long id;
    private Long assessmentId;
    private String taskType;
    private String state;
    private String stateLabel;
    private Integer progress;
    private String stepMessage;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime nextRunTime;
    private Boolean cancelRequested;
    private String errorCode;
    private String errorMessage;
    private String failedState;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime deadlineTime;
    private Integer inputRevision;
    private LocalDateTime createTime;

}
