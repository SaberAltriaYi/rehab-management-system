package cn.iocoder.yudao.module.rehab.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.task.vo.*;
import cn.iocoder.yudao.module.rehab.dal.dataobject.task.RehabExerciseTaskDO;

import java.util.List;

public interface RehabExerciseTaskService {

    PageResult<RehabExerciseTaskRespVO> getTaskPage(RehabExerciseTaskPageReqVO reqVO, Long operatorUserId);

    RehabExerciseTaskRespVO getTask(Long id, Long operatorUserId);

    Long createTask(RehabExerciseTaskCreateReqVO reqVO, Long operatorUserId);

    void updateTask(RehabExerciseTaskUpdateReqVO reqVO, Long operatorUserId);

    void sortTasks(RehabExerciseTaskSortReqVO reqVO, Long operatorUserId);

    void disableTask(RehabExerciseTaskToggleReqVO reqVO, Long operatorUserId);

    void enableTask(RehabExerciseTaskToggleReqVO reqVO, Long operatorUserId);

    List<RehabExerciseTaskRespVO> getTaskListByPlan(Long planId, Long operatorUserId);

    RehabExerciseTaskDO validateTaskReadable(Long taskId, Long operatorUserId);

}
