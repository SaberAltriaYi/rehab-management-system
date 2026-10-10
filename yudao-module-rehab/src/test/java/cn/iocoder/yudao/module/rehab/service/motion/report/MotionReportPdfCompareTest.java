package cn.iocoder.yudao.module.rehab.service.motion.report;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAiDraftDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionAssessmentDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionMetricDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionScoreDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.enums.RehabMotionConstants;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MotionReportPdfCompareTest {

    private static RehabMotionScoreDO fms(String test, Integer system, Integer fin, String status) {
        return RehabMotionScoreDO.builder().family("FMS").testCode(test).side("overall").scoringScheme("FMS_0_3")
                .systemScore(system == null ? null : BigDecimal.valueOf(system))
                .finalScore(fin == null ? null : BigDecimal.valueOf(fin)).finalStatus(status).build();
    }

    private static MotionReportBuilder.Input input() {
        MotionReportBuilder.Input in = new MotionReportBuilder.Input();
        in.assessment = RehabMotionAssessmentDO.builder().id(1L).patientId(2L).visitType("initial").dataSource("upload")
                .cameraCount(2).captureTime(LocalDateTime.of(2026, 9, 1, 10, 0)).engineVersion("motion-engine/1.0.0")
                .ruleVersion("motion-rules/1.0.0").resultSchemaVersion("motion-result/1.0.0").analyzedRevision(2)
                .protocolVersionsJson("{\"fms\":{\"protocol_version\":\"V0.2\",\"file_sha256\":\"abcdef0123456789\"}}")
                .sessionQualityStatus("pass").build();
        in.patientName = "测试患者";
        in.trials.add(RehabMotionTrialDO.builder().trialKey("ds1").testCode("FMS_DEEP_SQUAT").side("bilateral")
                .attemptNo(1).qcStatus("pass").repCount(3).pain(false).build());
        in.scores.add(fms("FMS_DEEP_SQUAT", 2, 2, RehabMotionConstants.FINAL_CONFIRMED));
        in.scores.add(fms("FMS_HURDLE_STEP", 3, 1, RehabMotionConstants.FINAL_MODIFIED));
        in.scores.add(fms("FMS_INLINE_LUNGE", 2, null, RehabMotionConstants.FINAL_PENDING));
        in.metrics.add(RehabMotionMetricDO.builder().trialId(1L).metricKey("m:ds1:knee:right:bottom:1").code("knee")
                .valueNum(new BigDecimal("95.5")).unit("deg").classification("mot_direct").build());
        Map<String, Object> draft = new LinkedHashMap<String, Object>();
        draft.put("therapist_summary", "治疗师版内部说明");
        draft.put("patient_summary", "请坚持训练，下次复评对比。");
        draft.put("training_suggestions", Collections.singletonList(Collections.singletonMap("text", "踝背屈活动度训练")));
        in.acceptedDraft = RehabMotionAiDraftDO.builder().provider("openai").model("m").promptVersion("p1")
                .contentJson(JsonUtils.toJsonString(draft)).build();
        in.signerName = "王治疗师";
        in.signedTime = LocalDateTime.of(2026, 9, 2, 9, 30);
        in.versionNo = 1;
        return in;
    }

    @Test
    void patientViewShowsOnlyReviewedFinalScoresAndNoTotalUntilAllSeven() {
        String json = JsonUtils.toJsonString(MotionReportBuilder.patient(input()));
        assertTrue(json.contains("：2\""), json);
        assertTrue(json.contains("：1\""), "修改后的最终分展示给患者，系统分不展示");
        assertFalse(json.contains("：3\""), "系统建议分 3 不得出现在患者版");
        assertFalse(json.contains("弓步"), "未审核项不出现在患者版");
        assertFalse(json.contains("治疗师版内部说明"));
        assertFalse(json.contains("knee"));
        assertTrue(json.contains("请坚持训练"));
        assertFalse(json.contains("FMS 总分"), "7 项未全部审核时不出总分");
        assertTrue(json.contains(MotionReportBuilder.DISCLAIMER));
    }

    @Test
    void therapistViewCarriesVersionsSystemAndFinalScores() {
        Map<String, Object> content = MotionReportBuilder.therapist(input());
        String json = JsonUtils.toJsonString(content);
        assertTrue(json.contains("motion-engine/1.0.0") && json.contains("motion-rules/1.0.0"));
        assertTrue(json.contains("V0.2"));
        assertTrue(json.contains("王治疗师"));
        assertNull(content.get("fms_total"), "7 项未全部审核 → 无总分");
    }

    @Test
    void pdfRendersChineseWhenFontAvailable() throws Exception {
        String[] fonts = {"/System/Library/Fonts/STHeiti Medium.ttc", "/System/Library/Fonts/STHeiti Light.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"};
        boolean any = false;
        for (String f : fonts) {
            any |= new File(f).isFile();
        }
        assumeTrue(any, "本机无中文字体，跳过（部署镜像内置 wqy-zenhei）");
        Map<String, Object> content = MotionReportBuilder.therapist(input());
        content.put("assessment_ref", "MA-1");
        content.put("version_no", 1);
        byte[] pdf = new MotionPdfRenderer().render(content);
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4, "US-ASCII"));
        PDDocument doc = PDDocument.load(pdf);
        try {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("王治疗师"), "签署人必须出现在 PDF");
        } finally {
            doc.close();
        }
    }

    @Test
    void wrapKeepsCjkWidthAndDropsControlChars() {
        List<String> lines = MotionPdfRenderer.wrap("中文中文中文\u0007abc", 6);
        assertEquals(Arrays.asList("中文中", "文中文", "abc"), lines);
        assertTrue(MotionPdfRenderer.wrap("", 10).isEmpty());
    }

    // ---------------------------------------------------------------- 对比

    @Test
    void comparisonUsesFinalScoresAndMeasurementErrorOnly() {
        RehabMotionScoreDO base = fms("FMS_DEEP_SQUAT", 1, 1, RehabMotionConstants.FINAL_CONFIRMED);
        RehabMotionScoreDO cur = fms("FMS_DEEP_SQUAT", 3, null, RehabMotionConstants.FINAL_PENDING);
        List<Map<String, Object>> rows = MotionComparator.compareScores(Collections.singletonList(base),
                Collections.singletonList(cur));
        assertEquals(1, rows.size());
        assertEquals("final", rows.get(0).get("baseline_source"));
        assertEquals("system_unreviewed", rows.get(0).get("current_source"));
        assertEquals(0, ((BigDecimal) rows.get(0).get("delta")).compareTo(BigDecimal.valueOf(2)));
        assertEquals(Boolean.FALSE, MotionComparator.beyond("deg", 4.0));
        assertEquals(Boolean.TRUE, MotionComparator.beyond("deg", -6.0));
        assertEquals(Boolean.FALSE, MotionComparator.beyond("m", 0.012));
        assertEquals(Boolean.TRUE, MotionComparator.beyond("m", 0.013));
        assertEquals(Boolean.TRUE, MotionComparator.beyond("cm", 1.3));
        assertNull(MotionComparator.beyond("ratio", 0.5), "无测量误差依据的单位不做判断");
        Map<String, Object> all = MotionComparator.compare(Collections.singletonList(base), Collections.singletonList(cur),
                new ArrayList<RehabMotionTrialDO>(), new ArrayList<RehabMotionMetricDO>(),
                new ArrayList<RehabMotionTrialDO>(), new ArrayList<RehabMotionMetricDO>());
        String json = JsonUtils.toJsonString(all);
        assertFalse(json.contains("改善") || json.contains("恶化") || json.contains("进步"));
    }

}
