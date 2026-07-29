package cn.iocoder.yudao.module.rehab.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 任务
 */
@TableName("rehab_ai_job")
@KeySequence("rehab_ai_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabAiJobDO extends BaseDO {

    @TableId
    private Long id;

    private String jobNo;
    private Long patientId;
    private Long episodeId;
    private Long assessmentId;
    private Long reportId;
    private Long planId;
    private Long progressId;
    private Long alertId;
    private Long triggerId;
    private String jobType;
    private String modelName;
    private String promptName;
    private String inputHash;
    private String outputHash;
    private String requestPayloadJson;
    private String responsePayloadJson;
    private String status;
    private Boolean fallbackUsed;
    private Long latencyMs;
    private String tokenUsageJson;
    private Long triggeredByUserId;
}
