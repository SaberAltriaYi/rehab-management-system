package cn.iocoder.yudao.module.rehab.controller.admin.notification;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.notification.vo.*;
import cn.iocoder.yudao.module.rehab.service.notification.RehabNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 通知中心")
@RestController
@RequestMapping("/rehab/notification")
@Validated
public class RehabNotificationController {

    @Resource
    private RehabNotificationService notificationService;

    @GetMapping("/page")
    @Operation(summary = "获得通知分页")
    @PreAuthorize("@ss.hasPermission('rehab:notification:view')")
    public CommonResult<PageResult<RehabNotificationRespVO>> getPage(@Valid RehabNotificationPageReqVO reqVO) {
        return success(notificationService.getNotificationPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得通知详情")
    @Parameter(name = "id", description = "通知编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:notification:view')")
    public CommonResult<RehabNotificationRespVO> get(@RequestParam("id") Long id) {
        return success(notificationService.getNotification(id, getLoginUserId()));
    }

    @PostMapping("/read")
    @Operation(summary = "通知已读")
    @PreAuthorize("@ss.hasPermission('rehab:notification:read')")
    public CommonResult<Boolean> read(@Valid @RequestBody RehabNotificationReadReqVO reqVO) {
        notificationService.readNotification(reqVO.getId(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/read-all")
    @Operation(summary = "通知全部已读")
    @PreAuthorize("@ss.hasPermission('rehab:notification:read')")
    public CommonResult<Boolean> readAll() {
        notificationService.readAllNotification(getLoginUserId());
        return success(true);
    }

    @PostMapping("/create")
    @Operation(summary = "创建通知")
    @PreAuthorize("@ss.hasPermission('rehab:notification:send')")
    public CommonResult<Long> create(@Valid @RequestBody RehabNotificationCreateReqVO reqVO) {
        return success(notificationService.createNotification(reqVO, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除通知")
    @Parameter(name = "id", description = "通知编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:notification:send')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        notificationService.deleteNotification(id, getLoginUserId());
        return success(true);
    }

}
