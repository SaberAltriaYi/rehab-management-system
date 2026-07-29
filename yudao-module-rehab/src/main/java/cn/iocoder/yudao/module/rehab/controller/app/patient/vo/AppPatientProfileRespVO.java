package cn.iocoder.yudao.module.rehab.controller.app.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "患者端 APP - 个人信息 Response VO")
@Data
public class AppPatientProfileRespVO {

    @Schema(description = "患者编号", example = "10001")
    private Long patientId;

    @Schema(description = "患者编码", example = "PAT202603100001")
    private String patientNo;

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "手机号", example = "138****8000")
    private String phone;

    @Schema(description = "当前阶段", example = "执行中")
    private String currentStage;

    @Schema(description = "主责治疗师名称", example = "王治疗师")
    private String therapistName;
}
