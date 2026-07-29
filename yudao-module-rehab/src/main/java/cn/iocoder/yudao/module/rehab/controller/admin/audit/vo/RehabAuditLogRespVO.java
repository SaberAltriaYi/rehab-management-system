package cn.iocoder.yudao.module.rehab.controller.admin.audit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 审计日志 Response VO")
@Data
public class RehabAuditLogRespVO {

    private Long id;
    private String moduleType;
    private Long moduleId;
    private String operationType;
    private Long operatorUserId;
    private String operatorRole;
    private String beforeDataJson;
    private String afterDataJson;
    private String ip;
    private String userAgent;
    private String resultStatus;
    private String remark;
    private LocalDateTime createTime;

    @Schema(description = "操作人昵称")
    private String operatorName;

}
