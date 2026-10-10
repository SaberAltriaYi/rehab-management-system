package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionTaskDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 动作评估异步任务 Mapper。
 * 认领采用条件更新（乐观锁）：多实例部署时同一任务同一时刻只会被一个节点执行。
 */
@Mapper
public interface RehabMotionTaskMapper extends BaseMapperX<RehabMotionTaskDO> {

    default List<RehabMotionTaskDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabMotionTaskDO>()
                .eq(RehabMotionTaskDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabMotionTaskDO::getId));
    }

    default RehabMotionTaskDO selectByIdempotencyKey(String key) {
        return selectOne(RehabMotionTaskDO::getIdempotencyKey, key);
    }

    default List<RehabMotionTaskDO> selectActiveByAssessment(Long assessmentId, Collection<String> activeStates) {
        return selectList(new LambdaQueryWrapperX<RehabMotionTaskDO>()
                .eq(RehabMotionTaskDO::getAssessmentId, assessmentId)
                .in(RehabMotionTaskDO::getState, activeStates));
    }

    /** 调用方须处于 TenantUtils.executeIgnore 中（跨租户轮询）。 */
    default List<RehabMotionTaskDO> selectDue(Collection<String> runnableStates, LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<RehabMotionTaskDO>()
                .in(RehabMotionTaskDO::getState, runnableStates)
                .and(w -> w.isNull(RehabMotionTaskDO::getNextRunTime).or().le(RehabMotionTaskDO::getNextRunTime, now))
                .and(w -> w.isNull(RehabMotionTaskDO::getLockUntil).or().lt(RehabMotionTaskDO::getLockUntil, now))
                .orderByAsc(RehabMotionTaskDO::getNextRunTime)
                .orderByAsc(RehabMotionTaskDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    /** @return 1 = 认领成功；0 = 已被其它节点认领或状态已变化 */
    default int claim(Long id, String expectedState, String owner, LocalDateTime now, LocalDateTime lockUntil) {
        return update(null, new LambdaUpdateWrapper<RehabMotionTaskDO>()
                .set(RehabMotionTaskDO::getLockOwner, owner)
                .set(RehabMotionTaskDO::getLockUntil, lockUntil)
                .eq(RehabMotionTaskDO::getId, id)
                .eq(RehabMotionTaskDO::getState, expectedState)
                .and(w -> w.isNull(RehabMotionTaskDO::getLockUntil).or().lt(RehabMotionTaskDO::getLockUntil, now)));
    }

    /** 释放锁并写入新状态；仅当锁仍属于本节点时生效。 */
    default int release(RehabMotionTaskDO update, String owner) {
        return update(update, new LambdaUpdateWrapper<RehabMotionTaskDO>()
                .set(RehabMotionTaskDO::getLockOwner, null)
                .set(RehabMotionTaskDO::getLockUntil, null)
                .eq(RehabMotionTaskDO::getId, update.getId())
                .eq(RehabMotionTaskDO::getLockOwner, owner));
    }

}
