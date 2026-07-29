package cn.iocoder.yudao.module.rehab.service.assessment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RehabStaticAssessmentSummaryBuilderTest {

    private RehabStaticAssessmentSummaryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RehabStaticAssessmentSummaryBuilder();
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithSummary_shouldBuildAbnormalSummary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("posterior_view", mapOf(
                "left", mapOf("ear_height", "偏上"),
                "right", mapOf("ear_height", "正常"),
                "midline", mapOf("neck_rotation", "正常")
        ));

        Map<String, Object> result = builder.enrichWithSummary(payload);
        assertNotNull(result.get("static_summary"));

        Map<String, Object> summary = (Map<String, Object>) result.get("static_summary");
        Map<String, Object> posterior = (Map<String, Object>) summary.get("posterior_view_summary");
        List<Map<String, Object>> abnormalItems = (List<Map<String, Object>>) posterior.get("abnormal_items");
        assertFalse(abnormalItems.isEmpty());
        assertEquals("耳朵高度（左）", abnormalItems.get(0).get("field_label"));
        assertEquals("偏上", abnormalItems.get(0).get("value"));
        assertTrue(String.valueOf(posterior.get("summary_text")).contains("后面观"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithSummary_shouldMarkManualReviewWhenConflictDetected() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("posterior_view", mapOf(
                "left", mapOf("neck_rotation", "右旋"),
                "right", mapOf("neck_rotation", "右旋"),
                "midline", mapOf("neck_rotation", "左旋")
        ));

        Map<String, Object> result = builder.enrichWithSummary(payload);
        Map<String, Object> summary = (Map<String, Object>) result.get("static_summary");
        Map<String, Object> overall = (Map<String, Object>) summary.get("overall_summary");

        assertEquals(Boolean.TRUE, overall.get("needs_manual_review"));
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) overall.get("conflicts");
        assertFalse(conflicts.isEmpty());
        assertTrue(String.valueOf(overall.get("summary_text")).contains("需人工复核"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithSummary_shouldHandleEmptyPayload() {
        Map<String, Object> result = builder.enrichWithSummary(null);
        Map<String, Object> summary = (Map<String, Object>) result.get("static_summary");

        Map<String, Object> posterior = (Map<String, Object>) summary.get("posterior_view_summary");
        Map<String, Object> lateral = (Map<String, Object>) summary.get("lateral_view_summary");
        Map<String, Object> anterior = (Map<String, Object>) summary.get("anterior_view_summary");

        assertEquals("该视角暂未录入完整评估结果", posterior.get("summary_text"));
        assertEquals("该视角暂未录入完整评估结果", lateral.get("summary_text"));
        assertEquals("该视角暂未录入完整评估结果", anterior.get("summary_text"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichWithFallback_shouldReturnConservativeSummary() {
        Map<String, Object> result = builder.enrichWithFallback(mapOf("a", "b"), "mock-error");
        Map<String, Object> summary = (Map<String, Object>) result.get("static_summary");
        Map<String, Object> overall = (Map<String, Object>) summary.get("overall_summary");

        assertEquals(Boolean.TRUE, overall.get("needs_manual_review"));
        assertEquals("mock-error", overall.get("fallback_reason"));
        assertTrue(String.valueOf(overall.get("summary_text")).contains("需人工复核"));
    }

    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return map;
    }
}
