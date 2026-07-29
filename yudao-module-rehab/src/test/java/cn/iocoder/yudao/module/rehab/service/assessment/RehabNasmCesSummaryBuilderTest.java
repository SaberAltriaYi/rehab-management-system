package cn.iocoder.yudao.module.rehab.service.assessment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RehabNasmCesSummaryBuilderTest {

    private RehabNasmCesSummaryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RehabNasmCesSummaryBuilder();
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithSummary_shouldBuildCesSummaryAndActionSummaries() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transition_assessments", mapOf(
                "push_up", mapOf(
                        "full_view", mapOf(
                                "lphc", mapOf(
                                        "lumbar_sag", mapOf("present", true, "note", "明显")
                                )
                        )
                )
        ));
        payload.put("dynamic_assessments", mapOf(
                "gait_analysis", mapOf(
                        "flat_foot", mapOf("present", true, "left", true, "right", false, "note", "左侧更明显")
                )
        ));
        payload.put("upper_extremity_davies_test", mapOf(
                "trials", Arrays.asList(
                        mapOf("trial_no", 1, "repetition_count", 18, "duration_sec", 15),
                        mapOf("trial_no", 2, "repetition_count", 20, "duration_sec", 15)
                )
        ));
        payload.put("less_test", mapOf(
                "less_total_score", 6,
                "items", mapOf(
                        "overall_impression", mapOf("value", "糟糕的"),
                        "foot_external_rotation_gt_30", mapOf("value", "是")
                )
        ));

        Map<String, Object> result = builder.enrichWithSummary(payload);
        assertTrue(result.containsKey("ces_summary"));
        assertTrue(result.containsKey("action_summaries"));
        assertTrue(result.containsKey("risk_precheck"));
        assertTrue(result.containsKey("report_mapping"));

        Map<String, Object> cesSummary = (Map<String, Object>) result.get("ces_summary");
        Map<String, Object> transition = (Map<String, Object>) cesSummary.get("transition_assessments_summary");
        Map<String, Object> dynamic = (Map<String, Object>) cesSummary.get("dynamic_assessments_summary");
        Map<String, Object> less = (Map<String, Object>) cesSummary.get("less_test_summary");
        Map<String, Object> overall = (Map<String, Object>) cesSummary.get("overall_summary");
        List<Map<String, Object>> actionSummaries = (List<Map<String, Object>>) cesSummary.get("action_summaries");

        assertTrue(((Integer) transition.get("completed_items")) >= 1);
        assertTrue(((Integer) dynamic.get("completed_items")) >= 1);
        assertEquals(6, less.get("less_total_score"));
        assertFalse(((List<?>) overall.get("key_findings")).isEmpty());
        assertEquals(11, actionSummaries.size());
        assertNotNull(((Map<String, Object>) result.get("risk_precheck")).get("overall_risk_level"));
        assertTrue(((Map<String, Object>) result.get("report_mapping")).containsKey("nasm_ces_action_blocks"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithSummary_shouldHandlePartialOrEmptyData() {
        Map<String, Object> result = builder.enrichWithSummary(new LinkedHashMap<>());
        Map<String, Object> cesSummary = (Map<String, Object>) result.get("ces_summary");
        Map<String, Object> transition = (Map<String, Object>) cesSummary.get("transition_assessments_summary");
        Map<String, Object> overall = (Map<String, Object>) cesSummary.get("overall_summary");
        List<Map<String, Object>> actionSummaries = (List<Map<String, Object>>) cesSummary.get("action_summaries");

        assertEquals(0, transition.get("completed_items"));
        assertTrue(String.valueOf(overall.get("summary_text")).contains("证据不足"));
        assertEquals(11, actionSummaries.size());
        assertTrue(actionSummaries.stream().allMatch(item ->
                "not_assessed".equals(item.get("status")) || "partial".equals(item.get("status"))));
        assertTrue(((Map<String, Object>) result.get("risk_precheck")).containsKey("overall_risk_level"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithFallback_shouldReturnConservativeSummary() {
        Map<String, Object> result = builder.enrichWithFallback(mapOf("a", "b"), "mock-error");
        Map<String, Object> cesSummary = (Map<String, Object>) result.get("ces_summary");
        Map<String, Object> overall = (Map<String, Object>) cesSummary.get("overall_summary");
        Map<String, Object> riskPrecheck = (Map<String, Object>) result.get("risk_precheck");
        Map<String, Object> reportMapping = (Map<String, Object>) result.get("report_mapping");

        assertEquals("mock-error", overall.get("fallback_reason"));
        assertTrue(String.valueOf(overall.get("summary_text")).contains("需结合人工复核"));
        assertTrue(((List<?>) result.get("action_summaries")).isEmpty());
        assertEquals("low", riskPrecheck.get("overall_risk_level"));
        assertTrue(reportMapping.containsKey("nasm_ces_action_blocks"));
    }

    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return map;
    }
}
