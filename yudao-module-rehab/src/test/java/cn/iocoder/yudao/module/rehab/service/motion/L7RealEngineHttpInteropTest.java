package cn.iocoder.yudao.module.rehab.service.motion;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parses an ACTUAL HTTP 200 result obtained from the real Python engine using
 * a disposable, generated OpenCap-like ZIP. Never accesses a real patient.
 * Explicit opt-in; the evidence JSON lives outside the source checkout.
 */
class L7RealEngineHttpInteropTest {
    @Test
    @SuppressWarnings("unchecked")
    void realHttpEngineResponseMapsToReviewableBackendRows() throws Exception {
        String file = System.getenv("REHAB_L7_SYNTHETIC_ENGINE_HTTP_RESULT");
        assumeTrue(file != null && !file.isEmpty(), "real synthetic engine HTTP evidence not supplied");
        Path source = Paths.get(file).toRealPath();
        assertTrue(source.toString().contains("/validation-evidence/2026-10-10/l7-integration-tests/"),
                "only private isolated evidence accepted");
        assertTrue(Files.size(source) > 100 && Files.size(source) < 2_000_000);
        String json = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertFalse(json.contains("patient-zhang-san"), "synthetic source identity must be redacted");
        assertFalse(json.contains("someone@example.com"), "synthetic email must be redacted");
        Map<String, Object> response = JsonUtils.parseObject(json, Map.class);
        assertEquals("motion-result/1.0.0", response.get("schema_version"));
        MotionResultMapper.Mapped mapped = MotionResultMapper.map(response, 991100L, 1,
                Collections.singletonMap("ds1", 991101L));
        assertEquals("pass", mapped.getSessionQualityStatus());
        assertEquals("motion-result/1.0.0", mapped.getSchemaVersion());
        assertNotNull(mapped.getTrialUpdates().get("ds1"));
        assertEquals(Long.valueOf(991101L), mapped.getTrialUpdates().get("ds1").getId());
        assertEquals("pass", mapped.getTrialUpdates().get("ds1").getQcStatus());
        assertFalse(mapped.getMetrics().isEmpty(), "HTTP engine output must contain actual computed metrics");
        assertFalse(mapped.getScores().isEmpty(), "reviewable score rows must exist");
        mapped.getScores().forEach(score -> assertNull(score.getFinalScore(),
                "engine cannot bypass therapist approval by setting final score"));
        mapped.getMetrics().forEach(metric -> assertEquals(Integer.valueOf(1), metric.getAnalysisRevision()));
    }
}
