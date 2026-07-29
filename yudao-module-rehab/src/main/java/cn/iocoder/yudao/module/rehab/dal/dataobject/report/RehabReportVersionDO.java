package cn.iocoder.yudao.module.rehab.dal.dataobject.report;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 报告版本历史
 */
@TableName("rehab_report_version")
@KeySequence("rehab_report_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabReportVersionDO extends BaseDO {

    @TableId
    private Long id;

    private Long reportId;
    private Integer versionNo;
    private String reportStatus;
    private String generationMode;
    private String reportJson;
    private String docxPath;
    private String pdfPath;
    private String htmlSnapshotPath;
    private Long basedOnAssessmentId;
    private String changeSummary;
    private Long generatedBy;
    private Long reviewedBy;
    private Long approvedBy;
    private Long lockedBy;
    private LocalDateTime lockedTime;
}
