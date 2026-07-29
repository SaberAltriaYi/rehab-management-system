package cn.iocoder.yudao.module.rehab.dal.mysql.binding;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientUserBindingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabPatientUserBindingMapper extends BaseMapperX<RehabPatientUserBindingDO> {

    default RehabPatientUserBindingDO selectActiveByPatientId(Long patientId) {
        return selectOne(new LambdaQueryWrapperX<RehabPatientUserBindingDO>()
                .eq(RehabPatientUserBindingDO::getPatientId, patientId)
                .eq(RehabPatientUserBindingDO::getBindStatus, "active")
                .orderByDesc(RehabPatientUserBindingDO::getUpdateTime));
    }

    default RehabPatientUserBindingDO selectActiveByAppUserId(Long appUserId) {
        return selectOne(new LambdaQueryWrapperX<RehabPatientUserBindingDO>()
                .eq(RehabPatientUserBindingDO::getAppUserId, appUserId)
                .eq(RehabPatientUserBindingDO::getBindStatus, "active")
                .orderByDesc(RehabPatientUserBindingDO::getUpdateTime));
    }

    default RehabPatientUserBindingDO selectActiveByPatientIdAndAppUserId(Long patientId, Long appUserId) {
        return selectOne(new LambdaQueryWrapperX<RehabPatientUserBindingDO>()
                .eq(RehabPatientUserBindingDO::getPatientId, patientId)
                .eq(RehabPatientUserBindingDO::getAppUserId, appUserId)
                .eq(RehabPatientUserBindingDO::getBindStatus, "active"));
    }

    default List<RehabPatientUserBindingDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabPatientUserBindingDO>()
                .eq(RehabPatientUserBindingDO::getPatientId, patientId)
                .orderByDesc(RehabPatientUserBindingDO::getUpdateTime));
    }

    default List<RehabPatientUserBindingDO> selectActiveListByPhone(String phone) {
        return selectList(new LambdaQueryWrapperX<RehabPatientUserBindingDO>()
                .eq(RehabPatientUserBindingDO::getPhone, phone)
                .eq(RehabPatientUserBindingDO::getBindStatus, "active")
                .orderByDesc(RehabPatientUserBindingDO::getUpdateTime));
    }
}
