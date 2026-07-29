package cn.iocoder.yudao.module.rehab.controller.admin.ai.vo;

import lombok.Data;

/**
 * AI 任务查询参数（内部）
 */
@Data
public class RehabAiJobQueryReqVO {

    private Long patientId;
    private Long episodeId;
    private Long assessmentId;
    private Long reportId;
    private Long planId;
    private Long progressId;
    private Long alertId;
    private Long triggerId;
}
