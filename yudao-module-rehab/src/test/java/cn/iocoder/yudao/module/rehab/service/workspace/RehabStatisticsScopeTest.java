package cn.iocoder.yudao.module.rehab.service.workspace;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.dal.dataobject.plan.RehabCarePlanDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. Synthetic H2 only. */
class RehabStatisticsScopeTest {
    private JdbcTemplate jdbc;

    @BeforeAll
    static void metadata() {
        if (TableInfoHelper.getTableInfo(RehabCarePlanDO.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RehabCarePlanDO.class);
        }
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:stats_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE rehab_patient (id BIGINT PRIMARY KEY,tenant_id BIGINT,"
                + "data_origin VARCHAR(32) DEFAULT 'REAL',name VARCHAR(64),deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE synthetic_record (patient_id BIGINT,tenant_id BIGINT,status VARCHAR(16))");
        jdbc.update("INSERT INTO rehab_patient (id,tenant_id,data_origin,name,deleted) VALUES "
                + "(801,71,'REAL','synthetic business fixture',0),(802,71,'SYNTHETIC','ordinary name',0),"
                + "(803,71,'SYNTHETIC_ARCHIVED','ordinary name',0),(804,71,'REAL','deleted fixture',1),"
                + "(805,71,'REAL','【QA测试】仍为业务分类的合成样本',0),(901,72,'REAL','other tenant',0)");
        jdbc.update("INSERT INTO synthetic_record VALUES (801,71,'active'),(802,71,'active'),(803,71,'active'),"
                + "(804,71,'active'),(805,71,'active'),(901,72,'active'),(901,71,'active'),(9999,71,'active')");
        TenantContextHolder.setTenantId(71L);
    }

    @AfterEach
    void close() {
        TenantContextHolder.clear();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.Statement statement = connection.createStatement()) { statement.execute("SHUTDOWN"); }
            return null;
        });
    }

    private int count(LambdaQueryWrapperX<RehabCarePlanDO> query) {
        String sql = RehabStatisticsScope.realRecords(query).getSqlSegment();
        Matcher matcher = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)}").matcher(sql);
        List<Object> values = new ArrayList<>();
        values.add(TenantContextHolder.getRequiredTenantId());
        StringBuffer bound = new StringBuffer();
        while (matcher.find()) {
            values.add(query.getParamNameValuePairs().get(matcher.group(1)));
            matcher.appendReplacement(bound, "?");
        }
        matcher.appendTail(bound);
        return jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_record WHERE tenant_id=? AND " + bound,
                Integer.class, values.toArray());
    }

    @Test
    void excludesSyntheticArchivedDeletedMissingAndCrossTenantReferencesButNeverMatchesName() {
        assertEquals(2, count(new LambdaQueryWrapperX<>()));
        TenantContextHolder.setTenantId(72L);
        assertEquals(1, count(new LambdaQueryWrapperX<>()));
    }

    @Test
    void retainsExistingVisiblePatientAndStatusConditions() {
        assertEquals(1, count(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .in(RehabCarePlanDO::getPatientId, Arrays.asList(801L, 802L))
                .eq(RehabCarePlanDO::getStatus, "active")));
        assertEquals(0, count(new LambdaQueryWrapperX<RehabCarePlanDO>()
                .in(RehabCarePlanDO::getPatientId, Arrays.asList(802L, 803L))));
    }

    @Test
    void archiveAndRestoreDoNotChangeBusinessCountsOrDeleteAnySourceRows() {
        for (String state : Arrays.asList("SYNTHETIC_ARCHIVED", "SYNTHETIC")) {
            jdbc.update("UPDATE rehab_patient SET data_origin=? WHERE id=802", state);
            assertEquals(2, count(new LambdaQueryWrapperX<>()));
        }
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM rehab_patient", Integer.class));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_record", Integer.class));
    }

    @Test
    void missingTenantFailsClosedInsteadOfBuildingInstitutionWideQuery() {
        TenantContextHolder.clear();
        assertThrows(RuntimeException.class, () -> RehabStatisticsScope.realRecords(new LambdaQueryWrapperX<>()));
    }
}
