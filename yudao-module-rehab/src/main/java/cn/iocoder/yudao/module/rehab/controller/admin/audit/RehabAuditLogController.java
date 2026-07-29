package cn.iocoder.yudao.module.rehab.controller.admin.audit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;
import cn.iocoder.yudao.module.rehab.service.log.RehabAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 审计日志")
@RestController
@RequestMapping("/rehab/audit-log")
@Validated
public class RehabAuditLogController {

    @Resource
    private RehabAuditLogService auditLogService;

    @GetMapping("/page")
    @Operation(summary = "获得审计日志分页")
    @PreAuthorize("@ss.hasPermission('rehab:audit-log:view')")
    public CommonResult<PageResult<RehabAuditLogRespVO>> getPage(@Valid RehabAuditLogPageReqVO reqVO) {
        return success(auditLogService.getAuditLogPage(reqVO, getLoginUserId()));
    }
}
