package cn.iocoder.yudao.module.rehab.controller.admin.assessment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.assessment.vo.*;
import cn.iocoder.yudao.module.rehab.service.assessment.RehabAssessmentAttachmentFile;
import cn.iocoder.yudao.module.rehab.service.assessment.RehabAssessmentService;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复评估")
@RestController
@RequestMapping("/rehab/assessment")
@Validated
public class RehabAssessmentController {

    @Resource
    private RehabAssessmentService assessmentService;

    @GetMapping("/page")
    @Operation(summary = "获得评估分页")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:view')")
    public CommonResult<PageResult<RehabAssessmentRespVO>> getAssessmentPage(@Valid RehabAssessmentPageReqVO reqVO) {
        return success(assessmentService.getAssessmentPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得评估详情")
    @Parameter(name = "id", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public CommonResult<RehabAssessmentDetailRespVO> getAssessment(@RequestParam("id") Long id) {
        return success(assessmentService.getAssessment(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建评估")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:create')")
    public CommonResult<RehabAssessmentCreateRespVO> createAssessment(@Valid @RequestBody RehabAssessmentCreateReqVO reqVO) {
        return success(assessmentService.createAssessment(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新评估")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:update')")
    public CommonResult<Boolean> updateAssessment(@Valid @RequestBody RehabAssessmentUpdateReqVO reqVO) {
        assessmentService.updateAssessment(reqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除评估")
    @Parameter(name = "id", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:delete')")
    public CommonResult<Boolean> deleteAssessment(@RequestParam("id") Long id) {
        assessmentService.deleteAssessment(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/archive")
    @Operation(summary = "归档评估")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:archive')")
    public CommonResult<Boolean> archiveAssessment(@Valid @RequestBody RehabAssessmentArchiveReqVO reqVO) {
        assessmentService.archiveAssessment(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/module-data")
    @Operation(summary = "获得评估模块数据")
    @Parameter(name = "assessmentId", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public CommonResult<List<RehabAssessmentModuleDataRespVO>> getModuleDataList(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.getModuleDataList(assessmentId, getLoginUserId()));
    }

    @PostMapping("/module-data/save")
    @Operation(summary = "保存评估模块数据")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:update')")
    public CommonResult<RehabAssessmentModuleDataRespVO> saveModuleData(@Valid @RequestBody RehabAssessmentModuleDataSaveReqVO reqVO) {
        return success(assessmentService.saveModuleData(reqVO, getLoginUserId()));
    }

    @GetMapping("/sfma/protocol")
    @Operation(summary = "获得 SFMA 原书版分解评估协议")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public CommonResult<Map<String, Object>> getSfmaBookProtocol() {
        return success(assessmentService.getSfmaBookProtocol());
    }

    @GetMapping("/attachments")
    @Operation(summary = "获得评估附件列表")
    @Parameter(name = "assessmentId", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public CommonResult<List<RehabAssessmentAttachmentRespVO>> getAttachments(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.getAttachmentList(assessmentId, getLoginUserId()));
    }

    @PostMapping("/upload-attachment")
    @Operation(summary = "上传评估附件")
    @PreAuthorize("@ss.hasPermission('rehab:assessment:update')")
    public CommonResult<RehabAssessmentAttachmentRespVO> uploadAttachment(@RequestParam("assessmentId") Long assessmentId,
                                                                          @RequestParam("moduleType") String moduleType,
                                                                          @RequestPart("file") MultipartFile file) {
        return success(assessmentService.uploadAttachment(assessmentId, moduleType, file, getLoginUserId()));
    }

    @GetMapping("/download-attachment")
    @Operation(summary = "下载评估附件")
    @Parameter(name = "id", description = "附件编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public void downloadAttachment(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        RehabAssessmentAttachmentFile file = assessmentService.downloadAttachment(id, getLoginUserId());
        response.setHeader("X-Content-Type", file.getFileType());
        ServletUtils.writeAttachment(response, file.getFileName(), file.getContent());
    }

    @GetMapping("/operation-log")
    @Operation(summary = "获得评估操作日志")
    @Parameter(name = "assessmentId", description = "评估编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assessment:detail')")
    public CommonResult<List<RehabAssessmentOperationLogRespVO>> getOperationLog(@RequestParam("assessmentId") Long assessmentId) {
        return success(assessmentService.getOperationLogList(assessmentId, getLoginUserId()));
    }

}
