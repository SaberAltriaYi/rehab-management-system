package cn.iocoder.yudao.module.rehab.service.motion;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionReportDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.motion.RehabMotionReportMapper;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineClient;
import cn.iocoder.yudao.module.rehab.service.motion.engine.MotionEngineException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic only: authorization precedes render, frozen content integrity, source contract. */
class UnifiedReportExportTest {
    private RehabMotionReviewService service;
    private RehabMotionAccess access;
    private MotionEngineClient engine;
    private RehabMotionReportDO report;

    @BeforeEach
    void setup() {
        service = new RehabMotionReviewService();
        access = mock(RehabMotionAccess.class);
        engine = mock(MotionEngineClient.class);
        RehabMotionReportMapper mapper = mock(RehabMotionReportMapper.class);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "engineClient", engine);
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        report = new RehabMotionReportDO();
        report.setId(11L);
        report.setAssessmentId(22L);
        report.setVersionNo(1);
        report.setStatus("signed");
        report.setSignerName("Synthetic reviewer");
        report.setContentJson("{\"title\":\"Synthetic report\",\"engine_version\":\"test\",\"sections\":[{\"title\":\"Result\",\"lines\":[\"81.48%\"]}]}");
        report.setContentSha256(DigestUtil.sha256Hex(report.getContentJson()));
        when(mapper.selectById(11L)).thenReturn(report);
        when(engine.renderReport(anyMap())).thenReturn(new byte[]{'P','K',3,4});
    }

    @Test
    void deniesUnauthorizedBeforeEngineCall() {
        doThrow(new SecurityException("synthetic denied")).when(access).readable(22L, 99L);
        assertThrows(SecurityException.class, () -> service.reportDocx(11L, 99L));
        verifyNoInteractions(engine);
    }

    @Test
    void rejectsModifiedFrozenContent() {
        report.setContentSha256("0".repeat(64));
        assertThrows(MotionEngineException.class, () -> service.reportDocx(11L, 1L));
        verifyNoInteractions(engine);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rendersAuthorizedSnapshotWithHistoryStatus() {
        report.setStatus("superseded");
        assertArrayEquals(new byte[]{'P','K',3,4}, service.reportDocx(11L, 1L));
        ArgumentCaptor<Map> document = ArgumentCaptor.forClass(Map.class);
        verify(engine).renderReport(document.capture());
        assertEquals("main-system", document.getValue().get("source"));
        assertEquals("superseded", document.getValue().get("source_status"));
        assertEquals(report.getContentSha256(), document.getValue().get("input_sha256"));
        verify(access).readable(22L, 1L);
        verify(access).audit(eq(22L), eq("report_docx_export"), eq(1L), isNull(), anyMap(), isNull());
    }
}
