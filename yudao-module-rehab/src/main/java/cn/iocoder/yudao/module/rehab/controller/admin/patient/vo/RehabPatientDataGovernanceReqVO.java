package cn.iocoder.yudao.module.rehab.controller.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. */
@Data
@Schema(description = "受控模拟资料治理；不删除或修改临床记录")
public class RehabPatientDataGovernanceReqVO {
    @NotNull @Positive
    private Long patientId;
    @NotBlank @Size(max = 64)
    private String confirmPatientNo;
    @NotBlank @Pattern(regexp = "REAL|SYNTHETIC|SYNTHETIC_ARCHIVED")
    private String expectedDataOrigin;
    @NotBlank @Pattern(regexp = "MARK_SYNTHETIC|ARCHIVE_SYNTHETIC|RESTORE_SYNTHETIC")
    private String operation;
    @NotBlank @Size(min = 5, max = 256)
    @Schema(description = "操作依据，请勿填写病情、电话号码等敏感原文")
    private String reason;
}
