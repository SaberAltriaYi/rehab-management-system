package cn.iocoder.yudao.module.rehab.service.assessment;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 评估附件下载文件。
 */
@Data
@AllArgsConstructor
public class RehabAssessmentAttachmentFile {

    private String fileName;
    private String fileType;
    private byte[] content;

}
