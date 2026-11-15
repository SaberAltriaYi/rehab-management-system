package cn.iocoder.yudao.module.rehab.service;

import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.episode.RehabEpisodeMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentRecordMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.assessment.RehabAssessmentModuleDataMapper;
import cn.iocoder.yudao.module.rehab.dal.mysql.report.RehabReportMapper;
import org.mockito.Answers;
import static org.mockito.Mockito.mock;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
 * Bridges existing strict service fixtures to current-read ports. NOT a DB/lock
 * proof; the real component is tested separately with H2 and MySQL transactions.
 */
public final class ClinicalSourcesTestAdapter {
    public static RehabClinicalSources bridge(RehabPatientMapper patients, RehabEpisodeMapper episodes,
            RehabAssessmentRecordMapper assessments, RehabAssessmentModuleDataMapper modules, RehabReportMapper reports) {
        return bridge(patients, episodes, assessments, modules, reports, null);
    }
    public static RehabClinicalSources bridge(RehabPatientMapper patients, RehabEpisodeMapper episodes,
            RehabAssessmentRecordMapper assessments, RehabAssessmentModuleDataMapper modules, RehabReportMapper reports,
            cn.iocoder.yudao.module.rehab.dal.mysql.plan.RehabCarePlanMapper plans) {
        return mock(RehabClinicalSources.class, call -> {
            if (call.getArguments().length == 0) return Answers.RETURNS_DEFAULTS.answer(call);
            Long id = call.getArgument(0);
            switch (call.getMethod().getName()) {
                case "patient": return patients == null ? RehabPatientDO.builder().id(id).build() : patients.selectById(id);
                case "assessment": return assessments.selectById(id);
                case "episode": return episodes.selectById(id);
                case "activeEpisode": return episodes.selectActiveByPatientId(id);
                case "plan": return plans.selectById(id);
                case "activePlan": return plans.selectActiveByPatientEpisode(id, call.getArgument(1));
                case "modules": return modules.selectListByAssessmentId(id);
                case "module": return modules.selectByAssessmentIdAndModuleType(id, call.getArgument(1));
                case "hasAssessmentReports": return !reports.selectListByAssessmentId(id).isEmpty();
                case "hasReferences": return episodes.selectCount("patient_id", id) > 0 || assessments.selectCountByPatientId(id) > 0 || reports.selectCountByPatientId(id) > 0;
                default: return Answers.RETURNS_DEFAULTS.answer(call);
            }
        });
    }
}
