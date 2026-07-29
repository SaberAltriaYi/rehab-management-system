package cn.iocoder.yudao.module.rehab.controller.admin.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.app.vo.*;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.RehabDailyCheckinRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientPageReqVO;
import cn.iocoder.yudao.module.rehab.service.app.RehabAppAdminService;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理端小程序 - 康复工作台")
@RestController
@RequestMapping("/app-admin")
@Validated
public class RehabAppAdminController {

    @Resource
    private RehabAppAdminService appAdminService;

    @PostMapping("/auth/login")
    @PermitAll
    @Operation(summary = "管理端小程序登录")
    public CommonResult<AuthLoginRespVO> login(@RequestBody @Valid AuthLoginReqVO reqVO) {
        return success(appAdminService.login(reqVO));
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "管理端工作台摘要")
    public CommonResult<AppAdminDashboardSummaryRespVO> getDashboardSummary() {
        return success(appAdminService.getDashboardSummary(getLoginUserId()));
    }

    @GetMapping("/patients/my-page")
    @Operation(summary = "我的患者分页")
    public CommonResult<PageResult<AppAdminPatientMiniRespVO>> getMyPatientPage(@Valid RehabPatientPageReqVO reqVO) {
        return success(appAdminService.getMyPatientPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/patients/summary")
    @Operation(summary = "患者摘要")
    public CommonResult<AppAdminPatientSummaryRespVO> getPatientSummary(@RequestParam("id") Long id) {
        return success(appAdminService.getPatientSummary(id, getLoginUserId()));
    }

    @GetMapping("/patients/checkins")
    @Operation(summary = "患者打卡记录分页")
    public CommonResult<PageResult<RehabDailyCheckinRespVO>> getPatientCheckins(@RequestParam("patientId") Long patientId,
                                                                                 @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                                                                                 @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(appAdminService.getPatientCheckins(patientId, pageNo, pageSize, getLoginUserId()));
    }

    @GetMapping("/alerts/page")
    @Operation(summary = "风险提醒分页")
    public CommonResult<PageResult<RehabAlertEventRespVO>> getAlertPage(@Valid RehabAlertEventPageReqVO reqVO) {
        return success(appAdminService.getAlertPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/notifications/page")
    @Operation(summary = "通知分页")
    public CommonResult<PageResult<AppAdminNotificationRespVO>> getNotificationPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(appAdminService.getNotificationPage(pageNo, pageSize, getLoginUserId()));
    }

    @PostMapping("/notifications/read")
    @Operation(summary = "通知已读")
    public CommonResult<Boolean> readNotification(@RequestBody @Valid AppAdminNotificationReadReqVO reqVO) {
        appAdminService.readNotification(reqVO.getId(), getLoginUserId());
        return success(true);
    }

    @PostMapping("/followup-note/create")
    @Operation(summary = "新增随访备注")
    public CommonResult<Long> createFollowupNote(@RequestBody @Valid AppAdminFollowupNoteCreateReqVO reqVO) {
        return success(appAdminService.createFollowupNote(reqVO, getLoginUserId()));
    }

    @GetMapping("/followup-note/page")
    @Operation(summary = "随访备注分页")
    public CommonResult<PageResult<AppAdminFollowupNoteRespVO>> getFollowupNotePage(@Valid AppAdminFollowupNotePageReqVO reqVO) {
        return success(appAdminService.getFollowupNotePage(reqVO, getLoginUserId()));
    }

    @GetMapping("/ai/summary/latest")
    @Operation(summary = "患者最新 AI 摘要")
    public CommonResult<AppAdminAiOutputRespVO> getLatestAiSummary(@RequestParam("patientId") Long patientId) {
        return success(appAdminService.getLatestAiSummary(patientId, getLoginUserId()));
    }

    @GetMapping("/ai/followup/latest")
    @Operation(summary = "患者最新 AI 随访建议")
    public CommonResult<AppAdminAiOutputRespVO> getLatestAiFollowup(@RequestParam("patientId") Long patientId) {
        return success(appAdminService.getLatestAiFollowup(patientId, getLoginUserId()));
    }
}
