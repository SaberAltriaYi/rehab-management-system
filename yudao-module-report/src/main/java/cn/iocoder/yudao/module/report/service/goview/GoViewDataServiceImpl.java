package cn.iocoder.yudao.module.report.service.goview;

import cn.iocoder.yudao.module.report.controller.admin.goview.vo.data.GoViewDataRespVO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.report.enums.ErrorCodeConstants.GO_VIEW_RAW_SQL_DISABLED;

/**
 * Do not execute SQL provided by a report editor against the application data source.
 * JdbcTemplate bypasses the MyBatis tenant interceptor and can read patient data
 * from other tenants. Replace only with a separately reviewed, scoped report API.
 */
@Service
@Validated
public class GoViewDataServiceImpl implements GoViewDataService {

    @Override
    public GoViewDataRespVO getDataBySQL(String sql) {
        throw exception(GO_VIEW_RAW_SQL_DISABLED);
    }

}
