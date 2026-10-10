package cn.iocoder.yudao.module.rehab.service.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * B 版证据包结构与来源核对的纯函数校验器，输入只允许去标识的合成/授权结构化数据。
 *
 * <p>这不是报告生成器、患者数据仓库或临床审批服务。仅核查 Source ID 引用、QEI 有效性、
 * 冲突解决记录及来源冻结版本，绝不改写原始分数、推断缺失项或自动签署。</p>
 * <p>findings 仅返回固定错误码与字段索引，绝不回显原始测量、患者身份或自由文本。</p>
 */
public final class AdolescentEvidencePackagePolicy {

    public static final String SCHEMA = "ADOLESCENT_EVIDENCE_B_V1_DRAFT";
    private static final Pattern SOURCE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Pattern REVISION = Pattern.compile("^[1-9][0-9]{0,17}$");
    private static final int MAX_PACKAGE_FIELDS = 32;
    private static final int MAX_OBJECT_FIELDS = 64;
    private static final int MAX_REGISTER_RECORDS = 512;
    private static final int MAX_REFERENCE_IDS = 64;
    private static final Set<String> SOURCE_TYPES = new HashSet<>(Arrays.asList(
            "RAW_DATA", "ONSITE_OBSERVATION", "DEVICE_ALGORITHM",
            "FUNCTIONAL_INFERENCE", "MEDICAL_IMAGING", "WORKING_HYPOTHESIS"));
    private static final Set<String> VALIDITY = new HashSet<>(Arrays.asList(
            "VALID", "INVALID", "FAILED", "NOT_TESTED", "PLACEHOLDER"));
    private static final Set<String> RISK_DOMAINS = new HashSet<>(Arrays.asList(
            "medical_safety", "symptom", "movement_control", "growth_and_measurement"));
    private static final Set<String> TRAINING_DECISIONS = new HashSet<>(Arrays.asList(
            "PENDING_REVIEW", "HOLD", "REFER", "CONDITIONAL_TRAINING"));

    private AdolescentEvidencePackagePolicy() {
    }

    public static Map<String, Object> audit(Map<String, Object> evidencePackage) {
        List<Map<String, String>> findings = new ArrayList<>();
        if (evidencePackage == null) {
            add(findings, "EVIDENCE_PACKAGE_MISSING", "package");
            return result(findings, 0);
        }
        if (evidencePackage.size() > MAX_PACKAGE_FIELDS) {
            add(findings, "EVIDENCE_PACKAGE_TOO_WIDE", "package");
        }
        if (!SCHEMA.equals(string(evidencePackage.get("schema")))) {
            add(findings, "EVIDENCE_SCHEMA_UNSUPPORTED", "schema");
        }
        Object caseRef = evidencePackage.get("case_ref");
        if (blank(caseRef)) {
            add(findings, "CASE_REFERENCE_MISSING", "case_ref");
        } else if (!(caseRef instanceof String)
                || ((String) caseRef).length() > 128
                || ((String) caseRef).codePointCount(0, ((String) caseRef).length()) > 64) {
            // Match the JSON Schema's string/maxLength contract. A number
            // converted by String.valueOf is not a de-identified case ID.
            add(findings, "CASE_REFERENCE_INVALID", "case_ref");
        }
        Map<String, Object> assessment = object(evidencePackage, "assessment", findings, "assessment");
        String revision = string(assessment.get("locked_revision"));
        if (!REVISION.matcher(revision).matches()) {
            add(findings, "LOCKED_REVISION_MISSING", "assessment.locked_revision");
        }
        if (blank(assessment.get("id"))) {
            add(findings, "ASSESSMENT_REFERENCE_MISSING", "assessment.id");
        } else if (!(assessment.get("id") instanceof String)) {
            add(findings, "ASSESSMENT_REFERENCE_INVALID", "assessment.id");
        }
        if (!date(assessment.get("date"))) {
            add(findings, "ASSESSMENT_DATE_INVALID", "assessment.date");
        }

        List<?> sources = list(evidencePackage, "sources", findings);
        Set<String> sourceIds = new HashSet<>();
        if (sources.isEmpty()) {
            add(findings, "SOURCE_REGISTER_EMPTY", "sources");
        }
        for (int i = 0; i < sources.size(); i++) {
            String prefix = "sources[" + i + "]";
            Map<String, Object> source = item(sources.get(i), findings, prefix);
            String id = string(source.get("source_id"));
            if (!SOURCE_ID.matcher(id).matches()) {
                add(findings, "SOURCE_ID_INVALID", prefix + ".source_id");
            } else if (!sourceIds.add(id)) {
                add(findings, "SOURCE_ID_DUPLICATED", prefix + ".source_id");
            }
            if (!SOURCE_TYPES.contains(string(source.get("type")))) {
                add(findings, "SOURCE_TYPE_INVALID", prefix + ".type");
            }
            if (!date(source.get("capture_date"))) {
                add(findings, "SOURCE_CAPTURE_DATE_INVALID", prefix + ".capture_date");
            }
            if (blank(source.get("method"))) {
                add(findings, "SOURCE_METHOD_MISSING", prefix + ".method");
            }
            if (blank(source.get("permission_scope"))) {
                add(findings, "SOURCE_PERMISSION_SCOPE_MISSING", prefix + ".permission_scope");
            }
            if (!blank(source.get("file_hash"))
                    && !SHA256.matcher(string(source.get("file_hash"))).matches()) {
                add(findings, "SOURCE_FILE_HASH_INVALID", prefix + ".file_hash");
            }
        }

        List<?> measurements = list(evidencePackage, "measurements", findings);
        for (int i = 0; i < measurements.size(); i++) {
            String prefix = "measurements[" + i + "]";
            Map<String, Object> measurement = item(measurements.get(i), findings, prefix);
            reference(measurement.get("source_id"), sourceIds, findings, prefix + ".source_id");
            String validity = string(measurement.get("validity"));
            if (!VALIDITY.contains(validity)) {
                add(findings, "MEASUREMENT_VALIDITY_INVALID", prefix + ".validity");
            } else if ("VALID".equals(validity)) {
                if (!numeric(measurement.get("value"))) {
                    add(findings, "VALID_MEASUREMENT_VALUE_INVALID", prefix + ".value");
                }
                if (blank(measurement.get("unit"))) {
                    add(findings, "VALID_MEASUREMENT_UNIT_MISSING", prefix + ".unit");
                }
                if (("YBT_UQ".equals(string(measurement.get("task")))
                        || "YBT_LQ".equals(string(measurement.get("task"))))
                        && numeric(measurement.get("value"))
                        && new BigDecimal(string(measurement.get("value"))).signum() <= 0) {
                    add(findings, "YBT_DISTANCE_NOT_VALID", prefix + ".value");
                }
            } else {
                if (measurement.get("value") != null) {
                    add(findings, "INVALID_MEASUREMENT_HAS_VALUE", prefix + ".value");
                }
                if (blank(measurement.get("missing_reason"))) {
                    add(findings, "MEASUREMENT_REASON_MISSING", prefix + ".missing_reason");
                }
            }
        }

        List<?> observations = list(evidencePackage, "observations", findings);
        for (int i = 0; i < observations.size(); i++) {
            String prefix = "observations[" + i + "]";
            Map<String, Object> observation = item(observations.get(i), findings, prefix);
            if (blank(observation.get("task"))) {
                add(findings, "OBSERVATION_TASK_MISSING", prefix + ".task");
            }
            references(observation.get("evidence_ids"), sourceIds, findings, prefix + ".evidence_ids", true);
            String sfma = string(observation.get("score"));
            if ("SFMA".equals(string(observation.get("task")))) {
                if (!Arrays.asList("FN", "FP", "DN", "DP").contains(sfma)) {
                    add(findings, "SFMA_CODE_INVALID", prefix + ".score");
                }
                if (!numeric(observation.get("pain"))) {
                    add(findings, "SFMA_PAIN_NOT_DOCUMENTED", prefix + ".pain");
                } else {
                    BigDecimal pain = new BigDecimal(string(observation.get("pain")));
                    if (pain.signum() < 0 || pain.compareTo(BigDecimal.TEN) > 0) {
                        add(findings, "SFMA_PAIN_INVALID", prefix + ".pain");
                    } else if (((sfma.equals("FN") || sfma.equals("DN")) && pain.signum() > 0)
                            || ((sfma.equals("FP") || sfma.equals("DP")) && pain.signum() == 0)) {
                        add(findings, "SFMA_PAIN_CODE_CONFLICT", prefix);
                    }
                }
            }
        }

        List<?> conflicts = list(evidencePackage, "conflicts", findings);
        for (int i = 0; i < conflicts.size(); i++) {
            String prefix = "conflicts[" + i + "]";
            Map<String, Object> conflict = item(conflicts.get(i), findings, prefix);
            references(conflict.get("conflicting_sources"), sourceIds, findings,
                    prefix + ".conflicting_sources", true);
            if (!"RESOLVED".equals(string(conflict.get("state")))) {
                add(findings, "CONFLICT_UNRESOLVED", prefix + ".state");
            } else if (blank(conflict.get("resolution")) || blank(conflict.get("resolved_by"))
                    || !timestamp(conflict.get("timestamp"))) {
                add(findings, "CONFLICT_RESOLUTION_AUDIT_MISSING", prefix);
            }
        }

        List<?> interpretations = list(evidencePackage, "interpretations", findings);
        for (int i = 0; i < interpretations.size(); i++) {
            String prefix = "interpretations[" + i + "]";
            Map<String, Object> interpretation = item(interpretations.get(i), findings, prefix);
            references(interpretation.get("evidence_ids"), sourceIds, findings,
                    prefix + ".evidence_ids", true);
            references(interpretation.containsKey("contradictory_evidence_ids")
                            ? interpretation.get("contradictory_evidence_ids") : Collections.emptyList(),
                    sourceIds, findings,
                    prefix + ".contradictory_evidence_ids", false);
            if (!"APPROVED".equals(string(interpretation.get("reviewer_status")))) {
                add(findings, "INTERPRETATION_REQUIRES_REVIEW", prefix + ".reviewer_status");
            }
        }

        // QEI is a reviewed index, not a clinical conclusion. Every key evidence
        // entry must resolve to the original Source ID rather than a report page.
        List<?> qei = list(evidencePackage, "qei", findings);
        if (qei.isEmpty()) {
            add(findings, "QEI_INDEX_EMPTY", "qei");
        }
        Set<String> qeiIds = new HashSet<>();
        for (int i = 0; i < qei.size(); i++) {
            String prefix = "qei[" + i + "]";
            Map<String, Object> entry = item(qei.get(i), findings, prefix);
            String qeiId = string(entry.get("qei_id"));
            if (!SOURCE_ID.matcher(qeiId).matches()) {
                add(findings, "QEI_ID_INVALID", prefix + ".qei_id");
            } else if (!qeiIds.add(qeiId)) {
                add(findings, "QEI_ID_DUPLICATED", prefix + ".qei_id");
            }
            references(entry.get("evidence_ids"), sourceIds, findings, prefix + ".evidence_ids", true);
            references(entry.containsKey("counter_evidence_ids")
                            ? entry.get("counter_evidence_ids") : Collections.emptyList(),
                    sourceIds, findings,
                    prefix + ".counter_evidence_ids", false);
            if (!"APPROVED".equals(string(entry.get("reviewer_status")))) {
                add(findings, "QEI_REVIEW_PENDING", prefix + ".reviewer_status");
            }
        }

        Map<String, Object> safety = object(evidencePackage, "safety", findings, "safety");
        String redFlags = string(safety.get("red_flag_screen_status"));
        String symptoms = string(safety.get("symptom_screen_status"));
        String trainingDecision = string(safety.get("training_decision"));
        if (!Arrays.asList("SCREENED_NEGATIVE", "SCREENED_POSITIVE", "NOT_DOCUMENTED").contains(redFlags)) {
            add(findings, "RED_FLAG_SCREEN_INVALID", "safety.red_flag_screen_status");
        }
        if (!"DOCUMENTED".equals(symptoms)) {
            add(findings, "SYMPTOMS_NOT_DOCUMENTED", "safety.symptom_screen_status");
        }
        if (!TRAINING_DECISIONS.contains(trainingDecision)) {
            add(findings, "TRAINING_DECISION_INVALID", "safety.training_decision");
        }
        if (("SCREENED_POSITIVE".equals(redFlags) || "NOT_DOCUMENTED".equals(redFlags)
                || !"DOCUMENTED".equals(symptoms))
                && "CONDITIONAL_TRAINING".equals(trainingDecision)) {
            add(findings, "TRAINING_WITH_UNRESOLVED_SAFETY", "safety.training_decision");
        }
        references(safety.get("evidence_ids"), sourceIds, findings, "safety.evidence_ids", true);

        Map<String, Object> risks = object(evidencePackage, "risks", findings, "risks");
        for (String domain : RISK_DOMAINS) {
            String field = "risks." + domain;
            Map<String, Object> risk = item(risks.get(domain), findings, field);
            if (!"ASSESSED".equals(string(risk.get("status")))) {
                add(findings, "RISK_DOMAIN_UNREVIEWED", field + ".status");
            }
            references(risk.get("evidence_ids"), sourceIds, findings, field + ".evidence_ids", true);
        }

        List<?> kpis = list(evidencePackage, "kpis", findings);
        if (kpis.isEmpty()) {
            add(findings, "KPI_PLAN_EMPTY", "kpis");
        }
        for (int i = 0; i < kpis.size(); i++) {
            String field = "kpis[" + i + "]";
            Map<String, Object> kpi = item(kpis.get(i), findings, field);
            if (blank(kpi.get("task")) || blank(kpi.get("method")) || blank(kpi.get("unit"))) {
                add(findings, "KPI_MEASUREMENT_PROTOCOL_MISSING", field);
            }
            if (!numeric(kpi.get("baseline"))) {
                if (kpi.get("baseline") != null) {
                    add(findings, "KPI_BASELINE_INVALID", field + ".baseline");
                }
                if (blank(kpi.get("reason_if_unavailable"))) {
                    add(findings, "KPI_BASELINE_MISSING_REASON", field + ".reason_if_unavailable");
                }
            } else {
                reference(kpi.get("source_id"), sourceIds, findings, field + ".source_id");
            }
            if (kpi.get("target") != null && !numeric(kpi.get("target"))) {
                add(findings, "KPI_TARGET_INVALID", field + ".target");
            }
            if (!date(kpi.get("next_review"))) {
                add(findings, "KPI_NEXT_REVIEW_DATE_INVALID", field + ".next_review");
            } else if (date(assessment.get("date"))) {
                LocalDate assessedOn = LocalDate.parse(string(assessment.get("date")));
                LocalDate nextReview = LocalDate.parse(string(kpi.get("next_review")));
                if (nextReview.isBefore(assessedOn.plusWeeks(4))
                        || nextReview.isAfter(assessedOn.plusWeeks(6))) {
                    add(findings, "KPI_REVIEW_OUTSIDE_4_6_WEEKS", field + ".next_review");
                }
            }
        }

        Map<String, Object> review = object(evidencePackage, "review", findings, "review");
        if (!revision.equals(string(review.get("source_revision")))) {
            add(findings, "SOURCE_REVISION_MISMATCH", "review.source_revision");
        }
        if (!"APPROVED".equals(string(review.get("qa_status"))) || blank(review.get("reviewer"))) {
            add(findings, "QUALITY_REVIEW_NOT_APPROVED", "review");
        }
        if (!blank(review.get("signature"))) {
            if (!SHA256.matcher(string(review.get("immutable_output_hash"))).matches()) {
                add(findings, "SIGNED_OUTPUT_HASH_INVALID", "review.immutable_output_hash");
            }
            if (blank(review.get("report_revision"))) {
                add(findings, "SIGNED_REPORT_REVISION_MISSING", "review.report_revision");
            }
        }
        return result(findings, sourceIds.size());
    }

    private static Map<String, Object> result(List<Map<String, String>> findings, int sourceCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", SCHEMA);
        result.put("sourceCount", sourceCount);
        result.put("structuralValid", findings.isEmpty());
        result.put("clinicalReady", false);
        result.put("signatureAuthorized", false);
        result.put("findings", findings);
        result.put("notice", "仅核对证据包结构，不代表治疗师临床审核、签署、家长版发布或报告生成器验收");
        return result;
    }

    private static void reference(Object value, Set<String> sourceIds, List<Map<String, String>> findings, String field) {
        if (!sourceIds.contains(string(value))) {
            add(findings, "EVIDENCE_SOURCE_UNRESOLVED", field);
        }
    }

    private static void references(Object value, Set<String> ids, List<Map<String, String>> findings,
                                   String field, boolean required) {
        // Callers supply an empty list for *absent* optional fields. A present
        // null must be rejected, matching the JSON Schema array type.
        if (!(value instanceof List<?>)) {
            add(findings, "EVIDENCE_REFS_INVALID", field);
            return;
        }
        List<?> refs = (List<?>) value;
        if (refs.size() > MAX_REFERENCE_IDS) {
            add(findings, "EVIDENCE_REFS_TOO_MANY", field);
            return;
        }
        if (required && refs.isEmpty()) {
            add(findings, "EVIDENCE_REFS_EMPTY", field);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < refs.size(); i++) {
            String id = string(refs.get(i));
            if (!ids.contains(id)) {
                add(findings, "EVIDENCE_SOURCE_UNRESOLVED", field + "[" + i + "]");
            } else if (!seen.add(id)) {
                add(findings, "EVIDENCE_SOURCE_DUPLICATED", field + "[" + i + "]");
            }
        }
    }

    private static Map<String, Object> object(Map<String, Object> parent, String key,
                                               List<Map<String, String>> findings, String field) {
        Object value = parent.get(key);
        return item(value, findings, field);
    }

    private static Map<String, Object> item(Object value, List<Map<String, String>> findings, String field) {
        if (!(value instanceof Map<?, ?>)) {
            add(findings, "EVIDENCE_OBJECT_INVALID", field);
            return Collections.emptyMap();
        }
        Map<?, ?> source = (Map<?, ?>) value;
        if (source.size() > MAX_OBJECT_FIELDS) {
            add(findings, "EVIDENCE_OBJECT_TOO_WIDE", field);
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, val) -> {
            if (key instanceof String) {
                result.put((String) key, val);
            }
        });
        return result;
    }

    private static List<?> list(Map<String, Object> parent, String field,
                                List<Map<String, String>> findings) {
        Object value = parent.get(field);
        if (!(value instanceof List<?>)) {
            add(findings, "EVIDENCE_LIST_INVALID", field);
            return Collections.emptyList();
        }
        List<?> entries = (List<?>) value;
        if (entries.size() > MAX_REGISTER_RECORDS) {
            add(findings, "EVIDENCE_LIST_TOO_LARGE", field);
            return Collections.emptyList();
        }
        return entries;
    }

    private static boolean numeric(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        String text = string(value);
        if (text.isEmpty() || text.length() > 60) {
            return false;
        }
        try {
            BigDecimal number = new BigDecimal(text);
            return number.scale() <= 20 && number.scale() >= -12 && number.precision() <= 18;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean timestamp(Object value) {
        try {
            // An audit timestamp must carry an explicit UTC offset.
            OffsetDateTime.parse(string(value));
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean date(Object value) {
        String text = string(value);
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            return LocalDate.parse(text).toString().equals(text);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean blank(Object value) {
        return string(value).isEmpty();
    }

    private static void add(List<Map<String, String>> findings, String code, String field) {
        Map<String, String> finding = new LinkedHashMap<>();
        finding.put("code", code);
        finding.put("severity", "blocker");
        finding.put("field", field);
        findings.add(finding);
    }
}
