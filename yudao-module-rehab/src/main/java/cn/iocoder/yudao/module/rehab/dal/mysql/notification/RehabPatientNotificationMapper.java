package cn.iocoder.yudao.module.rehab.dal.mysql.notification;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.notification.RehabPatientNotificationDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface RehabPatientNotificationMapper extends BaseMapperX<RehabPatientNotificationDO> {

    default PageResult<RehabPatientNotificationDO> selectPageByPatientId(Long patientId, Integer pageNo, Integer pageSize) {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(pageNo);
        pageParam.setPageSize(pageSize);
        LocalDateTime now = LocalDateTime.now();
        return selectPage(pageParam, new LambdaQueryWrapperX<RehabPatientNotificationDO>()
                .eq(RehabPatientNotificationDO::getPatientId, patientId)
                .and(wrapper -> wrapper.isNull(RehabPatientNotificationDO::getVisibleFrom)
                        .or().le(RehabPatientNotificationDO::getVisibleFrom, now))
                .and(wrapper -> wrapper.isNull(RehabPatientNotificationDO::getExpireTime)
                        .or().ge(RehabPatientNotificationDO::getExpireTime, now))
                .orderByDesc(RehabPatientNotificationDO::getCreateTime)
                .orderByDesc(RehabPatientNotificationDO::getId));
    }
}
