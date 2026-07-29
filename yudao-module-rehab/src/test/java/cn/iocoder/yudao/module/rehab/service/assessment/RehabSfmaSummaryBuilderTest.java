package cn.iocoder.yudao.module.rehab.service.assessment;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RehabSfmaSummaryBuilderTest {

    private final RehabSfmaSummaryBuilder builder = new RehabSfmaSummaryBuilder();

    @Test
    void enrichWithSummary_shouldGenerateRecommendationSummaryRiskAndMapping() {
        Map<String, Object> topTier = new HashMap<>();
        topTier.put("cervical_flexion", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        topTier.put("single_leg_stance_left", new HashMap<String, Object>() {{
            put("classification", "FP");
            put("pain_present", false);
        }});

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        assertNotNull(enriched.get("breakout_recommendations"));
        assertNotNull(enriched.get("summary"));
        assertNotNull(enriched.get("risk_precheck"));
        assertNotNull(enriched.get("report_mapping"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        assertTrue(summary.containsKey("primary_classification"));
        assertTrue(summary.containsKey("top_tier_table"));

        Map<String, Object> riskPrecheck = (Map<String, Object>) enriched.get("risk_precheck");
        assertEquals(true, riskPrecheck.get("internal_rule_only"));
        assertEquals(true, riskPrecheck.get("provisional_v1"));
    }

    @Test
    void enrichWithSummary_shouldWriteSfmaRootAndLegacyMirror() {
        Map<String, Object> nestedTopTier = new HashMap<>();
        nestedTopTier.put("cervical_flexion", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});

        Map<String, Object> nestedSfma = new HashMap<>();
        nestedSfma.put("top_tier", nestedTopTier);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sfma", nestedSfma);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);

        assertTrue(enriched.containsKey("sfma"));
        Map<String, Object> sfma = (Map<String, Object>) enriched.get("sfma");
        assertNotNull(sfma.get("top_tier"));
        assertNotNull(sfma.get("summary"));
        assertNotNull(sfma.get("report_mapping"));

        // legacy mirror keys should still exist for old readers
        assertTrue(enriched.containsKey("top_tier"));
        assertTrue(enriched.containsKey("summary"));
        assertTrue(enriched.containsKey("report_mapping"));
    }

    @Test
    void enrichWithSummary_shouldNormalizeStoppedDueToPainToCompleted() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_flexion_top_tier", new HashMap<String, Object>() {{
            put("classification", "DP");
        }});
        payload.put("cervical_flexion_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "stopped_due_to_pain");
            put("breakout_summary_text", "疼痛中止");
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);

        Map<String, Object> sfma = (Map<String, Object>) enriched.get("sfma");
        Map<String, Object> normalizedBreakout = (Map<String, Object>) sfma.get("cervical_flexion_breakout");
        assertEquals("completed", normalizedBreakout.get("breakout_status"));
        assertEquals(true, normalizedBreakout.get("needs_manual_review"));

        Map<String, Object> summary = (Map<String, Object>) sfma.get("summary");
        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> cervicalBreakoutSummary = (Map<String, Object>) breakoutSummaryItem.get("cervical_flexion");
        assertEquals("completed", cervicalBreakoutSummary.get("breakout_status"));
    }

    @Test
    void enrichWithSummary_shouldAutoSetPainFieldsWhenFpOrDp() {
        Map<String, Object> topTier = new HashMap<>();
        topTier.put("multi_segmental_extension", new HashMap<String, Object>() {{
            put("classification", "DP");
            put("pain_present", false);
        }});

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> enrichedTopTier = (Map<String, Object>) enriched.get("top_tier");
        Map<String, Object> row = (Map<String, Object>) enrichedTopTier.get("multi_segmental_extension");
        assertEquals(true, row.get("pain_present"));
        assertEquals("high", row.get("review_priority"));
        assertEquals("优先疼痛管理/谨慎继续分解", row.get("caution_text"));
    }

    @Test
    void enrichWithSummary_shouldOrderRecommendationsByDnFpDpAndHierarchy() {
        Map<String, Object> topTier = new HashMap<>();
        String[] allTests = new String[]{
                "cervical_flexion",
                "cervical_extension",
                "cervical_rotation_left",
                "cervical_rotation_right",
                "upper_extremity_pattern1_left",
                "upper_extremity_pattern1_right",
                "upper_extremity_pattern2_left",
                "upper_extremity_pattern2_right",
                "multi_segmental_flexion",
                "multi_segmental_extension",
                "multi_segmental_rotation_left",
                "multi_segmental_rotation_right",
                "single_leg_stance_left",
                "single_leg_stance_right",
                "arms_down_deep_squat"
        };
        for (String testCode : allTests) {
            Map<String, Object> row = new HashMap<>();
            row.put("classification", "FN");
            topTier.put(testCode, row);
        }
        ((Map<String, Object>) topTier.get("multi_segmental_rotation_right")).put("classification", "DP");
        ((Map<String, Object>) topTier.get("upper_extremity_pattern1_left")).put("classification", "FP");
        ((Map<String, Object>) topTier.get("cervical_flexion")).put("classification", "DN");

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) enriched.get("breakout_recommendations");
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());

        Map<String, Object> first = recommendations.get(0);
        assertEquals("cervical_flexion", first.get("test_code"));
        assertEquals("dn_first", first.get("recommendation_stage"));
        assertTrue(((Number) first.get("recommendation_order")).intValue() < 200);

        boolean hasDpLast = recommendations.stream().anyMatch(item ->
                "multi_segmental_rotation_right".equals(item.get("test_code"))
                        && "dp_last".equals(item.get("recommendation_stage")));
        assertTrue(hasDpLast);
    }

    @Test
    void enrichWithFallback_shouldReturnConservativeStructure() {
        Map<String, Object> enriched = builder.enrichWithFallback(new HashMap<String, Object>(), "unit-test");
        assertNotNull(enriched.get("summary"));
        assertNotNull(enriched.get("risk_precheck"));
        assertNotNull(enriched.get("report_mapping"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        assertEquals("T", summary.get("primary_classification"));
        assertEquals(true, summary.get("needs_manual_review"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedCervicalTwoLayerStructure() {
        Map<String, Object> cervicalTopTier = new HashMap<>();
        cervicalTopTier.put("classification", "DN");
        cervicalTopTier.put("top_tier_note", "屈曲受限");

        Map<String, Object> cervicalBreakout = new HashMap<>();
        cervicalBreakout.put("breakout_status", "completed");
        cervicalBreakout.put("breakout_summary_text", "主动屈曲明显受限，伴代偿");
        cervicalBreakout.put("breakout_preliminary_direction", List.of("更偏活动度限制"));
        cervicalBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_flexion_top_tier", cervicalTopTier);
        payload.put("cervical_flexion_breakout", cervicalBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> topTier = (Map<String, Object>) enriched.get("top_tier");
        Map<String, Object> cervicalLegacyTopTier = (Map<String, Object>) topTier.get("cervical_flexion");
        assertEquals("DN", cervicalLegacyTopTier.get("classification"));
        assertEquals(true, cervicalLegacyTopTier.get("needs_breakout_suggestion"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> cervicalLegacyBreakout = (Map<String, Object>) breakouts.get("cervical_flexion_breakout");
        assertEquals("completed", cervicalLegacyBreakout.get("status"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> topTierSummaryItem = (Map<String, Object>) summary.get("top_tier_summary_item");
        Map<String, Object> cervicalTopTierSummary = (Map<String, Object>) topTierSummaryItem.get("cervical_flexion");
        assertEquals("DN", cervicalTopTierSummary.get("classification"));

        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> cervicalBreakoutSummary = (Map<String, Object>) breakoutSummaryItem.get("cervical_flexion");
        assertEquals("completed", cervicalBreakoutSummary.get("breakout_status"));
    }

    @Test
    void enrichWithSummary_shouldBuildCervicalReportMappingFields() {
        Map<String, Object> cervicalTopTier = new HashMap<>();
        cervicalTopTier.put("classification", "DP");
        cervicalTopTier.put("pain_vas", 6);

        Map<String, Object> cervicalBreakout = new HashMap<>();
        cervicalBreakout.put("breakout_status", "stopped_due_to_pain");
        cervicalBreakout.put("breakout_summary_text", "疼痛中止");
        cervicalBreakout.put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
        cervicalBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_flexion_top_tier", cervicalTopTier);
        payload.put("cervical_flexion_breakout", cervicalBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> cervical = (Map<String, Object>) sfma.get("cervical_flexion");

        assertEquals("DP", cervical.get("top_tier_result"));
        assertEquals("completed", cervical.get("breakout_status"));
        assertEquals(true, cervical.get("needs_manual_review"));
        assertTrue(String.valueOf(cervical.get("summary_text")).contains("疼痛"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedCervicalExtensionTwoLayerStructure() {
        Map<String, Object> extensionTopTier = new HashMap<>();
        extensionTopTier.put("classification", "FP");
        extensionTopTier.put("top_tier_note", "伸展伴疼痛");

        Map<String, Object> extensionBreakout = new HashMap<>();
        extensionBreakout.put("breakout_status", "partial");
        extensionBreakout.put("breakout_summary_text", "主动伸展受限，疼痛存在");
        extensionBreakout.put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
        extensionBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_extension_top_tier", extensionTopTier);
        payload.put("cervical_extension_breakout", extensionBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> topTier = (Map<String, Object>) enriched.get("top_tier");
        Map<String, Object> extensionLegacyTopTier = (Map<String, Object>) topTier.get("cervical_extension");
        assertEquals("FP", extensionLegacyTopTier.get("classification"));
        assertEquals(true, extensionLegacyTopTier.get("needs_breakout_suggestion"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> extensionLegacyBreakout = (Map<String, Object>) breakouts.get("cervical_extension_breakout");
        assertEquals("in_progress", extensionLegacyBreakout.get("status"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> topTierSummaryItem = (Map<String, Object>) summary.get("top_tier_summary_item");
        Map<String, Object> extensionTopTierSummary = (Map<String, Object>) topTierSummaryItem.get("cervical_extension");
        assertEquals("FP", extensionTopTierSummary.get("classification"));

        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> extensionBreakoutSummary = (Map<String, Object>) breakoutSummaryItem.get("cervical_extension");
        assertEquals("in_progress", extensionBreakoutSummary.get("breakout_status"));
    }

    @Test
    void enrichWithSummary_shouldBuildCervicalExtensionReportMappingFields() {
        Map<String, Object> extensionTopTier = new HashMap<>();
        extensionTopTier.put("classification", "DP");
        extensionTopTier.put("pain_vas", 7);

        Map<String, Object> extensionBreakout = new HashMap<>();
        extensionBreakout.put("breakout_status", "stopped_due_to_pain");
        extensionBreakout.put("breakout_summary_text", "伸展评估疼痛中止");
        extensionBreakout.put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
        extensionBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_extension_top_tier", extensionTopTier);
        payload.put("cervical_extension_breakout", extensionBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> extension = (Map<String, Object>) sfma.get("cervical_extension");

        assertEquals("DP", extension.get("top_tier_result"));
        assertEquals("completed", extension.get("breakout_status"));
        assertEquals(true, extension.get("needs_manual_review"));
        assertTrue(String.valueOf(extension.get("summary_text")).contains("疼痛"));
    }

    @Test
    void enrichWithFallback_shouldContainCervicalExtensionDefaults() {
        Map<String, Object> enriched = builder.enrichWithFallback(new HashMap<>(), "unit-test-extension");
        assertTrue(enriched.containsKey("cervical_extension_top_tier"));
        assertTrue(enriched.containsKey("cervical_extension_breakout"));

        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        assertTrue(sfma.containsKey("cervical_extension"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedCervicalRotationTwoLayerStructure() {
        Map<String, Object> rotationTopTier = new HashMap<>();
        rotationTopTier.put("left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        rotationTopTier.put("right", new HashMap<String, Object>() {{
            put("classification", "FP");
        }});

        Map<String, Object> rotationBreakout = new HashMap<>();
        rotationBreakout.put("left", new HashMap<String, Object>() {{
            put("breakout_status", "completed");
            put("breakout_summary_text", "左侧主动旋转受限");
            put("breakout_preliminary_direction", List.of("更偏活动度限制"));
        }});
        rotationBreakout.put("right", new HashMap<String, Object>() {{
            put("breakout_status", "partial");
            put("breakout_summary_text", "右侧旋转伴疼痛");
            put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
            put("needs_manual_review", true);
        }});
        rotationBreakout.put("asymmetry_focus", "左侧主动旋转较右侧更受限");

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_rotation_top_tier", rotationTopTier);
        payload.put("cervical_rotation_breakout", rotationBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> topTier = (Map<String, Object>) enriched.get("top_tier");
        Map<String, Object> leftLegacy = (Map<String, Object>) topTier.get("cervical_rotation_left");
        Map<String, Object> rightLegacy = (Map<String, Object>) topTier.get("cervical_rotation_right");
        assertEquals("DN", leftLegacy.get("classification"));
        assertEquals("FP", rightLegacy.get("classification"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> rotationLegacy = (Map<String, Object>) breakouts.get("cervical_rotation_breakout");
        assertNotNull(rotationLegacy);
        assertTrue(rotationLegacy.containsKey("status"));
        assertTrue(breakouts.containsKey("cervical_pattern"));
    }

    @Test
    void enrichWithSummary_shouldBuildCervicalRotationReportMappingFields() {
        Map<String, Object> rotationTopTier = new HashMap<>();
        rotationTopTier.put("left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        rotationTopTier.put("right", new HashMap<String, Object>() {{
            put("classification", "DP");
        }});

        Map<String, Object> rotationBreakout = new HashMap<>();
        rotationBreakout.put("left", new HashMap<String, Object>() {{
            put("breakout_status", "completed");
            put("breakout_summary_text", "左侧旋转控制不足");
        }});
        rotationBreakout.put("right", new HashMap<String, Object>() {{
            put("breakout_status", "stopped_due_to_pain");
            put("breakout_summary_text", "右侧旋转疼痛中止");
            put("needs_manual_review", true);
        }});
        rotationBreakout.put("asymmetry_focus", "右侧旋转疼痛与控制不足更突出");

        Map<String, Object> payload = new HashMap<>();
        payload.put("cervical_rotation_top_tier", rotationTopTier);
        payload.put("cervical_rotation_breakout", rotationBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> rotation = (Map<String, Object>) sfma.get("cervical_rotation");
        Map<String, Object> left = (Map<String, Object>) rotation.get("left");
        Map<String, Object> right = (Map<String, Object>) rotation.get("right");

        assertEquals("DN", left.get("top_tier_result"));
        assertEquals("DP", right.get("top_tier_result"));
        assertEquals("completed", right.get("breakout_status"));
        assertTrue(rotation.containsKey("rotation_asymmetry_focus"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedUpperExtremityPattern1TwoLayerStructure() {
        Map<String, Object> ue1TopTier = new HashMap<>();
        ue1TopTier.put("left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        ue1TopTier.put("right", new HashMap<String, Object>() {{
            put("classification", "FP");
        }});

        Map<String, Object> ue1Breakout = new HashMap<>();
        ue1Breakout.put("left", new HashMap<String, Object>() {{
            put("breakout_status", "completed");
            put("breakout_summary_text", "左侧上肢模式1肩胛控制不足");
            put("breakout_preliminary_direction", List.of("更偏运动控制问题"));
        }});
        ue1Breakout.put("right", new HashMap<String, Object>() {{
            put("breakout_status", "partial");
            put("breakout_summary_text", "右侧上肢模式1伴疼痛");
            put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
            put("needs_manual_review", true);
        }});
        ue1Breakout.put("asymmetry_focus", "右侧疼痛表现高于左侧");

        Map<String, Object> payload = new HashMap<>();
        payload.put("upper_extremity_pattern1_top_tier", ue1TopTier);
        payload.put("upper_extremity_pattern1_breakout", ue1Breakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> topTier = (Map<String, Object>) enriched.get("top_tier");
        Map<String, Object> leftLegacy = (Map<String, Object>) topTier.get("upper_extremity_pattern1_left");
        Map<String, Object> rightLegacy = (Map<String, Object>) topTier.get("upper_extremity_pattern1_right");
        assertEquals("DN", leftLegacy.get("classification"));
        assertEquals("FP", rightLegacy.get("classification"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> ue1Legacy = (Map<String, Object>) breakouts.get("upper_extremity_pattern1_breakout");
        assertNotNull(ue1Legacy);
        assertTrue(ue1Legacy.containsKey("status"));
    }

    @Test
    void enrichWithSummary_shouldBuildUpperExtremityPattern1ReportMappingFields() {
        Map<String, Object> ue1TopTier = new HashMap<>();
        ue1TopTier.put("left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        ue1TopTier.put("right", new HashMap<String, Object>() {{
            put("classification", "DP");
        }});

        Map<String, Object> ue1Breakout = new HashMap<>();
        ue1Breakout.put("left", new HashMap<String, Object>() {{
            put("breakout_status", "completed");
            put("breakout_summary_text", "左侧 UE1 控制不足");
        }});
        ue1Breakout.put("right", new HashMap<String, Object>() {{
            put("breakout_status", "stopped_due_to_pain");
            put("breakout_summary_text", "右侧 UE1 疼痛中止");
            put("needs_manual_review", true);
        }});
        ue1Breakout.put("asymmetry_focus", "右侧疼痛明显高于左侧");

        Map<String, Object> payload = new HashMap<>();
        payload.put("upper_extremity_pattern1_top_tier", ue1TopTier);
        payload.put("upper_extremity_pattern1_breakout", ue1Breakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> ue1 = (Map<String, Object>) sfma.get("upper_extremity_pattern1");
        Map<String, Object> left = (Map<String, Object>) ue1.get("left");
        Map<String, Object> right = (Map<String, Object>) ue1.get("right");

        assertEquals("DN", left.get("top_tier_result"));
        assertEquals("DP", right.get("top_tier_result"));
        assertEquals("completed", right.get("breakout_status"));
        assertTrue(ue1.containsKey("asymmetry_focus"));
    }

    @Test
    void enrichWithFallback_shouldContainUpperExtremityPattern1Defaults() {
        Map<String, Object> enriched = builder.enrichWithFallback(new HashMap<String, Object>(), "unit-test-ue1");
        assertTrue(enriched.containsKey("upper_extremity_pattern1_top_tier"));
        assertTrue(enriched.containsKey("upper_extremity_pattern1_breakout"));

        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        assertTrue(sfma.containsKey("upper_extremity_pattern1"));
    }

    @Test
    void enrichWithSummary_shouldBackfillUe1DedicatedFromLegacyCombinedBreakoutKey() {
        Map<String, Object> topTier = new HashMap<>();
        topTier.put("upper_extremity_pattern1_left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        topTier.put("upper_extremity_pattern1_right", new HashMap<String, Object>() {{
            put("classification", "FP");
            put("pain_present", true);
        }});

        Map<String, Object> legacyCombinedBreakout = new HashMap<>();
        legacyCombinedBreakout.put("status", "completed");
        legacyCombinedBreakout.put("findings", "历史聚合记录");
        legacyCombinedBreakout.put("pain_present", true);
        legacyCombinedBreakout.put("mobility_restriction_signs", "更偏活动度限制");
        legacyCombinedBreakout.put("motor_control_signs", "更偏运动控制问题");
        legacyCombinedBreakout.put("asymmetry_signs", "左右表现不一致");
        legacyCombinedBreakout.put("clinician_note", "历史聚合备注");

        Map<String, Object> breakouts = new HashMap<>();
        breakouts.put("upper_extremity_pattern1_breakout", legacyCombinedBreakout);

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);
        payload.put("breakouts", breakouts);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> dedicatedBreakout = (Map<String, Object>) enriched.get("upper_extremity_pattern1_breakout");
        Map<String, Object> left = (Map<String, Object>) dedicatedBreakout.get("left");
        Map<String, Object> right = (Map<String, Object>) dedicatedBreakout.get("right");
        assertEquals("completed", left.get("breakout_status"));
        assertEquals("completed", right.get("breakout_status"));
        assertEquals(true, left.get("active_ue_pattern1_pain"));
        assertEquals(true, right.get("active_ue_pattern1_pain"));
        assertEquals("左右表现不一致", dedicatedBreakout.get("asymmetry_focus"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> leftSummary = (Map<String, Object>) breakoutSummaryItem.get("upper_extremity_pattern1_left");
        Map<String, Object> rightSummary = (Map<String, Object>) breakoutSummaryItem.get("upper_extremity_pattern1_right");
        assertEquals("completed", leftSummary.get("breakout_status"));
        assertEquals("completed", rightSummary.get("breakout_status"));
    }

    @Test
    void enrichWithSummary_shouldKeepUe1StatusFlowInSummaryAndReportMapping() {
        Map<String, Object> ue1TopTier = new HashMap<>();
        ue1TopTier.put("left", new HashMap<String, Object>() {{
            put("classification", "DN");
        }});
        ue1TopTier.put("right", new HashMap<String, Object>() {{
            put("classification", "DP");
            put("pain_present", true);
        }});

        Map<String, Object> ue1Breakout = new HashMap<>();
        ue1Breakout.put("left", new HashMap<String, Object>() {{
            put("breakout_status", "skipped");
            put("breakout_summary_text", "左侧暂不分解");
            put("breakout_preliminary_direction", List.of("需结合其他模式综合判断"));
        }});
        ue1Breakout.put("right", new HashMap<String, Object>() {{
            put("breakout_status", "stopped_due_to_pain");
            put("breakout_summary_text", "右侧疼痛中止");
            put("breakout_preliminary_direction", List.of("更偏疼痛主导"));
            put("needs_manual_review", true);
        }});
        ue1Breakout.put("asymmetry_focus", "右侧较左侧疼痛与控制问题更突出");

        Map<String, Object> payload = new HashMap<>();
        payload.put("upper_extremity_pattern1_top_tier", ue1TopTier);
        payload.put("upper_extremity_pattern1_breakout", ue1Breakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> leftSummary = (Map<String, Object>) breakoutSummaryItem.get("upper_extremity_pattern1_left");
        Map<String, Object> rightSummary = (Map<String, Object>) breakoutSummaryItem.get("upper_extremity_pattern1_right");
        assertEquals("skipped", leftSummary.get("breakout_status"));
        assertEquals("completed", rightSummary.get("breakout_status"));
        assertEquals(true, rightSummary.get("needs_manual_review"));

        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> ue1 = (Map<String, Object>) sfma.get("upper_extremity_pattern1");
        Map<String, Object> left = (Map<String, Object>) ue1.get("left");
        Map<String, Object> right = (Map<String, Object>) ue1.get("right");
        assertEquals("skipped", left.get("breakout_status"));
        assertEquals("completed", right.get("breakout_status"));
        assertEquals(true, right.get("needs_manual_review"));
        assertTrue(String.valueOf(ue1.get("asymmetry_focus")).contains("右侧较左侧疼痛与控制问题更突出"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedMsfBreakoutTwoLayerStructure() {
        Map<String, Object> topTier = new HashMap<>();
        topTier.put("multi_segmental_flexion", new HashMap<String, Object>() {{
            put("classification", "DN");
            put("breakout_reason_text", "MSF 功能异常建议进入分解");
        }});

        Map<String, Object> msfBreakout = new HashMap<>();
        msfBreakout.put("breakout_status", "in_progress");
        msfBreakout.put("fingertips_to_floor_status", "明显不能触地");
        msfBreakout.put("fingertips_to_floor_distance_cm", 18);
        msfBreakout.put("breakout_preliminary_direction", List.of("更偏后侧链张力限制", "更偏运动控制问题"));
        msfBreakout.put("primary_restriction_chain", List.of("足踝-后侧链"));
        msfBreakout.put("primary_control_deficit_chain", List.of("LPHC控制不足"));
        msfBreakout.put("breakout_summary_text", "MSF 分解提示后侧链张力限制明显");
        msfBreakout.put("left_right_asymmetry_focus", "左侧问题更突出");
        msfBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);
        payload.put("msf_breakout", msfBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> dedicated = (Map<String, Object>) enriched.get("msf_breakout");
        assertEquals("in_progress", dedicated.get("breakout_status"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> msfLegacy = (Map<String, Object>) breakouts.get("msf_breakout");
        assertEquals("in_progress", msfLegacy.get("status"));
        assertTrue(String.valueOf(msfLegacy.get("rom_key_values")).contains("指尖距地"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> msfSummary = (Map<String, Object>) breakoutSummaryItem.get("multi_segmental_flexion");
        assertEquals("in_progress", msfSummary.get("breakout_status"));
        assertEquals(true, msfSummary.get("needs_manual_review"));

        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> msfMapping = (Map<String, Object>) sfma.get("msf_breakout");
        assertEquals("DN", msfMapping.get("top_tier_result"));
        assertEquals("in_progress", msfMapping.get("breakout_status"));
        assertTrue(String.valueOf(msfMapping.get("summary_text")).contains("MSF"));
    }

    @Test
    void enrichWithSummary_shouldSupportDedicatedMseBreakoutTwoLayerStructure() {
        Map<String, Object> topTier = new HashMap<>();
        topTier.put("multi_segmental_extension", new HashMap<String, Object>() {{
            put("classification", "DP");
            put("breakout_reason_text", "MSE 疼痛性功能异常建议进入分解");
        }});

        Map<String, Object> mseBreakout = new HashMap<>();
        mseBreakout.put("breakout_status", "in_progress");
        mseBreakout.put("active_extension_global_quality", "明显受限");
        mseBreakout.put("hip_extension_contribution", "明显不足");
        mseBreakout.put("thoracic_extension_participation", "减少");
        mseBreakout.put("breakout_preliminary_direction", List.of("更偏髋伸展不足", "更偏腰盆控制问题"));
        mseBreakout.put("primary_restriction_chain", List.of("髋前侧-骨盆链"));
        mseBreakout.put("primary_control_deficit_chain", List.of("LPHC控制不足"));
        mseBreakout.put("breakout_summary_text", "MSE 分解提示髋伸展不足并伴腰盆控制问题");
        mseBreakout.put("left_right_asymmetry_focus", "右侧问题更突出");
        mseBreakout.put("needs_manual_review", true);

        Map<String, Object> payload = new HashMap<>();
        payload.put("top_tier", topTier);
        payload.put("mse_breakout", mseBreakout);

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> dedicated = (Map<String, Object>) enriched.get("mse_breakout");
        assertEquals("in_progress", dedicated.get("breakout_status"));

        Map<String, Object> breakouts = (Map<String, Object>) enriched.get("breakouts");
        Map<String, Object> mseLegacy = (Map<String, Object>) breakouts.get("mse_breakout");
        assertEquals("in_progress", mseLegacy.get("status"));
        assertTrue(String.valueOf(mseLegacy.get("findings")).contains("MSE"));

        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");
        Map<String, Object> breakoutSummaryItem = (Map<String, Object>) summary.get("breakout_summary_item");
        Map<String, Object> mseSummary = (Map<String, Object>) breakoutSummaryItem.get("multi_segmental_extension");
        assertEquals("in_progress", mseSummary.get("breakout_status"));
        assertEquals(true, mseSummary.get("needs_manual_review"));

        Map<String, Object> reportMapping = (Map<String, Object>) enriched.get("report_mapping");
        Map<String, Object> sfma = (Map<String, Object>) reportMapping.get("sfma");
        Map<String, Object> mseMapping = (Map<String, Object>) sfma.get("multi_segmental_extension");
        assertEquals("DP", mseMapping.get("top_tier_result"));
        assertEquals("in_progress", mseMapping.get("breakout_status"));
        assertTrue(String.valueOf(mseMapping.get("summary_text")).contains("MSE"));
    }

    @Test
    void enrichWithSummary_shouldRouteLongSitPainWithSacrumNormalToProneNotStop() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msf_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "in_progress");
            put("single_leg_standing_forward_flexion_result", "双侧功能障碍或疼痛");
            put("long_sit_toe_touch_result", "FP");
            put("long_sit_toe_touch_reach_status", "未触及足趾");
            put("long_sit_sacral_angle_status", "正常(≥80°)");
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> msf = (Map<String, Object>) enriched.get("msf_breakout");
        assertEquals("继续俯卧位向后摆动", msf.get("flow_next_step"));
        Map<String, Object> analysis = (Map<String, Object>) msf.get("msf_analysis");
        Map<String, Object> summary = (Map<String, Object>) analysis.get("summary");
        assertEquals(false, summary.get("stop_and_treat_pain"));
    }

    @Test
    void enrichWithSummary_shouldRouteAslrPainToPslrNotStop() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msf_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "in_progress");
            put("single_leg_standing_forward_flexion_result", "双侧功能障碍或疼痛");
            put("long_sit_toe_touch_result", "DN");
            put("long_sit_toe_touch_reach_status", "未触及足趾");
            put("long_sit_sacral_angle_status", "受限(<80°)");
            put("aslr_result", "FP");
            put("aslr_left_deg", 62);
            put("aslr_right_deg", 60);
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> msf = (Map<String, Object>) enriched.get("msf_breakout");
        assertEquals("继续被动直腿抬高", msf.get("flow_next_step"));
        Map<String, Object> analysis = (Map<String, Object>) msf.get("msf_analysis");
        Map<String, Object> summary = (Map<String, Object>) analysis.get("summary");
        assertEquals(false, summary.get("stop_and_treat_pain"));
    }

    @Test
    void enrichWithSummary_shouldStopWhenPslrIsPainful() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msf_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "in_progress");
            put("single_leg_standing_forward_flexion_result", "双侧功能障碍或疼痛");
            put("long_sit_toe_touch_result", "DN");
            put("long_sit_sacral_angle_status", "受限(<80°)");
            put("aslr_result", "DN");
            put("pslr_result", "DP");
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> msf = (Map<String, Object>) enriched.get("msf_breakout");
        assertEquals("停止并优先处理疼痛", msf.get("flow_next_step"));
        Map<String, Object> analysis = (Map<String, Object>) msf.get("msf_analysis");
        Map<String, Object> summary = (Map<String, Object>) analysis.get("summary");
        assertEquals(true, summary.get("stop_and_treat_pain"));
        assertEquals(true, summary.get("manual_review_required"));
    }

    @Test
    void enrichWithSummary_shouldAddBaseFlexionSmcdHintWhenRollingDn() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msf_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "in_progress");
            put("single_leg_standing_forward_flexion_result", "双侧功能正常且无痛");
            put("long_sit_toe_touch_result", "FN");
            put("long_sit_toe_touch_reach_status", "可触及足趾");
            put("long_sit_sacral_angle_status", "正常(≥80°)");
            put("rolling_result", "DN");
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> msf = (Map<String, Object>) enriched.get("msf_breakout");
        Map<String, Object> analysis = (Map<String, Object>) msf.get("msf_analysis");
        Map<String, Object> summary = (Map<String, Object>) analysis.get("summary");
        List<String> likelyPattern = (List<String>) summary.get("likely_pattern");
        assertTrue(likelyPattern.contains("基础屈曲动作模式SMCD倾向"));
    }

    @Test
    void enrichWithSummary_shouldPersistNodeResultAndRequiredFieldsForMsfFlow() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msf_breakout", new HashMap<String, Object>() {{
            put("breakout_status", "in_progress");
            put("single_leg_standing_forward_flexion_result", "单侧功能障碍或疼痛");
            put("single_leg_standing_forward_flexion_asymmetry", "左侧更差");
            put("single_leg_standing_forward_flexion_note", "左侧前屈受限");
            put("long_sit_toe_touch_result", "DN");
            put("long_sit_toe_touch_reach_status", "未触及足趾");
            put("long_sit_sacral_angle_status", "受限(<80°)");
            put("long_sit_sacral_angle_deg", 72);
            put("long_sit_toe_touch_note", "骶骨角受限");
            put("aslr_result", "DN");
            put("aslr_left_deg", 60);
            put("aslr_right_deg", 62);
            put("aslr_note", "双侧偏低");
        }});

        Map<String, Object> enriched = builder.enrichWithSummary(payload);
        Map<String, Object> msf = (Map<String, Object>) enriched.get("msf_breakout");
        Map<String, Object> analysis = (Map<String, Object>) msf.get("msf_analysis");
        Map<String, Object> flow = (Map<String, Object>) analysis.get("flexion_flow");

        Map<String, Object> single = (Map<String, Object>) flow.get("single_leg_stance_forward_bend");
        assertEquals("单侧功能障碍或疼痛", single.get("result"));
        assertEquals("unilateral_abnormal_or_pain", single.get("result_type"));
        assertEquals(true, single.get("pain_present"));
        assertEquals("左侧前屈受限", single.get("note"));
        assertTrue(String.valueOf(single.get("summary_text")).contains("单腿站立体前屈"));

        Map<String, Object> longSit = (Map<String, Object>) flow.get("long_sit_toe_touch");
        assertEquals("DN", longSit.get("result"));
        assertEquals("abnormal_with_sacrum_limited", longSit.get("result_type"));
        assertEquals(false, longSit.get("pain_present"));
        assertEquals("骶骨角受限", longSit.get("note"));
        assertTrue(String.valueOf(longSit.get("summary_text")).contains("长坐位触趾"));

        Map<String, Object> aslr = (Map<String, Object>) flow.get("active_straight_leg_raise");
        assertEquals("DN", aslr.get("result"));
        assertEquals("DN_or_FP_or_DP", aslr.get("result_type"));
        assertEquals(false, aslr.get("pain_present"));
        assertEquals("双侧偏低", aslr.get("note"));
        assertTrue(String.valueOf(aslr.get("summary_text")).contains("ASLR"));
    }
}
