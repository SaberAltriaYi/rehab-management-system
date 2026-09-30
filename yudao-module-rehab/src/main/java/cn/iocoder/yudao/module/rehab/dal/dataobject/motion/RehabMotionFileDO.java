package cn.iocoder.yudao.module.rehab.dal.dataobject.motion;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 动作评估文件（租户隔离：继承 TenantBaseDO，由多租户拦截器自动过滤 tenant_id）
 */
@TableName("rehab_motion_file")
@KeySequence("rehab_motion_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RehabMotionFileDO extends TenantBaseDO {

    /** 文件编号 */
    @TableId
    private Long id;
    /** 动作评估编号 */
    private Long assessmentId;
    /** mot/trc/osim/metadata/video/result/pdf */
    private String fileKind;
    /** OpenCap trial 名 */
    private String trialName;
    /** 相机目录 Cam0.. */
    private String cameraKey;
    /** OpenCap 导出内相对路径 */
    private String relativePath;
    /** 存储根目录下相对路径 */
    private String storagePath;
    /** 字节数 */
    private Long fileSize;
    /** SHA-256 */
    private String sha256;
    /** MIME */
    private String contentType;
    /** upload/opencap/engine/system */
    private String source;
    /** 上传人 */
    private Long uploadUserId;

}
