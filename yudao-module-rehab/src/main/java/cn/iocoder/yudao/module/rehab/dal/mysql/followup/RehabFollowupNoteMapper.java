package cn.iocoder.yudao.module.rehab.dal.mysql.followup;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.followup.RehabFollowupNoteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface RehabFollowupNoteMapper extends BaseMapperX<RehabFollowupNoteDO> {

    default PageResult<RehabFollowupNoteDO> selectPage(Integer pageNo, Integer pageSize, Long patientId,
                                                       Collection<Long> visiblePatientIds) {
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        LambdaQueryWrapperX<RehabFollowupNoteDO> query = new LambdaQueryWrapperX<RehabFollowupNoteDO>()
                .eqIfPresent(RehabFollowupNoteDO::getPatientId, patientId)
                .orderByDesc(RehabFollowupNoteDO::getCreateTime)
                .orderByDesc(RehabFollowupNoteDO::getId);
        if (visiblePatientIds != null) {
            query.in(RehabFollowupNoteDO::getPatientId, visiblePatientIds);
        }
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        return selectPage(pageParam, query);
    }

    default List<RehabFollowupNoteDO> selectRecentByPatientId(Long patientId, Integer limit) {
        List<RehabFollowupNoteDO> list = selectList(new LambdaQueryWrapperX<RehabFollowupNoteDO>()
                .eq(RehabFollowupNoteDO::getPatientId, patientId)
                .orderByDesc(RehabFollowupNoteDO::getCreateTime)
                .orderByDesc(RehabFollowupNoteDO::getId));
        if (list == null) {
            return Collections.emptyList();
        }
        if (limit == null || limit <= 0 || list.size() <= limit) {
            return list;
        }
        return list.subList(0, limit);
    }
}
