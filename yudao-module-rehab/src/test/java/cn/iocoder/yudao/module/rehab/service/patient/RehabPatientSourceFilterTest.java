package cn.iocoder.yudao.module.rehab.service.patient;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.patient.RehabPatientMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.validation.Validation;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. */
class RehabPatientSourceFilterTest {
    @BeforeAll
    static void metadata() {
        if (TableInfoHelper.getTableInfo(RehabPatientDO.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RehabPatientDO.class);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void absentOrClearedSourceFilterExcludesArchivedWhileExplicitSelectionCanRecoverIt() {
        for (String source : new String[] {null, "", "REAL", "SYNTHETIC", "SYNTHETIC_ARCHIVED"}) {
            RehabPatientMapper mapper = mock(RehabPatientMapper.class, CALLS_REAL_METHODS);
            AtomicReference<LambdaQueryWrapperX<RehabPatientDO>> query = new AtomicReference<>();
            doAnswer(call -> {
                query.set(call.getArgument(1));
                return PageResult.empty();
            }).when(mapper).selectPage(any(PageParam.class), any(Wrapper.class));
            RehabPatientPageReqVO request = new RehabPatientPageReqVO();
            request.setDataOrigin(source);
            mapper.selectPage(request, (Collection<Long>) null, (Collection<Long>) null);
            String sql = query.get().getSqlSegment();
            assertTrue(sql.contains("data_origin"));
            assertTrue(sql.contains(source == null || source.isEmpty() ? "<>" : "="));
            assertTrue(query.get().getParamNameValuePairs().containsValue(
                    source == null || source.isEmpty() ? "SYNTHETIC_ARCHIVED" : source));
        }
    }

    @Test
    void emptyVisibleScopeNeverInvokesAnyDatabasePageQuery() {
        RehabPatientMapper mapper = mock(RehabPatientMapper.class, CALLS_REAL_METHODS);
        assertEquals(0L, mapper.selectPage(new RehabPatientPageReqVO(),
                Collections.<Long>emptyList(), (Collection<Long>) null).getTotal());
        verify(mapper, never()).selectPage(any(PageParam.class), any(Wrapper.class));
    }

    @Test
    void clearingFilterIsValidButUnknownSourceCannotBeQueried() {
        try (javax.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            RehabPatientPageReqVO request = new RehabPatientPageReqVO();
            request.setDataOrigin("");
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setDataOrigin("UNKNOWN");
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }
}
