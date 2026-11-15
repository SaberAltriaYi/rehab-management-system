package cn.iocoder.yudao.module.rehab.service.hep;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.binding.RehabPatientUserBindingMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** HEP 家庭康复训练：动作库、处方版本、训练日和患者反馈。所有写入显式带租户。 */
@Service
public class RehabHepService {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private RehabPatientUserBindingMapper bindingMapper;

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    public List<Map<String, Object>> listReviewedExercises() {
        return jdbcTemplate.queryForList("SELECT id,exercise_code,name,body_region,target_deficit," +
                "instruction_text,media_url,source_type,source_reference,license_status " +
                "FROM rehab_hep_exercise WHERE tenant_id=? AND status='REVIEWED' AND deleted=0 " +
                "ORDER BY name", tenantId());
    }

    public List<Map<String, Object>> listTemplates() {
        return jdbcTemplate.queryForList("SELECT id,template_no,name,goal,protocol_version,status,reviewed_by,reviewed_time " +
                "FROM rehab_hep_template WHERE tenant_id=? AND deleted=0 ORDER BY name", tenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long createTemplate(Map<String, Object> body, Long userId) {
        String no = text(body, "templateNo");
        String name = text(body, "name");
        if (StrUtil.isBlank(no) || StrUtil.isBlank(name)) {
            throw ServiceExceptionUtil.exception(APP_PATIENT_INVITATION_INVALID);
        }
        Long templateId = insertAndReturnId("INSERT INTO rehab_hep_template " +
                "(tenant_id,template_no,name,goal,protocol_version,creator,updater) VALUES (?,?,?,?,?,?,?)",
                tenantId(), no, name, text(body, "goal"), text(body, "protocolVersion"),
                String.valueOf(userId), String.valueOf(userId));
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                jdbcTemplate.update("INSERT INTO rehab_hep_template_item " +
                        "(tenant_id,template_id,exercise_id,training_day_no,group_code,sort_order,dosage_json,instruction_text,creator,updater) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)", tenantId(), templateId, number(item, "exerciseId"),
                        Optional.ofNullable(number(item, "trainingDayNo")).orElse(1L),
                        StrUtil.blankToDefault(text(item, "groupCode"), "A"),
                        Optional.ofNullable(number(item, "sortOrder")).orElse((long) i + 1),
                        text(item, "dosageJson"), text(item, "instructionText"), String.valueOf(userId), String.valueOf(userId));
            }
        }
        return templateId;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createExercise(Map<String, Object> body, Long userId) {
        String code = text(body, "exerciseCode");
        String name = text(body, "name");
        if (StrUtil.isBlank(code) || StrUtil.isBlank(name)) {
            throw ServiceExceptionUtil.exception(APP_PATIENT_INVITATION_INVALID);
        }
        return insertAndReturnId("INSERT INTO rehab_hep_exercise " +
                "(tenant_id,exercise_code,name,body_region,target_deficit,instruction_text,contraindications,media_url,source_type,source_reference,license_status,creator,updater) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?, ?, ?)", tenantId(), code, name, text(body, "bodyRegion"),
                text(body, "targetDeficit"), text(body, "instructionText"), text(body, "contraindications"),
                text(body, "mediaUrl"), text(body, "sourceType"), text(body, "sourceReference"),
                StrUtil.blankToDefault(text(body, "licenseStatus"), "PENDING"), String.valueOf(userId), String.valueOf(userId));
    }

    public void reviewExercise(Long exerciseId, Long userId) {
        int updated = jdbcTemplate.update("UPDATE rehab_hep_exercise SET status='REVIEWED',reviewed_by=?,reviewed_time=NOW(),updater=? " +
                "WHERE tenant_id=? AND id=? AND deleted=0", userId, String.valueOf(userId), tenantId(), exerciseId);
        if (updated != 1) {
            throw ServiceExceptionUtil.exception(PATIENT_NOT_EXISTS);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long createPrescription(Map<String, Object> body, Long therapistUserId) {
        Long patientId = number(body, "patientId");
        Long episodeId = number(body, "episodeId");
        if (patientId == null || episodeId == null || therapistUserId == null ||
                date(body, "startDate") == null ||
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rehab_patient WHERE id=? AND tenant_id=? AND deleted=0",
                    Integer.class,patientId,tenantId()) != 1 ||
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rehab_episode WHERE id=? AND tenant_id=? AND patient_id=? AND status IN ('active','paused') AND deleted=0",
                    Integer.class,episodeId,tenantId(),patientId) != 1 ||
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rehab_therapist_assignment WHERE patient_id=? AND tenant_id=? AND therapist_user_id=? AND assign_status='active' AND deleted=0",
                    Integer.class,patientId,tenantId(),therapistUserId) < 1) {
            throw ServiceExceptionUtil.exception(PATIENT_NOT_EXISTS);
        }
        String no = StrUtil.blankToDefault(text(body, "prescriptionNo"), "HEP-" + System.currentTimeMillis());
        Long prescriptionId = insertAndReturnId("INSERT INTO rehab_hep_prescription " +
                "(tenant_id,prescription_no,patient_id,episode_id,core_plan_id,therapist_user_id,template_id,start_date,end_date,creator,updater) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)", tenantId(), no, patientId, episodeId, number(body, "corePlanId"),
                therapistUserId, number(body, "templateId"), date(body, "startDate"), date(body, "endDate"),
                String.valueOf(therapistUserId), String.valueOf(therapistUserId));
        Long versionId = insertAndReturnId("INSERT INTO rehab_hep_prescription_version " +
                "(tenant_id,prescription_id,version_no,status,change_reason,source_template_id,creator,updater) VALUES (?,?,1,'DRAFT',?,?,?,?)",
                tenantId(), prescriptionId, text(body, "changeReason"), number(body, "templateId"),
                String.valueOf(therapistUserId), String.valueOf(therapistUserId));
        jdbcTemplate.update("UPDATE rehab_hep_prescription SET current_version_id=? WHERE tenant_id=? AND id=?",
                versionId, tenantId(), prescriptionId);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                Long exerciseId = number(item, "exerciseId");
                if (exerciseId == null || jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rehab_hep_exercise " +
                        "WHERE tenant_id=? AND id=? AND status='REVIEWED' AND deleted=0", Integer.class, tenantId(), exerciseId) != 1) {
                    throw ServiceExceptionUtil.exception(PATIENT_NOT_EXISTS);
                }
                jdbcTemplate.update("INSERT INTO rehab_hep_prescription_item " +
                        "(tenant_id,version_id,exercise_id,training_day_no,group_code,sort_order,dosage_json,instruction_text,pain_limit_rule,creator,updater) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)", tenantId(), versionId, exerciseId,
                        Optional.ofNullable(number(item, "trainingDayNo")).orElse(1L),
                        StrUtil.blankToDefault(text(item, "groupCode"), "A"),
                        Optional.ofNullable(number(item, "sortOrder")).orElse((long) i + 1),
                        text(item, "dosageJson"), text(item, "instructionText"), text(item, "painLimitRule"),
                        String.valueOf(therapistUserId), String.valueOf(therapistUserId));
            }
        }
        return prescriptionId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void issuePrescription(Long prescriptionId, Long therapistUserId) {
        List<Long> versions = jdbcTemplate.query("SELECT p.current_version_id FROM rehab_hep_prescription p " +
                "JOIN rehab_patient patient ON patient.id=p.patient_id AND patient.tenant_id=p.tenant_id AND patient.deleted=0 " +
                "JOIN rehab_episode ep ON ep.id=p.episode_id AND ep.tenant_id=p.tenant_id AND ep.patient_id=p.patient_id AND ep.status IN ('active','paused') AND ep.deleted=0 " +
                "JOIN rehab_therapist_assignment a ON a.patient_id=p.patient_id AND a.tenant_id=p.tenant_id AND a.therapist_user_id=? AND a.assign_status='active' AND a.deleted=0 " +
                "WHERE p.tenant_id=? AND p.id=? AND p.therapist_user_id=? AND p.status='DRAFT' AND p.start_date IS NOT NULL AND p.deleted=0",
                (rs,i)->rs.getLong(1),therapistUserId,tenantId(),prescriptionId,therapistUserId);
        if (versions.size()!=1 || versions.get(0)==null || versions.get(0)<=0) {
            throw ServiceExceptionUtil.exception(PATIENT_NOT_EXISTS);
        }
        Long versionId = versions.get(0);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT training_day_no,group_code FROM rehab_hep_prescription_item " +
                "WHERE tenant_id=? AND version_id=? AND deleted=0 ORDER BY training_day_no,sort_order", tenantId(), versionId);
        if (items.isEmpty()) {
            throw ServiceExceptionUtil.exception(APP_PATIENT_INVITATION_INVALID);
        }
        if (jdbcTemplate.update("UPDATE rehab_hep_prescription_version SET status='ISSUED',issued_by=?,issued_time=NOW(),reviewed_by=?,reviewed_time=NOW() " +
                "WHERE tenant_id=? AND id=? AND prescription_id=? AND status='DRAFT'",
                therapistUserId,therapistUserId,tenantId(),versionId,prescriptionId) != 1 ||
            jdbcTemplate.update("UPDATE rehab_hep_prescription SET status='ISSUED',core_sync_status='PENDING' WHERE tenant_id=? AND id=? AND status='DRAFT'",
                tenantId(),prescriptionId) != 1) {
            throw ServiceExceptionUtil.exception(PATIENT_NOT_EXISTS);
        }
        Set<Integer> days = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            days.add(((Number) item.get("training_day_no")).intValue());
        }
        for (Integer day : days) {
            Long dayId = insertAndReturnId("INSERT IGNORE INTO rehab_hep_training_day " +
                    "(tenant_id,version_id,day_no,status,creator,updater) VALUES (?,?,?,'PENDING',?,?)",
                    tenantId(), versionId, day, String.valueOf(therapistUserId), String.valueOf(therapistUserId));
            for (Map<String, Object> item : items) {
                if (((Number) item.get("training_day_no")).intValue() == day) {
                    jdbcTemplate.update("INSERT IGNORE INTO rehab_hep_training_group " +
                            "(tenant_id,training_day_id,group_code,group_name,sort_order,creator,updater) VALUES (?,?,?, ?,?, ?,?)",
                            tenantId(), dayId, item.get("group_code"), item.get("group_code"), 1,
                            String.valueOf(therapistUserId), String.valueOf(therapistUserId));
                }
            }
        }
    }

    public Map<String, Object> currentForPatient(Long patientId) {
        List<Map<String, Object>> plans = jdbcTemplate.queryForList("SELECT id,prescription_no,patient_id,episode_id,current_version_id,start_date,end_date " +
                "FROM rehab_hep_prescription WHERE tenant_id=? AND patient_id=? AND status='ISSUED' AND deleted=0 " +
                "AND (start_date IS NULL OR start_date<=CURRENT_DATE) AND (end_date IS NULL OR end_date>=CURRENT_DATE) " +
                "ORDER BY id DESC LIMIT 1", tenantId(), patientId);
        if (plans.isEmpty()) return Collections.emptyMap();
        Map<String, Object> plan = new LinkedHashMap<>(plans.get(0));
        Long versionId = ((Number) plan.get("current_version_id")).longValue();
        plan.put("items", jdbcTemplate.queryForList("SELECT i.exercise_id,e.exercise_code,e.name,i.training_day_no,i.group_code,i.sort_order,i.dosage_json,i.instruction_text,i.pain_limit_rule " +
                "FROM rehab_hep_prescription_item i JOIN rehab_hep_exercise e ON e.id=i.exercise_id AND e.tenant_id=i.tenant_id " +
                "WHERE i.tenant_id=? AND i.version_id=? AND i.deleted=0 ORDER BY i.training_day_no,i.sort_order", tenantId(), versionId));
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long submitFeedback(Map<String, Object> body, Long appUserId) {
        RehabPatientUserBindingDO binding = bindingMapper.selectActiveByAppUserId(appUserId);
        if (binding == null) throw ServiceExceptionUtil.exception(APP_PATIENT_BINDING_REQUIRED);
        Long patientId = binding.getPatientId();
        Long prescriptionId = number(body, "prescriptionId");
        Long versionId = number(body, "versionId");
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rehab_hep_prescription WHERE tenant_id=? AND id=? AND patient_id=? AND status='ISSUED'",
                Integer.class, tenantId(), prescriptionId, patientId);
        if (count == null || count != 1) throw ServiceExceptionUtil.exception(PATIENT_NO_PERMISSION);
        return insertAndReturnId("INSERT INTO rehab_hep_execution_feedback " +
                "(tenant_id,patient_id,prescription_id,version_id,training_day_id,group_code,submitted_by_member_id,completion_status,pain_before,pain_after,rpe,abnormal_flag,feedback_text,creator,updater) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", tenantId(), patientId, prescriptionId, versionId,
                number(body, "trainingDayId"), text(body, "groupCode"), appUserId,
                StrUtil.blankToDefault(text(body, "completionStatus"), "COMPLETED"), body.get("painBefore"), body.get("painAfter"),
                body.get("rpe"), Boolean.TRUE.equals(body.get("abnormalFlag")) ? 1 : 0, text(body, "feedbackText"),
                String.valueOf(appUserId), String.valueOf(appUserId));
    }

    public void reviewFeedback(Long feedbackId, Long therapistUserId, String status) {
        jdbcTemplate.update("UPDATE rehab_hep_execution_feedback SET therapist_review_status=?,reviewed_by=?,reviewed_time=NOW(),core_sync_status='SYNCED',updater=? " +
                "WHERE tenant_id=? AND id=?", StrUtil.blankToDefault(status, "REVIEWED"), therapistUserId,
                String.valueOf(therapistUserId), tenantId(), feedbackId);
    }

    private Long insertAndReturnId(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long number(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private static java.sql.Date date(Map<String, Object> body, String key) {
        String value = text(body, key);
        return StrUtil.isBlank(value) ? null : java.sql.Date.valueOf(LocalDate.parse(value));
    }
}
