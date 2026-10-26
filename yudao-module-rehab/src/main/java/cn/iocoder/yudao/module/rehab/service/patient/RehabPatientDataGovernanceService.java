package cn.iocoder.yudao.module.rehab.service.patient;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientDataGovernanceReqVO;
import cn.iocoder.yudao.module.rehab.enums.RehabPatientDataOrigin;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.*;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. */
@Service
public class RehabPatientDataGovernanceService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private RehabDataPermissionService dataPermissionService;
    @Resource private PermissionApi permissionApi;
    @Resource private RehabAuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public String changeOrigin(RehabPatientDataGovernanceReqVO request, Long operatorUserId) {
        if (operatorUserId == null
                || (!dataPermissionService.isSuperAdmin(operatorUserId) && !dataPermissionService.isTenantAdmin(operatorUserId))
                || !permissionApi.hasAnyPermissions(operatorUserId, RehabPatientDataOrigin.PERMISSION)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        if (request == null || request.getPatientId() == null || request.getPatientId() <= 0
                || StrUtil.isBlank(request.getConfirmPatientNo()) || StrUtil.length(request.getConfirmPatientNo()) > 64
                || StrUtil.isBlank(request.getReason()) || request.getReason().trim().length() < 5
                || request.getReason().length() > 256) {
            throw exception(PATIENT_DATA_CONFIRMATION_REQUIRED);
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT patient_no,data_origin FROM rehab_patient WHERE tenant_id=? AND id=? AND deleted=FALSE FOR UPDATE",
                tenantId, request.getPatientId());
        if (rows.size() != 1) {
            throw exception(PATIENT_NOT_EXISTS);
        }
        Map<String, Object> patient = rows.get(0);
        if (!request.getConfirmPatientNo().trim().equals(patient.get("patient_no"))) {
            throw exception(PATIENT_DATA_CONFIRMATION_REQUIRED);
        }
        String before = String.valueOf(patient.get("data_origin"));
        if (!before.equals(request.getExpectedDataOrigin())) {
            throw exception(PATIENT_DATA_ORIGIN_CONFLICT);
        }
        String after = nextOrigin(before, request.getOperation());
        int updated = jdbcTemplate.update("UPDATE rehab_patient SET data_origin=?,update_time=NOW() "
                        + "WHERE tenant_id=? AND id=? AND data_origin=? AND deleted=FALSE",
                after, tenantId, request.getPatientId(), before);
        if (updated != 1) {
            throw exception(PATIENT_DATA_ORIGIN_CONFLICT);
        }
        auditLogService.createAuditLog("patient", request.getPatientId(), request.getOperation(), operatorUserId,
                "data-governance", Collections.singletonMap("dataOrigin", before),
                Collections.singletonMap("dataOrigin", after), "success", request.getReason().trim());
        return after;
    }

    private String nextOrigin(String current, String operation) {
        if (RehabPatientDataOrigin.REAL.equals(current) && "MARK_SYNTHETIC".equals(operation)) {
            return RehabPatientDataOrigin.SYNTHETIC;
        }
        if (RehabPatientDataOrigin.SYNTHETIC.equals(current) && "ARCHIVE_SYNTHETIC".equals(operation)) {
            return RehabPatientDataOrigin.ARCHIVED;
        }
        if (RehabPatientDataOrigin.ARCHIVED.equals(current) && "RESTORE_SYNTHETIC".equals(operation)) {
            return RehabPatientDataOrigin.SYNTHETIC;
        }
        throw exception(PATIENT_DATA_ORIGIN_INVALID);
    }
}
