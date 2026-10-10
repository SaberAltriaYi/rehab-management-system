package cn.iocoder.yudao.module.rehab.service.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 青少年 B 版生产母版的报告前置质控提示（不涉及自动临床解释）。
 *
 * <p>旧版 V4.1 报告是 raw-data-only，不具有 B 版临床推理、证据审查、家长解释和签署前
 * 的生产闭环。此检查只记录可验证的缺失/待审项，绝不自动把缺失值变为正常值，或把
 * 已生成的原始数据报告误标为符合 B 版标准的正式报告。</p>
 */
public final class AdolescentReportQualityPolicy {

    public static final String STANDARD = "ADOLESCENT_THERAPIST_B_V1";
    public static final String STATUS = "SOURCE_REVIEW_REQUIRED";

    private AdolescentReportQualityPolicy() {
    }

    public static Map<String, Object> audit(Map<String, Object> patient, Map<String, Object> assessment,
                                            Map<String, Object> rawModules) {
        List<Map<String, String>> findings = new ArrayList<>();
        if (isBlank(patient.get("birthday"))) {
            add(findings, "GROWTH_BIRTH_DATE_MISSING", "warning", "patient.birthday",
                    "出生日期未提供；不可推断月龄、儿童 BMI 百分位或骨龄");
        }
        if (isBlank(assessment.get("assessmentDate"))) {
            add(findings, "ASSESSMENT_DATE_MISSING", "blocker", "assessment.assessmentDate",
                    "原始评估日期缺失，无法确认每个证据的时间基线");
        }
        if (isBlank(assessment.get("redFlagNotes"))) {
            add(findings, "RED_FLAG_SCREEN_NOT_DOCUMENTED", "blocker", "assessment.redFlagNotes",
                    "红旗筛查未记录；不能将未记录解读为阴性或允许负荷训练");
        }
        if (isBlank(assessment.get("sourceSummary"))) {
            add(findings, "SOURCE_REGISTER_NEEDS_REVIEW", "blocker", "assessment.sourceSummary",
                    "证据来源清单未记录；需建立实际测试日期和 Source ID");
        }
        if (rawModules.isEmpty()) {
            add(findings, "ALL_ASSESSMENT_MODULES_MISSING", "warning", "rawModules",
                    "没有任何原始评估模块；应列出未测项目及对决策的影响");
        }
        add(findings, "CLINICAL_SYNTHESIS_NOT_GENERATED", "blocker", "sections[13..17]",
                "现有 V4.1 原始数据版未自动生成证据互证/反证、四域风险、KPI、训练方向与复评闭环；必须治疗师补充审核");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("standard", STANDARD);
        result.put("status", STATUS);
        result.put("clinicalReady", false);
        result.put("provenanceVerified", false);
        result.put("findings", findings);
        result.put("notice", "当前仅为原始数据汇总，不等于 B 版正式临床报告；报告生成后仍须完成冲突核查、数据锁定、证据索引、治疗师审核和逐页版式检查");
        return result;
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private static void add(List<Map<String, String>> findings, String code, String severity, String field,
                            String message) {
        Map<String, String> finding = new LinkedHashMap<>();
        finding.put("code", code);
        finding.put("severity", severity);
        finding.put("field", field);
        finding.put("message", message);
        findings.add(finding);
    }
}
