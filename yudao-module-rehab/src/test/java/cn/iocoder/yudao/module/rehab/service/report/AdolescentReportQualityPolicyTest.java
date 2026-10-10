package cn.iocoder.yudao.module.rehab.service.report;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdolescentReportQualityPolicyTest {

    @SuppressWarnings("unchecked")
    @Test
    void unknownSafetyAndGrowthMustNotBePresentedAsNormal() {
        Map<String, Object> result = AdolescentReportQualityPolicy.audit(
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        assertEquals("SOURCE_REVIEW_REQUIRED", result.get("status"));
        assertEquals(false, result.get("clinicalReady"));
        assertEquals(false, result.get("provenanceVerified"));
        List<Map<String, String>> findings = (List<Map<String, String>>) result.get("findings");
        List<String> codes = findings.stream().map(f -> f.get("code")).collect(Collectors.toList());
        assertTrue(codes.contains("GROWTH_BIRTH_DATE_MISSING"));
        assertTrue(codes.contains("RED_FLAG_SCREEN_NOT_DOCUMENTED"));
        assertTrue(codes.contains("SOURCE_REGISTER_NEEDS_REVIEW"));
        assertTrue(codes.contains("CLINICAL_SYNTHESIS_NOT_GENERATED"));
        assertTrue(findings.stream().anyMatch(f -> "blocker".equals(f.get("severity"))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void filledBasicFieldsRemoveCorrespondingWarningsButNotClinicalReviewRequirement() {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("birthday", "2012-03-04");
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("assessmentDate", "2026-10-08");
        assessment.put("redFlagNotes", "合成示例：已做红旗核查，未见需进一步评估的信号");
        assessment.put("sourceSummary", "合成示例：FMS-001 2026-10-08");
        Map<String, Object> result = AdolescentReportQualityPolicy.audit(
                patient, assessment, Collections.singletonMap("fms", new Object()));
        List<Map<String, String>> findings = (List<Map<String, String>>) result.get("findings");
        assertEquals(1, findings.size());
        assertEquals("CLINICAL_SYNTHESIS_NOT_GENERATED", findings.get(0).get("code"));
        assertFalse((Boolean) result.get("clinicalReady"));
    }
}
