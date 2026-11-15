package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.episode.RehabEpisodeDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentRecordDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentModuleDataDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
 * Current reads in the caller transaction. Clinical writers take patient first,
 * then assessment/episode/modules; never reuse an earlier RR snapshot as current.
 * Callers must authorize before entering; this is not an authorization service.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RehabClinicalSources {
    static final String PATIENT_SQL = "SELECT * FROM rehab_patient WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE";
    static final String ASSESSMENT_SQL = "SELECT * FROM rehab_assessment_record WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE";
    static final String EPISODE_SQL = "SELECT * FROM rehab_episode WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE";
    static final String MODULES_SQL = "SELECT * FROM rehab_assessment_module_data WHERE tenant_id=? AND assessment_id=? AND deleted=FALSE ORDER BY module_type,id FOR UPDATE";
    static final String ACTIVE_EPISODE_SQL = "SELECT * FROM rehab_episode WHERE tenant_id=? AND patient_id=? AND deleted=FALSE AND status='active' ORDER BY create_time DESC,id DESC LIMIT 1 FOR UPDATE";
    static final String PLAN_SQL = "SELECT * FROM rehab_care_plan WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE";
    static final String ACTIVE_PLAN_SQL = "SELECT * FROM rehab_care_plan WHERE tenant_id=? AND patient_id=? AND episode_id=? AND status='active' AND deleted=FALSE ORDER BY id DESC LIMIT 1 FOR UPDATE";
    @Resource private JdbcTemplate jdbcTemplate;

    private long tenant(Long id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("临床源数据读取必须位于业务事务内");
        if (id == null || id <= 0) throw new IllegalArgumentException("临床记录编号无效");
        return TenantContextHolder.getRequiredTenantId();
    }
    private <T> T one(String sql, Class<T> type, Long id) {
        List<T> rows = jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(type), tenant(id), id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public RehabPatientDO patient(Long id) {
        RehabPatientDO value = one(PATIENT_SQL, RehabPatientDO.class, id);
        if (value == null) throw exception(PATIENT_NOT_EXISTS);
        return value;
    }
    public RehabAssessmentRecordDO assessment(Long id) {
        RehabAssessmentRecordDO value = one(ASSESSMENT_SQL, RehabAssessmentRecordDO.class, id);
        if (value == null) throw exception(ASSESSMENT_NOT_EXISTS);
        return value;
    }
    public RehabEpisodeDO episode(Long id) {
        RehabEpisodeDO value = one(EPISODE_SQL, RehabEpisodeDO.class, id);
        if (value == null) throw exception(EPISODE_NOT_EXISTS);
        return value;
    }
    public List<RehabAssessmentModuleDataDO> modules(Long assessmentId) {
        return jdbcTemplate.query(MODULES_SQL, BeanPropertyRowMapper.newInstance(RehabAssessmentModuleDataDO.class), tenant(assessmentId), assessmentId);
    }
    public RehabAssessmentModuleDataDO module(Long assessmentId, String type) {
        return modules(assessmentId).stream().filter(value -> java.util.Objects.equals(type, value.getModuleType())).findFirst().orElse(null);
    }
    public RehabEpisodeDO activeEpisode(Long patientId) { return one(ACTIVE_EPISODE_SQL, RehabEpisodeDO.class, patientId); }
    public RehabCarePlanDO plan(Long id) {
        RehabCarePlanDO value = one(PLAN_SQL, RehabCarePlanDO.class, id);
        if (value == null) throw exception(PLAN_NOT_EXISTS);
        return value;
    }
    public RehabCarePlanDO activePlan(Long patientId, Long episodeId) {
        List<RehabCarePlanDO> rows = jdbcTemplate.query(ACTIVE_PLAN_SQL, BeanPropertyRowMapper.newInstance(RehabCarePlanDO.class), tenant(patientId), patientId, episodeId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public boolean hasAssessmentReports(Long assessmentId) {
        long tenant = tenant(assessmentId);
        if (!jdbcTemplate.queryForList("SELECT id FROM rehab_report WHERE tenant_id=? AND assessment_id=? AND deleted=FALSE LIMIT 1 FOR UPDATE", tenant, assessmentId).isEmpty()) return true;
        return !jdbcTemplate.queryForList("SELECT id FROM rehab_care_plan WHERE tenant_id=? AND source_assessment_id=? AND deleted=FALSE LIMIT 1 FOR UPDATE", tenant, assessmentId).isEmpty();
    }
    public boolean hasReferences(Long patientId) {
        long tenant = tenant(patientId);
        // Fixed table allowlist only; no user-controlled SQL identifier.
        for (String table : new String[]{"rehab_episode", "rehab_assessment_record", "rehab_report"}) {
            if (!jdbcTemplate.queryForList("SELECT id FROM " + table + " WHERE tenant_id=? AND patient_id=? AND deleted=FALSE LIMIT 1 FOR UPDATE", tenant, patientId).isEmpty()) return true;
        }
        return false;
    }
}
