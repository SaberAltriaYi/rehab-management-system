package cn.iocoder.yudao.module.report.service.goview;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.report.enums.ErrorCodeConstants.GO_VIEW_RAW_SQL_DISABLED;

@Import(GoViewDataServiceImpl.class)
public class GoViewDataServiceImplTest extends BaseDbUnitTest {

    @Resource
    private GoViewDataServiceImpl goViewDataService;

    @Test
    public void testRawSqlIsRejectedIncludingCrossTenantRead() {
        assertServiceException(() -> goViewDataService.getDataBySQL(
                "SELECT * FROM rehab_patient WHERE tenant_id <> 1"), GO_VIEW_RAW_SQL_DISABLED);
        assertServiceException(() -> goViewDataService.getDataBySQL("SELECT * FROM system_users"),
                GO_VIEW_RAW_SQL_DISABLED);
    }

}
