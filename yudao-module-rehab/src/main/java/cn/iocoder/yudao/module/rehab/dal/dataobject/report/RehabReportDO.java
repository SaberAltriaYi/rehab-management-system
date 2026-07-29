package cn.iocoder.yudao.module.rehab.dal.dataobject.report;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 康复报告主表
 */
@TableName(value = "rehab_report")
@KeySequence("rehab_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabReportDO extends BaseDO {

    @TableId
    private Long id;

    private String reportNo;
    private Long patientId;
    private Long episodeId;
    private Long assessmentId;
    private String reportType;
    private String reportStatus;
    private Integer reportVersion;
    private Long generatedBy;
    private Long reviewedBy;
    private Long approvedBy;
    private Long lockedBy;
    private LocalDateTime lockedTime;
    private String generationMode;
    private String reportJson;
    private String docxPath;
    private String pdfPath;
    private String htmlSnapshotPath;
    private LocalDateTime lastGeneratedAt;
    private LocalDateTime exportedAt;
    private String note;

}
