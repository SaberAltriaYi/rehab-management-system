package cn.iocoder.yudao.module.rehab.controller.admin.audit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 审计日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RehabAuditLogPageReqVO extends PageParam {

    @Schema(description = "模块类型", example = "report")
    private String moduleType;

    @Schema(description = "模块ID", example = "30001")
    private Long moduleId;

    @Schema(description = "操作类型", example = "report_lock")
    private String operationType;

    @Schema(description = "操作人ID", example = "100")
    private Long operatorUserId;

    @Schema(description = "结果状态", example = "success")
    private String resultStatus;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
