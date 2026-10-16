package cn.iocoder.yudao.module.crm.controller.admin.department;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.crm.service.department.CrmDepartmentService;
import cn.iocoder.yudao.module.crm.service.department.CrmDepartmentService.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 机构共享客户目录、部门认领与双向转接接口。
 * Copyright (c) 2026 杨玺龙。保留上游项目许可。
 */
@RestController
@RequestMapping("/crm/department")
@Validated
public class CrmDepartmentController {

    @Resource private CrmDepartmentService service;

    @GetMapping("/directory")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<PageResult<DirectoryItem>> directory(@RequestParam(required = false) @Size(max = 100) String name,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) Integer pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return success(service.directory(name, pageNo, pageSize));
    }

    @GetMapping("/workspaces")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<List<Workspace>> workspaces() {
        return success(service.workspaces());
    }

    @PutMapping("/workspace")
    @PreAuthorize("@ss.hasPermission('system:dept:update')")
    public CommonResult<Boolean> configure(@Valid @RequestBody WorkspaceReq req) {
        service.configureWorkspace(req.getDeptId(), req.getDisplayName(), req.getEnabled());
        return success(true);
    }

    @GetMapping("/cases")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<PageResult<CaseItem>> cases(@RequestParam(defaultValue = "1") @Min(1) @Max(1000000) Integer pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return success(service.cases(getLoginUserId(), pageNo, pageSize));
    }

    @PostMapping("/claim")
    @PreAuthorize("@ss.hasPermission('crm:customer:create')")
    public CommonResult<Long> claim(@Valid @RequestBody ClaimReq req) {
        return success(service.claim(req.getCustomerId(), getLoginUserId()));
    }

    @PutMapping("/note")
    @PreAuthorize("@ss.hasPermission('crm:customer:update')")
    public CommonResult<Boolean> saveNote(@Valid @RequestBody NoteReq req) {
        service.saveNote(req.getCaseId(), req.getContent(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/transfer")
    @PreAuthorize("@ss.hasPermission('crm:customer:update')")
    public CommonResult<Long> transfer(@Valid @RequestBody TransferReq req) {
        return success(service.requestTransfer(req.getCaseId(), req.getToDeptId(), req.getReason(), getLoginUserId()));
    }

    @GetMapping("/transfers/incoming")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<List<TransferItem>> incoming() {
        return success(service.incoming(getLoginUserId()));
    }

    @PutMapping("/transfers/resolve")
    @PreAuthorize("@ss.hasPermission('crm:customer:update')")
    public CommonResult<Boolean> resolve(@Valid @RequestBody ResolveReq req) {
        service.resolveTransfer(req.getTransferId(), req.getAccept(), getLoginUserId());
        return success(true);
    }

    @Data public static class WorkspaceReq {
        @NotNull private Long deptId;
        @NotBlank @Size(max = 100) private String displayName;
        @NotNull private Boolean enabled;
    }
    @Data public static class ClaimReq { @NotNull private Long customerId; }
    @Data public static class NoteReq {
        @NotNull private Long caseId;
        @NotNull @Size(max = 4000) private String content;
    }
    @Data public static class TransferReq {
        @NotNull private Long caseId;
        @NotNull private Long toDeptId;
        @Size(max = 200) private String reason;
    }
    @Data public static class ResolveReq {
        @NotNull private Long transferId;
        @NotNull private Boolean accept;
    }
}
