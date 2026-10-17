package cn.iocoder.yudao.module.rehab.service.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/**
 * 患者端安全入口：机构先核验身份并签发一次性邀请，患者使用会员短信登录后再兑换。
 * 不接受手机号 + patientNo 直接换取会话，也不在患者端持久化明文邀请。
 */
@Service
public class PatientAppAccessService {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private RehabPatientMapper patientMapper;
    @Resource
    private RehabPatientUserBindingMapper bindingMapper;

    @Value("${yudao.rehab.patient-auth.invitation-secret:}")
    private String invitationSecret;

    private PatientInvitationTokens tokens() {
        if (StrUtil.length(invitationSecret) < 32) {
            throw exception(APP_PATIENT_AUTH_DISABLED);
        }
        return new PatientInvitationTokens(invitationSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(rollbackFor = Exception.class)
    public String issueInvitation(Long patientId, Integer expiresMinutes, Long operatorUserId) {
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient == null || StrUtil.isBlank(patient.getPhone())) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        int minutes = expiresMinutes == null ? 30 : expiresMinutes;
        if (minutes < 5 || minutes > 7 * 24 * 60) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PatientInvitationTokens tokenCodec = tokens();
        String token = tokenCodec.generate();
        String digest = tokenCodec.digest(tenantId, token);
        jdbcTemplate.update("UPDATE rehab_patient_app_invitation SET status='REVOKED' "
                        + "WHERE tenant_id=? AND patient_id=? AND status='PENDING'", tenantId, patientId);
        jdbcTemplate.update("INSERT INTO rehab_patient_app_invitation "
                        + "(tenant_id,patient_id,phone,invitation_digest,expires_at,status,issued_by) "
                        + "VALUES (?,?,?,?,?,'PENDING',?)", tenantId, patientId, patient.getPhone(), digest,
                LocalDateTime.now().plusMinutes(minutes), operatorUserId);
        return token;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long redeemInvitation(String patientNo, String phone, String invitation, String bindType,
                                 String nickname, Long appUserId) {
        if (appUserId == null || StrUtil.isBlank(phone) || StrUtil.isBlank(invitation)) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PatientInvitationTokens tokenCodec = tokens();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id,patient_id,phone,invitation_digest "
                        + "FROM rehab_patient_app_invitation WHERE tenant_id=? AND status='PENDING' "
                        + "AND expires_at>? ORDER BY id DESC FOR UPDATE", tenantId, LocalDateTime.now());
        Map<String, Object> matched = null;
        for (Map<String, Object> row : rows) {
            String digest = String.valueOf(row.get("invitation_digest"));
            if (tokenCodec.matches(tenantId, invitation, digest)) {
                matched = row;
                break;
            }
        }
        if (matched == null || !phone.equals(String.valueOf(matched.get("phone")))) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long patientId = ((Number) matched.get("patient_id")).longValue();
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient == null || !phone.equals(patient.getPhone())
                || (StrUtil.isNotBlank(patientNo) && !patientNo.equals(patient.getPatientNo()))) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        RehabPatientUserBindingDO existing = bindingMapper.selectActiveByAppUserId(appUserId);
        if (existing != null && !patientId.equals(existing.getPatientId())) {
            throw exception(APP_PATIENT_BINDING_CONFLICT);
        }
        if (existing == null) {
            bindingMapper.insert(RehabPatientUserBindingDO.builder().patientId(patientId).appUserId(appUserId)
                    .bindType(StrUtil.blankToDefault(bindType, "self")).bindStatus("active")
                    .phone(phone).nickname(StrUtil.blankToDefault(nickname, patient.getName())).build());
        }
        int updated = jdbcTemplate.update("UPDATE rehab_patient_app_invitation SET status='REDEEMED', "
                        + "redeemed_by_user_id=?,redeemed_at=NOW() WHERE tenant_id=? AND id=? AND status='PENDING'",
                appUserId, tenantId, ((Number) matched.get("id")).longValue());
        if (updated != 1) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        return patientId;
    }

    public void validateActiveBinding(Long appUserId) {
        if (appUserId == null || bindingMapper.selectActiveByAppUserId(appUserId) == null) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
    }
}
