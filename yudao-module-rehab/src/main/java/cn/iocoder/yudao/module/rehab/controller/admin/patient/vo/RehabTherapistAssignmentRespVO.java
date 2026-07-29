package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 治疗师分配记录 Response VO")
@Data
public class RehabTherapistAssignmentRespVO {

    private Long id;
    private Long patientId;
    private Long therapistUserId;
    private String therapistName;
    private String roleType;
    private String assignStatus;
    private String assignReason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long assignedBy;
    private String assignedByName;
    private Long transferFromUserId;
    private String transferFromUserName;
    private Long transferToUserId;
    private String transferToUserName;
    private String remark;
    private LocalDateTime createTime;

}
