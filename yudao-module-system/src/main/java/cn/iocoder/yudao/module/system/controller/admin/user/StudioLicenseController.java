package cn.iocoder.yudao.module.system.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.framework.license.StudioLicenseOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Studio-only read API. No owner approval, signing or arbitrary tenant_id.
 * The authenticated tenant is resolved by the existing Java tenant context.
 */
@Tag(name = "管理后台 - 工作室许可与员工席位")
@RestController
@RequestMapping("/system/studio-license")
public class StudioLicenseController {
    private final StudioLicenseOverviewService service;

    public StudioLicenseController(StudioLicenseOverviewService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "查看当前工作室签名许可及员工席位占用")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<Map<String, Object>> overview() {
        return success(service.readCurrentStudio());
    }
}
