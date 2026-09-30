package cn.iocoder.yudao.module.rehab.controller.admin.motion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 动作评估文件 Response VO（不暴露服务器存储路径）")
@Data
public class RehabMotionFileRespVO {

    private Long id;
    private String fileKind;
    private String trialName;
    private String cameraKey;
    private String relativePath;
    private Long fileSize;
    private String contentType;
    private String source;
    private LocalDateTime createTime;

}
