package cn.iocoder.yudao.module.rehab.service.report;

import cn.iocoder.yudao.module.rehab.controller.admin.report.RehabReportController;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportGenerateReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.rehab.service.report.RehabReportGenerationContract.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Metadata-only fixtures. */
class RehabReportGenerationContractTest {
    private JdbcTemplate jdbc;
    private RehabReportGenerationContract contract;
    @BeforeEach void open() {
        jdbc = mock(JdbcTemplate.class);
        contract = new RehabReportGenerationContract();
        ReflectionTestUtils.setField(contract, "jdbcTemplate", jdbc);
    }
    private void version() { when(jdbc.queryForObject(VERSION_SQL, String.class, "036")).thenReturn(CHECKSUM); }
    private void engine() { when(jdbc.queryForObject(ENGINE_SQL, String.class)).thenReturn("InnoDB"); }
    private void key() { when(jdbc.queryForList(PK_SQL, String.class)).thenReturn(Arrays.asList("tenant_id", "assessment_id", "operator_user_id", "request_key_hash")); }
    @Test void matchingLedgerEngineKeyAndShapeAreReady() {
        version(); engine(); key();
        when(jdbc.queryForList(SHAPE_SQL)).thenReturn(Collections.emptyList());
        assertTrue(contract.isReady());
        verify(jdbc).queryForList(SHAPE_SQL);
    }
    @Test void missingLedgerDoesNotAdoptOrCreateAnything() {
        when(jdbc.queryForObject(VERSION_SQL, String.class, "036")).thenThrow(new DataAccessResourceFailureException("SYNTHETIC_SCHEMA"));
        assertFalse(contract.isReady());
        verify(jdbc).queryForObject(VERSION_SQL, String.class, "036");
        verifyNoMoreInteractions(jdbc);
    }
    @Test void changedChecksumIsNotReady() {
        when(jdbc.queryForObject(VERSION_SQL, String.class, "036")).thenReturn("SYNTHETIC_WRONG");
        assertFalse(contract.isReady());
    }
    @Test void nonTransactionalEngineIsNotReady() {
        version(); when(jdbc.queryForObject(ENGINE_SQL, String.class)).thenReturn("MyISAM");
        assertFalse(contract.isReady());
    }
    @Test void wrongPrimaryKeyIsNotReady() {
        version(); engine();
        when(jdbc.queryForList(PK_SQL, String.class)).thenReturn(Arrays.asList("request_key_hash", "operator_user_id", "assessment_id", "tenant_id"));
        assertFalse(contract.isReady());
    }
    @Test void missingResultColumnIsNotReady() {
        version(); engine(); key();
        when(jdbc.queryForList(SHAPE_SQL)).thenThrow(new DataAccessResourceFailureException("SYNTHETIC_COLUMN"));
        assertFalse(contract.isReady());
    }
    @Test void endpointRetainsGenerationPermissionAndOnlyReturnsTwoMetadataFields() throws Exception {
        String expected = RehabReportController.class.getMethod("generateReport", RehabReportGenerateReqVO.class)
                .getAnnotation(PreAuthorize.class).value();
        assertEquals(expected, RehabReportController.class.getMethod("getGenerationContract").getAnnotation(PreAuthorize.class).value());
        RehabReportController controller = new RehabReportController();
        RehabReportGenerationContract ready = mock(RehabReportGenerationContract.class);
        ReflectionTestUtils.setField(controller, "generationContract", ready);
        when(ready.isReady()).thenReturn(true);
        Map<String, Object> result = controller.getGenerationContract().getData();
        assertEquals(new HashSet<>(Arrays.asList("contract", "ready")), result.keySet());
        assertEquals(CONTRACT, result.get("contract")); assertEquals(true, result.get("ready"));
    }
}
