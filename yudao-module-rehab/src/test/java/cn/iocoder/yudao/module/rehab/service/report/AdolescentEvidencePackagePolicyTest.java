package cn.iocoder.yudao.module.rehab.service.report;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdolescentEvidencePackagePolicyTest {

    private static Map<String, Object> map(Object... items) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i += 2) {
            result.put((String) items[i], items[i + 1]);
        }
        return result;
    }

    private static Map<String, Object> syntheticPackage() {
        return map(
                "schema", AdolescentEvidencePackagePolicy.SCHEMA,
                "case_ref", "SYNTHETIC-CASE-001",
                "assessment", map("id", "ASSESSMENT-SYN-001", "date", "2026-10-08", "locked_revision", "3"),
                "sources", new ArrayList<>(Collections.singletonList(
                        map("source_id", "SYN-YBT-001", "type", "RAW_DATA", "capture_date", "2026-10-08",
                                "method", "synthetic YBT fixture", "permission_scope", "THERAPIST_ONLY"))),
                "measurements", new ArrayList<>(Collections.singletonList(
                        map("source_id", "SYN-YBT-001", "task", "YBT_UQ", "value", 52.5,
                                "unit", "cm", "validity", "VALID"))),
                "observations", new ArrayList<>(Collections.singletonList(
                        map("task", "YBT_UQ", "evidence_ids", Collections.singletonList("SYN-YBT-001")))),
                "conflicts", new ArrayList<>(),
                "interpretations", new ArrayList<>(Collections.singletonList(
                        map("evidence_ids", Collections.singletonList("SYN-YBT-001"),
                                "contradictory_evidence_ids", Collections.emptyList(),
                                "reviewer_status", "APPROVED"))),
                "qei", new ArrayList<>(Collections.singletonList(
                        map("qei_id", "QEI-SYN-01", "evidence_ids", Collections.singletonList("SYN-YBT-001"),
                                "counter_evidence_ids", Collections.emptyList(), "reviewer_status", "APPROVED"))),
                "safety", map("red_flag_screen_status", "SCREENED_NEGATIVE", "symptom_screen_status",
                        "DOCUMENTED", "training_decision", "PENDING_REVIEW",
                        "evidence_ids", Collections.singletonList("SYN-YBT-001")),
                "risks", map(
                        "medical_safety", map("status", "ASSESSED", "evidence_ids",
                                Collections.singletonList("SYN-YBT-001")),
                        "symptom", map("status", "ASSESSED", "evidence_ids",
                                Collections.singletonList("SYN-YBT-001")),
                        "movement_control", map("status", "ASSESSED", "evidence_ids",
                                Collections.singletonList("SYN-YBT-001")),
                        "growth_and_measurement", map("status", "ASSESSED", "evidence_ids",
                                Collections.singletonList("SYN-YBT-001"))),
                "kpis", new ArrayList<>(Collections.singletonList(
                        map("task", "YBT_UQ", "baseline", 52.5, "unit", "cm", "method",
                                "synthetic repeatable protocol", "source_id", "SYN-YBT-001",
                                "next_review", "2026-11-12"))),
                "review", map("qa_status", "APPROVED", "reviewer", "synthetic-reviewer", "source_revision", "3"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(Map<String, Object> result) {
        return ((List<Map<String, String>>) result.get("findings")).stream()
                .map(item -> item.get("code")).collect(Collectors.toList());
    }

    @Test
    void completeStructuralPackageStillCannotSelfAuthorizeClinicalSignature() {
        Map<String, Object> result = AdolescentEvidencePackagePolicy.audit(syntheticPackage());
        assertEquals(1, result.get("sourceCount"));
        assertEquals(true, result.get("structuralValid"));
        assertEquals(false, result.get("clinicalReady"));
        assertEquals(false, result.get("signatureAuthorized"));
        assertTrue(codes(result).isEmpty());
    }

    @Test
    void absentPackageNeverPassesQualityOrLeaksInputs() {
        Map<String, Object> result = AdolescentEvidencePackagePolicy.audit(null);
        assertFalse((Boolean) result.get("structuralValid"));
        assertTrue(codes(result).contains("EVIDENCE_PACKAGE_MISSING"));
    }

    @Test
    void unknownSchemaVersionCannotBeInterpretedAsCurrent() {
        Map<String, Object> pack = syntheticPackage();
        pack.put("schema", "FUTURE-EVIDENCE-SCHEMA");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_SCHEMA_UNSUPPORTED"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void caseAndAssessmentReferencesMustMatchSchemaStringAndLengthRules() {
        Map<String, Object> pack = syntheticPackage();
        pack.put("case_ref", 123456);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("CASE_REFERENCE_INVALID"));
        pack.put("case_ref", "A".repeat(65));
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("CASE_REFERENCE_INVALID"));
        pack.put("case_ref", "A".repeat(64));
        assertTrue((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("structuralValid"));

        ((Map<String, Object>) pack.get("assessment")).put("id", 123456);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("ASSESSMENT_REFERENCE_INVALID"));
        assertFalse((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("signatureAuthorized"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void duplicateSourceAndUnknownReferencesAreBlocked() {
        Map<String, Object> pack = syntheticPackage();
        ((List<Object>) pack.get("sources")).add(map("source_id", "SYN-YBT-001", "type", "RAW_DATA",
                "capture_date", "2026-10-08", "method", "synthetic", "permission_scope", "THERAPIST_ONLY"));
        ((Map<String, Object>) ((List<?>) pack.get("measurements")).get(0)).put("source_id", "MISSING");
        Map<String, Object> result = AdolescentEvidencePackagePolicy.audit(pack);
        assertTrue(codes(result).containsAll(Arrays.asList("SOURCE_ID_DUPLICATED", "EVIDENCE_SOURCE_UNRESOLVED")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void failedYbtIsDifferentFromValidZeroAndFromUnmeasured() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> measure = (Map<String, Object>) ((List<?>) pack.get("measurements")).get(0);
        measure.put("value", 0);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("YBT_DISTANCE_NOT_VALID"));
        measure.put("value", "52.5");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("VALID_MEASUREMENT_VALUE_INVALID"));

        measure.put("validity", "FAILED");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("INVALID_MEASUREMENT_HAS_VALUE"));
        measure.put("value", null);
        measure.put("missing_reason", "synthetic invalid attempt");
        assertTrue(AdolescentEvidencePackagePolicy.audit(pack).get("structuralValid").equals(true));

        measure.put("validity", "NOT_TESTED");
        measure.remove("missing_reason");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("MEASUREMENT_REASON_MISSING"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sfmaPainCodeConflictMustBeEscalatedWithoutChangingScore() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> observation = (Map<String, Object>) ((List<?>) pack.get("observations")).get(0);
        observation.put("task", "SFMA");
        observation.put("score", "FN");
        observation.put("pain", 3);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("SFMA_PAIN_CODE_CONFLICT"));
        assertEquals("FN", observation.get("score"));
        observation.put("score", "DP");
        observation.put("pain", 0);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("SFMA_PAIN_CODE_CONFLICT"));
        observation.put("pain", null);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("SFMA_PAIN_NOT_DOCUMENTED"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reviewCannotAcceptUnresolvedOrUnauditedConflict() {
        Map<String, Object> pack = syntheticPackage();
        ((List<Object>) pack.get("conflicts")).add(map(
                "code", "SYNTHETIC_MISMATCH", "conflicting_sources", Collections.singletonList("SYN-YBT-001"),
                "state", "OPEN"));
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("CONFLICT_UNRESOLVED"));
        Map<String, Object> conflict = (Map<String, Object>) ((List<?>) pack.get("conflicts")).get(0);
        conflict.put("state", "RESOLVED");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("CONFLICT_RESOLUTION_AUDIT_MISSING"));
        conflict.put("resolution", "synthetic clinician confirmation");
        conflict.put("resolved_by", "synthetic-reviewer");
        conflict.put("timestamp", "2026-10-08T11:00:00");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("CONFLICT_RESOLUTION_AUDIT_MISSING"));
        conflict.put("timestamp", "2026-10-08T11:00:00+08:00");
        assertTrue((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("structuralValid"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void changingLockedRevisionInvalidatesReviewAndUnapprovedInterpretations() {
        Map<String, Object> pack = syntheticPackage();
        ((Map<String, Object>) pack.get("assessment")).put("locked_revision", "4");
        ((Map<String, Object>) ((List<?>) pack.get("interpretations")).get(0)).put("reviewer_status", "DRAFT");
        List<String> issues = codes(AdolescentEvidencePackagePolicy.audit(pack));
        assertTrue(issues.contains("SOURCE_REVISION_MISMATCH"));
        assertTrue(issues.contains("INTERPRETATION_REQUIRES_REVIEW"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void malformedSourceDateAndChecksumNeverCountAsProvenance() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> source = (Map<String, Object>) ((List<?>) pack.get("sources")).get(0);
        source.put("capture_date", "2026-02-30");
        source.put("file_hash", "not-a-sha256");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).containsAll(
                Arrays.asList("SOURCE_CAPTURE_DATE_INVALID", "SOURCE_FILE_HASH_INVALID")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void qeiMustReferenceKnownEvidenceAndHaveReviewedUniqueId() {
        Map<String, Object> pack = syntheticPackage();
        List<Object> qei = (List<Object>) pack.get("qei");
        Map<String, Object> entry = (Map<String, Object>) qei.get(0);
        entry.put("evidence_ids", Collections.singletonList("MISSING-SOURCE"));
        entry.put("reviewer_status", "DRAFT");
        qei.add(map("qei_id", "QEI-SYN-01", "evidence_ids", Collections.singletonList("SYN-YBT-001"),
                "counter_evidence_ids", Collections.emptyList(), "reviewer_status", "APPROVED"));
        List<String> issues = codes(AdolescentEvidencePackagePolicy.audit(pack));
        assertTrue(issues.contains("EVIDENCE_SOURCE_UNRESOLVED"));
        assertTrue(issues.contains("QEI_REVIEW_PENDING"));
        assertTrue(issues.contains("QEI_ID_DUPLICATED"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownRedFlagsOrSymptomsMustNeverAuthorizeConditionalTraining() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> safety = (Map<String, Object>) pack.get("safety");
        safety.put("red_flag_screen_status", "NOT_DOCUMENTED");
        safety.put("symptom_screen_status", "NOT_DOCUMENTED");
        safety.put("training_decision", "CONDITIONAL_TRAINING");
        List<String> issues = codes(AdolescentEvidencePackagePolicy.audit(pack));
        assertTrue(issues.contains("SYMPTOMS_NOT_DOCUMENTED"));
        assertTrue(issues.contains("TRAINING_WITH_UNRESOLVED_SAFETY"));
        assertFalse((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("signatureAuthorized"));
        safety.put("red_flag_screen_status", "SCREENED_POSITIVE");
        safety.put("symptom_screen_status", "DOCUMENTED");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("TRAINING_WITH_UNRESOLVED_SAFETY"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyRiskDomainRequiresReviewedEvidenceNotAnAssumedNormal() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> risks = (Map<String, Object>) pack.get("risks");
        Map<String, Object> growth = (Map<String, Object>) risks.get("growth_and_measurement");
        growth.put("status", "UNKNOWN");
        growth.put("evidence_ids", Collections.emptyList());
        List<String> issues = codes(AdolescentEvidencePackagePolicy.audit(pack));
        assertTrue(issues.contains("RISK_DOMAIN_UNREVIEWED"));
        assertTrue(issues.contains("EVIDENCE_REFS_EMPTY"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void kpiMustBeReproducibleAndHaveFourToSixWeekReview() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> kpi = (Map<String, Object>) ((List<?>) pack.get("kpis")).get(0);
        kpi.put("next_review", "2026-10-10");
        kpi.put("source_id", "UNKNOWN");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("KPI_REVIEW_OUTSIDE_4_6_WEEKS"));
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_SOURCE_UNRESOLVED"));
        kpi.put("baseline", null);
        kpi.remove("reason_if_unavailable");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("KPI_BASELINE_MISSING_REASON"));
        kpi.put("baseline", "55.0");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("KPI_BASELINE_INVALID"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void oversizedRegisterAndEvidenceReferencesAreRejectedBeforeIteration() {
        Map<String, Object> pack = syntheticPackage();
        pack.put("sources", new ArrayList<>(Collections.nCopies(513, "synthetic-invalid-source")));
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_LIST_TOO_LARGE"));

        pack = syntheticPackage();
        Map<String, Object> qei = (Map<String, Object>) ((List<?>) pack.get("qei")).get(0);
        qei.put("evidence_ids", new ArrayList<>(Collections.nCopies(65, "SYN-YBT-001")));
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_REFS_TOO_MANY"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void oversizedRecordOrRootCannotPassStructuralGate() {
        Map<String, Object> pack = syntheticPackage();
        Map<String, Object> source = (Map<String, Object>) ((List<?>) pack.get("sources")).get(0);
        for (int i = 0; i < 65; i++) {
            source.put("synthetic_extra_" + i, "fixture");
        }
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_OBJECT_TOO_WIDE"));
        pack = syntheticPackage();
        for (int i = 0; i < 33; i++) {
            pack.put("synthetic_root_extra_" + i, "fixture");
        }
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_PACKAGE_TOO_WIDE"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void omittedOptionalContraryEvidenceIsAllowedButRequiredReferencesAreNot() {
        Map<String, Object> pack = syntheticPackage();
        ((Map<String, Object>) ((List<?>) pack.get("interpretations")).get(0))
                .remove("contradictory_evidence_ids");
        ((Map<String, Object>) ((List<?>) pack.get("qei")).get(0))
                .remove("counter_evidence_ids");
        assertTrue((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("structuralValid"));

        ((Map<String, Object>) ((List<?>) pack.get("interpretations")).get(0))
                .put("contradictory_evidence_ids", null);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_REFS_INVALID"));
        ((Map<String, Object>) ((List<?>) pack.get("interpretations")).get(0))
                .remove("contradictory_evidence_ids");
        ((Map<String, Object>) ((List<?>) pack.get("qei")).get(0))
                .put("counter_evidence_ids", null);
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_REFS_INVALID"));
        ((Map<String, Object>) ((List<?>) pack.get("qei")).get(0))
                .remove("counter_evidence_ids");
        ((Map<String, Object>) ((List<?>) pack.get("qei")).get(0)).remove("evidence_ids");
        assertTrue(codes(AdolescentEvidencePackagePolicy.audit(pack)).contains("EVIDENCE_REFS_INVALID"));
        assertFalse((Boolean) AdolescentEvidencePackagePolicy.audit(pack).get("signatureAuthorized"));
    }
}
