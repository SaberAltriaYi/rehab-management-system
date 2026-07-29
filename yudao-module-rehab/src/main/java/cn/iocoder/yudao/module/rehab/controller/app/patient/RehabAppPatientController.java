package cn.iocoder.yudao.module.rehab.controller.app.patient;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.app.patient.vo.*;
import cn.iocoder.yudao.module.rehab.service.app.RehabAppPatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "患者端 APP - 康复服务")
@RestController
@RequestMapping("/app-patient")
@Validated
public class RehabAppPatientController {

    @Resource
    private RehabAppPatientService appPatientService;

    @PostMapping("/auth/login")
    @PermitAll
    @Operation(summary = "患者端登录")
    public CommonResult<AppPatientLoginRespVO> login(@RequestBody @Valid AppPatientLoginReqVO reqVO) {
        return success(appPatientService.login(reqVO));
    }

    @PostMapping("/auth/bind")
    @PermitAll
    @Operation(summary = "患者身份绑定")
    public CommonResult<Long> bind(@RequestBody @Valid AppPatientAuthBindReqVO reqVO) {
        return success(appPatientService.bindPatient(reqVO, getLoginUserId()));
    }

    @GetMapping("/home/summary")
    @Operation(summary = "首页摘要")
    public CommonResult<AppPatientHomeSummaryRespVO> getHomeSummary() {
        return success(appPatientService.getHomeSummary(getLoginUserId()));
    }

    @GetMapping("/reports/page")
    @Operation(summary = "报告摘要分页")
    public CommonResult<PageResult<AppPatientReportRespVO>> getReportPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(appPatientService.getReportPage(pageNo, pageSize, getLoginUserId()));
    }

    @GetMapping("/reports/get")
    @Operation(summary = "报告摘要详情")
    public CommonResult<AppPatientReportRespVO> getReport(@RequestParam("id") Long id) {
        return success(appPatientService.getReport(id, getLoginUserId()));
    }

    @GetMapping("/plan/current")
    @Operation(summary = "当前计划")
    public CommonResult<AppPatientCurrentPlanRespVO> getCurrentPlan() {
        return success(appPatientService.getCurrentPlan(getLoginUserId()));
    }

    @GetMapping("/tasks/today")
    @Operation(summary = "今日任务")
    public CommonResult<List<AppPatientTaskRespVO>> getTodayTasks() {
        return success(appPatientService.getTodayTasks(getLoginUserId()));
    }

    @PostMapping("/checkin/create")
    @Operation(summary = "提交打卡")
    public CommonResult<Long> createCheckin(@RequestBody @Valid AppPatientCheckinCreateReqVO reqVO) {
        return success(appPatientService.createCheckin(reqVO, getLoginUserId()));
    }

    @GetMapping("/checkin/history")
    @Operation(summary = "打卡历史")
    public CommonResult<PageResult<AppPatientCheckinHistoryRespVO>> getCheckinHistory(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(appPatientService.getCheckinHistory(pageNo, pageSize, getLoginUserId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "个人信息")
    public CommonResult<AppPatientProfileRespVO> getProfile() {
        return success(appPatientService.getProfile(getLoginUserId()));
    }

    @GetMapping("/notifications/page")
    @Operation(summary = "通知分页")
    public CommonResult<PageResult<AppPatientNotificationRespVO>> getNotificationPage(
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(appPatientService.getNotificationPage(pageNo, pageSize, getLoginUserId()));
    }

    @PostMapping("/notifications/read")
    @Operation(summary = "通知已读")
    public CommonResult<Boolean> readNotification(@RequestBody @Valid AppPatientNotificationReadReqVO reqVO) {
        appPatientService.markNotificationRead(reqVO.getId(), getLoginUserId());
        return success(true);
    }

    @GetMapping("/ai/summary/latest")
    @Operation(summary = "最新 AI 摘要")
    public CommonResult<AppPatientAiOutputRespVO> getLatestAiSummary() {
        return success(appPatientService.getLatestAiSummary(getLoginUserId()));
    }

    @GetMapping("/ai/followup/latest")
    @Operation(summary = "最新 AI 随访文案")
    public CommonResult<AppPatientAiOutputRespVO> getLatestAiFollowup() {
        return success(appPatientService.getLatestAiFollowup(getLoginUserId()));
    }
}
