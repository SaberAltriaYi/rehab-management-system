package cn.iocoder.yudao.module.rehab.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.*;
import cn.iocoder.yudao.module.rehab.service.task.RehabExerciseTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 训练任务")
@RestController
@RequestMapping("/rehab/task")
@Validated
public class RehabExerciseTaskController {

    @Resource
    private RehabExerciseTaskService taskService;

    @GetMapping("/page")
    @Operation(summary = "获得任务分页")
    @PreAuthorize("@ss.hasPermission('rehab:task:view')")
    public CommonResult<PageResult<RehabExerciseTaskRespVO>> getTaskPage(@Valid RehabExerciseTaskPageReqVO reqVO) {
        return success(taskService.getTaskPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得任务详情")
    @Parameter(name = "id", description = "任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:task:view')")
    public CommonResult<RehabExerciseTaskRespVO> getTask(@RequestParam("id") Long id) {
        return success(taskService.getTask(id, getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建任务")
    @PreAuthorize("@ss.hasPermission('rehab:task:create')")
    public CommonResult<Long> createTask(@Valid @RequestBody RehabExerciseTaskCreateReqVO reqVO) {
        return success(taskService.createTask(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    @PreAuthorize("@ss.hasPermission('rehab:task:update')")
    public CommonResult<Boolean> updateTask(@Valid @RequestBody RehabExerciseTaskUpdateReqVO reqVO) {
        taskService.updateTask(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/sort")
    @Operation(summary = "排序任务")
    @PreAuthorize("@ss.hasPermission('rehab:task:sort')")
    public CommonResult<Boolean> sortTasks(@Valid @RequestBody RehabExerciseTaskSortReqVO reqVO) {
        taskService.sortTasks(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/disable")
    @Operation(summary = "停用任务")
    @PreAuthorize("@ss.hasPermission('rehab:task:disable')")
    public CommonResult<Boolean> disableTask(@Valid @RequestBody RehabExerciseTaskToggleReqVO reqVO) {
        taskService.disableTask(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/enable")
    @Operation(summary = "启用任务")
    @PreAuthorize("@ss.hasPermission('rehab:task:update')")
    public CommonResult<Boolean> enableTask(@Valid @RequestBody RehabExerciseTaskToggleReqVO reqVO) {
        taskService.enableTask(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/list-by-plan")
    @Operation(summary = "按计划获取任务列表")
    @Parameter(name = "planId", description = "计划编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:task:view')")
    public CommonResult<List<RehabExerciseTaskRespVO>> getTaskListByPlan(@RequestParam("planId") Long planId) {
        return success(taskService.getTaskListByPlan(planId, getLoginUserId()));
    }

}
