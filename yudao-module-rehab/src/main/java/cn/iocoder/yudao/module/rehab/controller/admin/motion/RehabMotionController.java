package cn.iocoder.yudao.module.rehab.controller.admin.motion;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.motion.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionManualEditDO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTrialDO;
import cn.iocoder.yudao.module.rehab.service.motion.MotionFileRules;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionAssessmentService;
import cn.iocoder.yudao.module.rehab.service.motion.RehabMotionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 智能动作评估与量化分析。
 *
 * <p>权限：rehab:motion:{query,create,upload,process,review,sign,export,audit}；
 * 患者级数据权限、文员写入限制与租户隔离在 service 层统一校验。</p>
 */
@Tag(name = "管理后台 - 智能动作评估")
@RestController
@RequestMapping("/rehab/motion")
@Validated
public class RehabMotionController {

    @Resource
    private RehabMotionAssessmentService assessmentService;
    @Resource
    private RehabMotionReviewService reviewService;

    // ========== 评估 ==========

    @PostMapping("/create")
    @Operation(summary = "创建动作评估")
    @PreAuthorize("@ss.hasPermission('rehab:motion:create')")
    public CommonResult<Long> create(@Valid @RequestBody RehabMotionSaveReqVO req) {
        return success(assessmentService.create(req, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新动作评估")
    @PreAuthorize("@ss.hasPermission('rehab:motion:create')")
    public CommonResult<Boolean> update(@Valid @RequestBody RehabMotionSaveReqVO req) {
        assessmentService.update(req, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除动作评估（未签署）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:create')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        assessmentService.delete(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "动作评估分页")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<PageResult<RehabMotionAssessmentRespVO>> page(@Valid RehabMotionPageReqVO req) {
        return success(assessmentService.page(req, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "动作评估详情")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<RehabMotionAssessmentRespVO> get(@RequestParam("id") Long id) {
        return success(assessmentService.get(id, getLoginUserId()));
    }

    @GetMapping("/list-by-patient")
    @Operation(summary = "患者的动作评估列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<RehabMotionAssessmentRespVO>> listByPatient(@RequestParam("patientId") Long patientId) {
        return success(assessmentService.listByPatient(patientId, getLoginUserId()));
    }

    // ========== 文件 ==========

    @GetMapping("/upload-policy")
    @Operation(summary = "上传策略（白名单、单文件上限），供前端自动过滤与逐个上传")
    @PreAuthorize("@ss.hasPermission('rehab:motion:upload')")
    public CommonResult<Map<String, Object>> uploadPolicy() {
        return success(MotionFileRules.policy());
    }

    @PostMapping("/file/upload")
    @Operation(summary = "上传单个 OpenCap 导出文件（保留相对路径）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:upload')")
    public CommonResult<RehabMotionFileRespVO> upload(@RequestParam("assessmentId") Long assessmentId,
                                                      @RequestParam("relativePath") String relativePath,
                                                      @RequestPart("file") MultipartFile file) {
        return success(assessmentService.upload(assessmentId, relativePath, file, getLoginUserId()));
    }

    @GetMapping("/file/list")
    @Operation(summary = "文件列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<RehabMotionFileRespVO>> listFiles(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.listFiles(assessmentId, getLoginUserId()));
    }

    @DeleteMapping("/file/delete")
    @Operation(summary = "删除文件（未签署）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:upload')")
    public CommonResult<Boolean> deleteFile(@RequestParam("id") Long id) {
        assessmentService.deleteFile(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/file/download")
    @Operation(summary = "下载文件（视频需患者同意；留痕）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:export')")
    public void download(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        write(response, assessmentService.download(id, getLoginUserId()));
    }

    // ========== Trial / 人工输入 ==========

    @GetMapping("/trial/list")
    @Operation(summary = "Trial 列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<RehabMotionTrialDO>> listTrials(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.listTrials(assessmentId, getLoginUserId()));
    }

    @PostMapping("/trial/save")
    @Operation(summary = "保存 Trial 映射（测试/侧别/尝试序号）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:create')")
    public CommonResult<Boolean> saveTrials(@Valid @RequestBody RehabMotionTrialSaveReqVO req) {
        assessmentService.saveTrials(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/trial/manual")
    @Operation(summary = "Trial 人工字段（疼痛、有效性、人工判定）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<Boolean> updateTrialManual(@Valid @RequestBody RehabMotionTrialManualReqVO req) {
        assessmentService.updateTrialManual(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/manual-inputs")
    @Operation(summary = "人工输入（清除测试、YBT、TJA、LESS、NASM 观察）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<Boolean> saveManualInputs(@Valid @RequestBody RehabMotionManualInputsReqVO req) {
        assessmentService.saveManualInputs(req, getLoginUserId());
        return success(true);
    }

    // ========== 处理 / 任务 ==========

    @PostMapping("/process")
    @Operation(summary = "发起分析（异步，幂等键）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:process')")
    public CommonResult<RehabMotionTaskRespVO> process(@Valid @RequestBody RehabMotionProcessReqVO req) {
        return success(RehabMotionAssessmentService.toTaskResp(assessmentService.process(req, getLoginUserId())));
    }

    @PostMapping("/opencap/trials")
    @Operation(summary = "读取 OpenCap 会话中的 Trial")
    @PreAuthorize("@ss.hasPermission('rehab:motion:process')")
    public CommonResult<List<Map<String, Object>>> opencapTrials(@Valid @RequestBody RehabMotionOpencapReqVO req) {
        return success(assessmentService.opencapTrials(req, getLoginUserId()));
    }

    @GetMapping("/task/list")
    @Operation(summary = "任务列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<RehabMotionTaskRespVO>> listTasks(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.listTasks(assessmentId, getLoginUserId()));
    }

    @GetMapping("/task/get")
    @Operation(summary = "任务进度")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<RehabMotionTaskRespVO> getTask(@RequestParam("id") Long id) {
        return success(assessmentService.getTask(id, getLoginUserId()));
    }

    @PostMapping("/task/cancel")
    @Operation(summary = "取消任务")
    @PreAuthorize("@ss.hasPermission('rehab:motion:process')")
    public CommonResult<Boolean> cancelTask(@RequestParam("id") Long id) {
        assessmentService.cancelTask(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/task/retry")
    @Operation(summary = "重试失败/已取消任务（从失败步骤继续）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:process')")
    public CommonResult<RehabMotionTaskRespVO> retryTask(@RequestParam("id") Long id,
                                                         @RequestParam(value = "idempotencyKey", required = false)
                                                         String idempotencyKey) {
        return success(RehabMotionAssessmentService.toTaskResp(
                assessmentService.retryTask(id, idempotencyKey, getLoginUserId())));
    }

    // ========== 结果 / 审核 ==========

    @GetMapping("/result")
    @Operation(summary = "结果聚合：质控、Trial、评分、规则证据、指标、AI 草稿、签署前置条件")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<Map<String, Object>> result(@RequestParam("assessmentId") Long assessmentId) {
        return success(reviewService.result(assessmentId, getLoginUserId()));
    }

    @GetMapping("/series")
    @Operation(summary = "关节角度曲线（显示用降采样）")
    @Parameter(name = "trialName", description = "OpenCap trial 名；为空时返回可用列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<Map<String, Object>> series(@RequestParam("assessmentId") Long assessmentId,
                                                    @RequestParam(value = "trialName", required = false) String trialName) {
        return success(reviewService.series(assessmentId, trialName, getLoginUserId()));
    }

    @PostMapping("/score/review")
    @Operation(summary = "评分审核（确认/修改/不适用；修改须填写原因）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<Boolean> reviewScore(@Valid @RequestBody RehabMotionScoreReviewReqVO req) {
        reviewService.reviewScore(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/ai/review")
    @Operation(summary = "接受/驳回 AI 草稿（不影响任何分数）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<Boolean> reviewAi(@Valid @RequestBody RehabMotionAiReviewReqVO req) {
        reviewService.reviewAiDraft(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/ai/regenerate")
    @Operation(summary = "重新生成 AI 草稿（异步）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<RehabMotionTaskRespVO> regenerateAi(@RequestParam("assessmentId") Long assessmentId,
                                                            @RequestParam(value = "idempotencyKey", required = false)
                                                            String idempotencyKey) {
        return success(RehabMotionAssessmentService.toTaskResp(
                reviewService.regenerateAi(assessmentId, idempotencyKey, getLoginUserId())));
    }

    @PostMapping("/sign")
    @Operation(summary = "治疗师签署（生成治疗师版/患者版报告与 PDF 任务）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:sign')")
    public CommonResult<Boolean> sign(@Valid @RequestBody RehabMotionSignReqVO req) {
        reviewService.sign(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/amend")
    @Operation(summary = "发起修订（须填写原因；旧报告保留为已作废）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:sign')")
    public CommonResult<Boolean> amend(@Valid @RequestBody RehabMotionAmendReqVO req) {
        reviewService.amend(req, getLoginUserId());
        return success(true);
    }

    // ========== 报告 ==========

    @GetMapping("/report/list")
    @Operation(summary = "报告版本列表")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<Map<String, Object>>> reports(@RequestParam("assessmentId") Long assessmentId) {
        return success(reviewService.reports(assessmentId, getLoginUserId()));
    }

    @GetMapping("/report/get")
    @Operation(summary = "报告 JSON（网页版）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:export')")
    public CommonResult<Map<String, Object>> report(@RequestParam("id") Long id) {
        return success(reviewService.reportContent(id, getLoginUserId()));
    }

    @GetMapping("/report/pdf")
    @Operation(summary = "下载报告 PDF（留痕）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:export')")
    public void reportPdf(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        write(response, reviewService.reportPdf(id, getLoginUserId()));
    }

    // ========== 对比 / 趋势 / 留痕 / 协议 ==========

    @GetMapping("/compare")
    @Operation(summary = "初评/复评对比（不做“改善/恶化”判断）")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<Map<String, Object>> compare(@RequestParam("baseId") Long baseId,
                                                     @RequestParam("currentId") Long currentId) {
        return success(reviewService.compare(baseId, currentId, getLoginUserId()));
    }

    @GetMapping("/trend")
    @Operation(summary = "患者动作评估趋势")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<Map<String, Object>> trend(@RequestParam("patientId") Long patientId) {
        return success(reviewService.trend(patientId, getLoginUserId()));
    }

    @GetMapping("/manual-edits")
    @Operation(summary = "人工修改记录")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<List<RehabMotionManualEditDO>> manualEdits(@RequestParam("assessmentId") Long assessmentId) {
        return success(reviewService.manualEdits(assessmentId, getLoginUserId()));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "审计日志")
    @PreAuthorize("@ss.hasPermission('rehab:motion:audit')")
    public CommonResult<List<?>> auditLogs(@RequestParam("assessmentId") Long assessmentId) {
        return success(reviewService.auditLogs(assessmentId, getLoginUserId()));
    }

    @GetMapping("/protocols")
    @Operation(summary = "协议与规则版本")
    @PreAuthorize("@ss.hasPermission('rehab:motion:query')")
    public CommonResult<Map<String, Object>> protocols() {
        return success(reviewService.protocols());
    }

    /** 流式输出附件（不整体读入内存）；文件名 RFC 5987 编码，禁止内容嗅探。 */
    static void write(HttpServletResponse response, RehabMotionAssessmentService.Download d) throws IOException {
        response.setContentType(d.contentType);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encode(d.fileName));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLengthLong(d.file.length());
        InputStream in = new FileInputStream(d.file);
        try {
            OutputStream out = response.getOutputStream();
            IoUtil.copy(in, out);
            out.flush();
        } finally {
            IoUtil.close(in);
        }
    }

    static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return "download";
        }
    }

}
