package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionManualEditDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionManualEditMapper;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.CLERK_WRITE_FORBIDDEN;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.MOTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.MOTION_SIGNED_LOCKED;

/**
 * 访问控制与留痕：
 * <ul>
 *   <li>租户隔离由 MyBatis 租户插件保证（所有表带 tenant_id）；</li>
 *   <li>患者级数据权限复用 {@link RehabDataPermissionService#canReadPatient}；</li>
 *   <li>临床写入（疼痛、有效性、人工判定、评分审核、签署）禁止文员；</li>
 *   <li>审计日志只记录编号与状态，不记录患者姓名等个人信息。</li>
 * </ul>
 */
@Component
public class RehabMotionAccess {

    @Resource
    private RehabMotionAssessmentMapper assessmentMapper;
    @Resource
    private RehabMotionManualEditMapper manualEditMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private RehabAuditLogService auditLogService;

    public RehabMotionAssessmentDO readable(Long id, Long userId) {
        RehabMotionAssessmentDO a = id == null ? null : assessmentMapper.selectById(id);
        if (a == null) {
            throw exception(MOTION_NOT_EXISTS);
        }
        requirePatient(a.getPatientId(), userId);
        return a;
    }

    /** 可读 + 未签署 */
    public RehabMotionAssessmentDO editable(Long id, Long userId) {
        RehabMotionAssessmentDO a = readable(id, userId);
        if (RehabMotionConstants.STATE_COMPLETED.equals(a.getStatus())) {
            throw exception(MOTION_SIGNED_LOCKED);
        }
        return a;
    }

    public void requirePatient(Long patientId, Long userId) {
        if (patientId == null || !dataPermissionService.canReadPatient(patientId, userId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
    }

    public void requireClinician(Long userId) {
        if (dataPermissionService.isClerk(userId) && !dataPermissionService.isSuperAdmin(userId)
                && !dataPermissionService.isTherapist(userId)) {
            throw exception(CLERK_WRITE_FORBIDDEN);
        }
    }

    public java.util.Set<Long> visiblePatientIds(Long userId) {
        return dataPermissionService.getVisiblePatientIds(userId);
    }

    /** 输入变更：递增 input_revision（分析结果随之标记为过期，需重新计算）。 */
    public int bumpRevision(Long assessmentId) {
        assessmentMapper.update(null, new LambdaUpdateWrapper<RehabMotionAssessmentDO>()
                .setSql("input_revision = input_revision + 1")
                .eq(RehabMotionAssessmentDO::getId, assessmentId));
        RehabMotionAssessmentDO fresh = assessmentMapper.selectById(assessmentId);
        return fresh.getInputRevision();
    }

    public void manualEdit(Long assessmentId, String targetType, Long targetId, String targetKey, String field,
                           Object oldValue, Object newValue, String reason, Long userId) {
        manualEditMapper.insert(RehabMotionManualEditDO.builder()
                .assessmentId(assessmentId)
                .targetType(targetType)
                .targetId(targetId)
                .targetKey(targetKey == null ? null : StrUtil.maxLength(targetKey, 188))
                .fieldName(StrUtil.maxLength(field, 61))
                .oldValue(stringify(oldValue))
                .newValue(stringify(newValue))
                .reason(reason == null ? null : StrUtil.maxLength(reason, 497))
                .operatorUserId(userId)
                .build());
    }

    private static String stringify(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return (String) v;
        }
        return JsonUtils.toJsonString(v);
    }

    public void audit(Long assessmentId, String operation, Long userId, Object before, Object after, String remark) {
        auditLogService.createAuditLog(RehabMotionConstants.AUDIT_MODULE, assessmentId, operation, userId,
                role(userId), before, after, "success", remark);
    }

    public String role(Long userId) {
        if (dataPermissionService.isSuperAdmin(userId)) {
            return "admin";
        }
        if (dataPermissionService.isTherapist(userId)) {
            return "therapist";
        }
        if (dataPermissionService.isClerk(userId)) {
            return "clerk";
        }
        return "user";
    }

    public java.util.List<?> auditLogs(Long assessmentId, Long userId) {
        return auditLogService.getModuleAuditLogs(RehabMotionConstants.AUDIT_MODULE, assessmentId, userId);
    }

}
