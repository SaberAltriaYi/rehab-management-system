package cn.iocoder.yudao.module.rehab.service.report;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Arrays;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Read-only, no clinical rows. */
@Service
public class RehabReportGenerationContract {
    public static final String CONTRACT = "report-idempotency-v1";
    static final String CHECKSUM = "a9bc632fa0ac91be76da659e93b8c7bd3053bbcda98cd69a470ddbe70f32246f";
    static final String VERSION_SQL = "SELECT checksum FROM internal_schema_history WHERE version=?";
    static final String ENGINE_SQL = "SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rehab_report_generation_request'";
    static final String PK_SQL = "SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rehab_report_generation_request' AND index_name='PRIMARY' AND non_unique=0 ORDER BY seq_in_index";
    static final String SHAPE_SQL = "SELECT request_fingerprint,report_id,report_no,report_version,fallback_used,fallback_message FROM rehab_report_generation_request WHERE 1=0";
    @Resource private JdbcTemplate jdbcTemplate;

    public boolean isReady() {
        try {
            if (!CHECKSUM.equals(jdbcTemplate.queryForObject(VERSION_SQL, String.class, "036"))) return false;
            if (!"InnoDB".equalsIgnoreCase(jdbcTemplate.queryForObject(ENGINE_SQL, String.class))) return false;
            if (!Arrays.asList("tenant_id", "assessment_id", "operator_user_id", "request_key_hash")
                    .equals(jdbcTemplate.queryForList(PK_SQL, String.class))) return false;
            jdbcTemplate.queryForList(SHAPE_SQL);
            return true;
        } catch (DataAccessException ignored) {
            // Do not leak schema diagnostics or adopt/repair a table from an API call.
            return false;
        }
    }
}
