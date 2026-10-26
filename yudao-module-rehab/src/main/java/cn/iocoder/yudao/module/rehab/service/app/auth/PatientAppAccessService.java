package cn.iocoder.yudao.module.rehab.service.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
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
    @Resource
    private MemberUserApi memberUserApi;

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
        int minutes = expiresMinutes == null ? 30 : expiresMinutes;
        if (minutes < 5 || minutes > 24 * 60) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PatientInvitationTokens tokenCodec = tokens();
        // Serialize issuance and redemption using the same lock order:
        // patient row first, invitation row second. Two issuers must not leave
        // two pending codes, and an issuer/redeemer must not deadlock on FKs.
        if (!lockPatient(tenantId, patientId)) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        RehabPatientDO patient = patientMapper.selectById(patientId);
        if (patient == null || StrUtil.isBlank(patient.getPhone())) {
            throw exception(PATIENT_NOT_EXISTS);
        }
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
        // Only self-enrollment is supported by this invitation schema. A client
        // cannot claim caregiver/imported status without a separate consent flow.
        if (appUserId == null || StrUtil.isBlank(phone) || StrUtil.isBlank(invitation)
                || (StrUtil.isNotBlank(bindType) && !"self".equals(bindType))) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireCurrentMemberPhone(appUserId, phone);
        PatientInvitationTokens tokenCodec = tokens();
        String requestedDigest;
        try {
            requestedDigest = tokenCodec.digest(tenantId, invitation);
        } catch (IllegalArgumentException ignored) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        String invitationQuery = "SELECT id,patient_id,phone,invitation_digest "
                + "FROM rehab_patient_app_invitation WHERE tenant_id=? AND invitation_digest=? "
                + "AND status='PENDING' AND deleted=FALSE AND expires_at>?";
        // First resolve the patient without holding the invitation lock. Then
        // lock the patient and revalidate the invitation under its own row lock.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(invitationQuery,
                tenantId, requestedDigest, LocalDateTime.now());
        if (rows.isEmpty()) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long candidatePatientId = ((Number) rows.get(0).get("patient_id")).longValue();
        if (!lockPatient(tenantId, candidatePatientId)) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
        // The existing unique (tenant_id, invitation_digest) index limits this
        // lock to the selected invitation, not the institution's entire queue.
        rows = jdbcTemplate.queryForList(invitationQuery + " FOR UPDATE",
                tenantId, requestedDigest, LocalDateTime.now());
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
        if (!candidatePatientId.equals(patientId)) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
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
            try {
                bindingMapper.insert(RehabPatientUserBindingDO.builder().patientId(patientId).appUserId(appUserId)
                        .bindType("self").bindStatus("active")
                        .phone(phone).nickname(StrUtil.blankToDefault(nickname, patient.getName())).build());
            } catch (DuplicateKeyException ignored) {
                // A concurrent invitation for another patient may win the unique
                // active-member binding. Roll back this entire redemption.
                throw exception(APP_PATIENT_BINDING_CONFLICT);
            }
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
        RehabPatientUserBindingDO binding = appUserId == null ? null
                : bindingMapper.selectActiveByAppUserId(appUserId);
        if (binding == null) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
        // Binding rows alone must not keep access alive after disabling a member
        // or changing either side's phone. Re-enrollment requires a new invitation.
        requireCurrentMemberPhone(appUserId, binding.getPhone());
        RehabPatientDO patient = patientMapper.selectById(binding.getPatientId());
        if (patient == null || !StrUtil.equals(binding.getPhone(), patient.getPhone())) {
            throw exception(APP_PATIENT_BINDING_REQUIRED);
        }
    }

    private void requireCurrentMemberPhone(Long appUserId, String phone) {
        MemberUserRespDTO member = memberUserApi.getUser(appUserId);
        if (member == null || !appUserId.equals(member.getId())
                || !CommonStatusEnum.ENABLE.getStatus().equals(member.getStatus())
                || StrUtil.isBlank(phone) || !phone.equals(member.getMobile())) {
            throw exception(APP_PATIENT_INVITATION_INVALID);
        }
    }

    private boolean lockPatient(Long tenantId, Long patientId) {
        return patientId != null && jdbcTemplate.queryForList(
                "SELECT id FROM rehab_patient WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE",
                tenantId, patientId).size() == 1;
    }
}
