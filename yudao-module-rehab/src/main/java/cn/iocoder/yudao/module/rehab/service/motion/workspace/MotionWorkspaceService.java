package cn.iocoder.yudao.module.rehab.service.motion.workspace;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionAssessmentMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionAccess;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionEntitlement;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Resource;

/** Independent inbox: never overwrites clinical scores, original assessments or signed reports. */
@Service
public class MotionWorkspaceService {
    @Resource private RehabMotionAccess access;
    @Resource private RehabMotionEntitlement entitlement;
    @Resource private PermissionApi permissionApi;
    @Resource private RehabMotionAssessmentMapper mapper;
    @Resource private MotionWorkspaceClient client;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private RehabPatientMapper patientMapper;

    private RehabMotionAssessmentDO allowed(Long id, Long user, String permission) {
        if (!entitlement.enabled()
                || !permissionApi.hasAnyPermissions(user, "rehab:motion:query")
                || !permissionApi.hasAnyPermissions(user, permission)) {
            throw new AccessDeniedException("未开通或没有运动评估工作区权限");
        }
        access.requireClinician(user);
        RehabMotionAssessmentDO a = access.readable(id, user);
        if (a.getTenantId() == null || a.getInputRevision() == null)
            throw new IllegalStateException("缺少机构或输入修订");
        return a;
    }

    public Map<String, Object> launch(Long id, Long user) {
        RehabMotionAssessmentDO a = allowed(id, user, "rehab:motion:workspace");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenant", a.getTenantId().toString());
        data.put("assessment", id.toString());
        data.put("patient", a.getPatientId().toString());
        data.put("episode", a.getEpisodeId() == null ? null : a.getEpisodeId().toString());
        data.put("core_revision", a.getInputRevision());
        data.put("user", user.toString());
        RehabPatientDO patient = patientMapper.selectById(a.getPatientId());
        Integer age = patient == null ? null : patient.getAge();
        if (patient != null && patient.getBirthday() != null)
            age =
                    java.time.Period.between(patient.getBirthday(), java.time.LocalDate.now())
                            .getYears();
        data.put("population", age == null || age < 18 ? "child" : "adult_rehab");
        data.put("video_consent", Boolean.TRUE.equals(a.getVideoConsent()));
        List<String> rights = new ArrayList<>();
        rights.add("view");
        if (!"COMPLETED".equals(a.getStatus())
                && permissionApi.hasAnyPermissions(user, "rehab:motion:create")
                && permissionApi.hasAnyPermissions(user, "rehab:motion:upload")) rights.add("edit");
        if (!"COMPLETED".equals(a.getStatus())
                && permissionApi.hasAnyPermissions(user, "rehab:motion:review"))
            rights.add("review");
        if (!"COMPLETED".equals(a.getStatus())
                && permissionApi.hasAnyPermissions(user, "rehab:motion:sync")) rights.add("sync");
        if (!"COMPLETED".equals(a.getStatus())
                && permissionApi.hasAnyPermissions(user, "rehab:motion:sign")) rights.add("sign");
        data.put("rights", rights);
        Map<String, Object> response =
                client.exchange("/internal/core/launch", HttpMethod.POST, data);
        access.audit(id, "workspace_launch", user, null, null, null);
        return Collections.singletonMap("url", client.entryUrl((String) response.get("ticket")));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validateSnapshot(
            Map<String, Object> result, RehabMotionAssessmentDO a) {
        String raw = (String) result.get("snapshot_json");
        String hash = (String) result.get("sha256");
        if (raw == null
                || raw.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024
                || !DigestUtil.sha256Hex(raw).equals(hash))
            throw new IllegalStateException("同步结果哈希或体积无效");
        Map<String, Object> snapshot = JsonUtils.parseObject(raw, Map.class);
        Map<String, Object> binding = (Map<String, Object>) snapshot.get("binding");
        if (!"rehab-workspace-sync/1".equals(snapshot.get("schema"))
                || binding == null
                || !Objects.equals(String.valueOf(a.getTenantId()), binding.get("tenant"))
                || !Objects.equals(String.valueOf(a.getId()), binding.get("assessment"))
                || !Objects.equals(String.valueOf(a.getPatientId()), binding.get("patient"))
                || !Objects.equals(
                        a.getEpisodeId() == null ? null : a.getEpisodeId().toString(),
                        binding.get("episode"))
                || !Objects.equals(a.getInputRevision(), binding.get("core_revision"))
                || !(result.get("revision") instanceof Number)
                || !Objects.equals(result.get("revision"), snapshot.get("revision"))
                || ((Number) result.get("revision")).intValue() < 1
                || !(snapshot.get("observations") instanceof List)
                || ((List<?>) snapshot.get("observations")).isEmpty()) {
            throw new IllegalStateException("患者、机构、周期或输入修订不匹配，拒绝旧数据/错误绑定");
        }
        return snapshot;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receive(Long id, Long user) {
        RehabMotionAssessmentDO a = allowed(id, user, "rehab:motion:sync");
        mapper.lockById(id);
        a = access.editable(id, user);
        Map<String, Object> result =
                client.exchange(
                        "/internal/core/result?tenant=" + a.getTenantId() + "&assessment=" + id,
                        HttpMethod.GET,
                        null);
        Map<String, Object> snapshot = validateSnapshot(result, a);
        String source = (String) snapshot.get("assessment_id");
        if (source == null || !source.matches("a-[a-f0-9]{24}"))
            throw new IllegalStateException("来源编号无效");
        List<Map<String, Object>> old =
                jdbcTemplate.queryForList(
                        "SELECT sha256 FROM rehab_motion_workspace_inbox WHERE tenant_id=? AND"
                            + " assessment_id=? AND source_id=? AND source_revision=?",
                        a.getTenantId(),
                        id,
                        source,
                        result.get("revision"));
        if (!old.isEmpty()) {
            if (!Objects.equals(old.get(0).get("sha256"), result.get("sha256")))
                throw new IllegalStateException("同一修订内容冲突");
            return Collections.singletonMap("state", "already_received");
        }
        jdbcTemplate.update(
                "INSERT INTO"
                    + " rehab_motion_workspace_inbox(tenant_id,assessment_id,patient_id,source_id,source_revision,core_revision,sha256,snapshot_json,received_by)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)",
                a.getTenantId(),
                id,
                a.getPatientId(),
                source,
                result.get("revision"),
                a.getInputRevision(),
                result.get("sha256"),
                result.get("snapshot_json"),
                user);
        access.audit(
                id,
                "workspace_received",
                user,
                null,
                Collections.singletonMap("sha256", result.get("sha256")),
                null);
        return Collections.singletonMap("state", "received_pending_clinical_review");
    }

    public List<Map<String, Object>> history(Long id, Long user) {
        RehabMotionAssessmentDO a = allowed(id, user, "rehab:motion:workspace");
        return jdbcTemplate.queryForList(
                "SELECT"
                    + " id,source_revision,core_revision,sha256,snapshot_json,received_at,review_status,review_note,reviewed_by"
                    + " FROM rehab_motion_workspace_inbox WHERE tenant_id=? AND assessment_id=? AND"
                    + " patient_id=? ORDER BY id DESC LIMIT 100",
                a.getTenantId(),
                id,
                a.getPatientId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long assessmentId, Long inboxId, String note, Long user) {
        RehabMotionAssessmentDO a = allowed(assessmentId, user, "rehab:motion:review");
        mapper.lockById(assessmentId);
        a = access.editable(assessmentId, user);
        if (note == null || note.trim().isEmpty() || note.length() > 1000)
            throw new IllegalArgumentException("请填写1000字以内的复核结论");
        int changed =
                jdbcTemplate.update(
                        "UPDATE rehab_motion_workspace_inbox SET"
                            + " review_status='reviewed',review_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP"
                            + " WHERE tenant_id=? AND assessment_id=? AND patient_id=? AND id=? AND"
                            + " core_revision=? AND review_status='pending'",
                        note.trim(),
                        user,
                        a.getTenantId(),
                        assessmentId,
                        a.getPatientId(),
                        inboxId,
                        a.getInputRevision());
        if (changed != 1) throw new IllegalStateException("结果不存在、已复核或主系统输入修订已变化");
        access.audit(
                assessmentId,
                "workspace_reviewed",
                user,
                null,
                Collections.singletonMap("inboxId", inboxId),
                null);
    }
}
