package cn.iocoder.yudao.module.rehab.service.workspace;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.enums.RehabPatientDataOrigin;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. */
public final class RehabStatisticsScope {
    private RehabStatisticsScope() {
    }

    /** Statistical records only: clinical task/notification lists must not disappear. */
    public static <T> LambdaQueryWrapperX<T> realRecords(LambdaQueryWrapperX<T> query) {
        // Keep the nested patient query explicitly tenant scoped as well as the
        // outer mapper's existing tenant/role/department conditions. Parameterized,
        // with no caller-controlled table/column names and no broad ID loading.
        query.apply("patient_id IN (SELECT real_patient.id FROM rehab_patient real_patient "
                        + "WHERE real_patient.tenant_id={0} AND real_patient.data_origin={1} AND real_patient.deleted=0)",
                TenantContextHolder.getRequiredTenantId(), RehabPatientDataOrigin.REAL);
        return query;
    }
}
