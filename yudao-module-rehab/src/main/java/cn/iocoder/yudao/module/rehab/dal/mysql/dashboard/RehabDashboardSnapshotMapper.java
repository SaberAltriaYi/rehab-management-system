package cn.iocoder.yudao.module.rehab.dal.mysql.dashboard;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.dashboard.RehabDashboardSnapshotDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RehabDashboardSnapshotMapper extends BaseMapperX<RehabDashboardSnapshotDO> {

    default RehabDashboardSnapshotDO selectLatestByScope(String ownerScope, Long ownerUserId) {
        List<RehabDashboardSnapshotDO> list = selectList(new LambdaQueryWrapperX<RehabDashboardSnapshotDO>()
                .eq(RehabDashboardSnapshotDO::getOwnerScope, ownerScope)
                .eqIfPresent(RehabDashboardSnapshotDO::getOwnerUserId, ownerUserId)
                .orderByDesc(RehabDashboardSnapshotDO::getSnapshotDate)
                .orderByDesc(RehabDashboardSnapshotDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default RehabDashboardSnapshotDO selectByDateAndScope(LocalDate date, String ownerScope, Long ownerUserId) {
        return selectOne(new LambdaQueryWrapperX<RehabDashboardSnapshotDO>()
                .eq(RehabDashboardSnapshotDO::getSnapshotDate, date)
                .eq(RehabDashboardSnapshotDO::getOwnerScope, ownerScope)
                .eqIfPresent(RehabDashboardSnapshotDO::getOwnerUserId, ownerUserId));
    }

}
