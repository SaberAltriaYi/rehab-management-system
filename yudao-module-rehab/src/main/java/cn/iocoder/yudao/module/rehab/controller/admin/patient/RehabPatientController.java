package cn.iocoder.yudao.module.rehab.controller.admin.patient;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.*;
import cn.iocoder.yudao.module.rehab.service.patient.RehabPatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复患者")
@RestController
@RequestMapping("/rehab/patient")
@Validated
public class RehabPatientController {

    @Resource
    private RehabPatientService patientService;

    @GetMapping("/page")
    @Operation(summary = "获得患者分页")
    @PreAuthorize("@ss.hasPermission('rehab:patient:view')")
    public CommonResult<PageResult<RehabPatientRespVO>> getPatientPage(@Valid RehabPatientPageReqVO pageReqVO) {
        return success(patientService.getPatientPage(pageReqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得患者详情")
    @Parameter(name = "id", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:patient:detail')")
    public CommonResult<RehabPatientDetailRespVO> getPatient(@RequestParam("id") Long id) {
        return success(patientService.getPatientDetail(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建患者")
    @PreAuthorize("@ss.hasPermission('rehab:patient:create')")
    public CommonResult<RehabPatientCreateRespVO> createPatient(@Valid @RequestBody RehabPatientCreateReqVO reqVO) {
        return success(patientService.createPatient(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新患者")
    @PreAuthorize("@ss.hasPermission('rehab:patient:update')")
    public CommonResult<Boolean> updatePatient(@Valid @RequestBody RehabPatientUpdateReqVO reqVO) {
        patientService.updatePatient(reqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除患者")
    @Parameter(name = "id", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:patient:delete')")
    public CommonResult<Boolean> deletePatient(@RequestParam("id") Long id) {
        patientService.deletePatient(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/export")
    @Operation(summary = "导出患者")
    @PreAuthorize("@ss.hasPermission('rehab:patient:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportPatient(@Valid RehabPatientPageReqVO reqVO, HttpServletResponse response) throws IOException {
        List<RehabPatientExportRespVO> list = patientService.getPatientExportList(reqVO, getLoginUserId());
        ExcelUtils.write(response, "康复患者.xls", "数据", RehabPatientExportRespVO.class, list);
    }

    @PostMapping("/bind-crm")
    @Operation(summary = "绑定 CRM 客户")
    @PreAuthorize("@ss.hasPermission('rehab:patient:bind-crm')")
    public CommonResult<RehabPatientCrmBindingRespVO> bindCrm(@Valid @RequestBody RehabPatientBindCrmReqVO reqVO) {
        return success(patientService.bindCrm(reqVO, getLoginUserId()));
    }

    @PostMapping("/unbind-crm")
    @Operation(summary = "解绑 CRM 客户")
    @PreAuthorize("@ss.hasPermission('rehab:patient:bind-crm')")
    public CommonResult<RehabPatientCrmBindingRespVO> unbindCrm(@Valid @RequestBody RehabPatientUnbindCrmReqVO reqVO) {
        return success(patientService.unbindCrm(reqVO, getLoginUserId()));
    }

    @GetMapping("/crm-binding")
    @Operation(summary = "获得 CRM 绑定状态")
    @Parameter(name = "id", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:patient:bind-crm')")
    public CommonResult<RehabPatientCrmBindingRespVO> getCrmBinding(@RequestParam("id") Long patientId) {
        return success(patientService.getCrmBinding(patientId, getLoginUserId()));
    }

    @GetMapping("/member-binding")
    @Operation(summary = "获得患者会员绑定状态")
    @Parameter(name = "id", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:patient:detail')")
    public CommonResult<RehabPatientMemberBindingRespVO> getMemberBinding(@RequestParam("id") Long patientId) {
        return success(patientService.getMemberBinding(patientId, getLoginUserId()));
    }

    @PostMapping("/check-crm-conflict")
    @Operation(summary = "检查 CRM 绑定冲突")
    @PreAuthorize("@ss.hasPermission('rehab:patient:bind-crm')")
    public CommonResult<RehabCrmConflictCheckRespVO> checkCrmConflict(@Valid @RequestBody RehabPatientCheckCrmConflictReqVO reqVO) {
        return success(patientService.checkCrmConflict(reqVO));
    }

    @PostMapping("/assign-therapist")
    @Operation(summary = "分配治疗师")
    @PreAuthorize("@ss.hasPermission('rehab:patient:assign')")
    public CommonResult<Boolean> assignTherapist(@Valid @RequestBody RehabPatientAssignReqVO reqVO) {
        patientService.assignTherapist(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/transfer-therapist")
    @Operation(summary = "转交治疗师")
    @PreAuthorize("@ss.hasPermission('rehab:patient:transfer')")
    public CommonResult<Boolean> transferTherapist(@Valid @RequestBody RehabPatientTransferReqVO reqVO) {
        patientService.transferTherapist(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/assignment-history")
    @Operation(summary = "获得患者分配历史")
    @Parameter(name = "patientId", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:assignment:view')")
    public CommonResult<List<RehabTherapistAssignmentRespVO>> getAssignmentHistory(@RequestParam("patientId") Long patientId) {
        return success(patientService.getAssignmentHistory(patientId, getLoginUserId()));
    }

    @GetMapping("/operation-log")
    @Operation(summary = "获得患者操作日志")
    @Parameter(name = "patientId", description = "患者编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:patient:detail')")
    public CommonResult<List<RehabPatientOperationLogRespVO>> getOperationLog(@RequestParam("patientId") Long patientId) {
        return success(patientService.getOperationLogList(patientId, getLoginUserId()));
    }

}
