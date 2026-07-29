package cn.iocoder.yudao.module.rehab.dal.mysql.assignment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assignment.RehabTherapistAssignmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 患者治疗师分配 Mapper
 */
@Mapper
public interface RehabTherapistAssignmentMapper extends BaseMapperX<RehabTherapistAssignmentDO> {

    default List<RehabTherapistAssignmentDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabTherapistAssignmentDO>()
                .eq(RehabTherapistAssignmentDO::getPatientId, patientId)
                .orderByDesc(RehabTherapistAssignmentDO::getStartTime));
    }

    default List<RehabTherapistAssignmentDO> selectActiveListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabTherapistAssignmentDO>()
                .eq(RehabTherapistAssignmentDO::getPatientId, patientId)
                .eq(RehabTherapistAssignmentDO::getAssignStatus, "active")
                .orderByDesc(RehabTherapistAssignmentDO::getStartTime));
    }

    default RehabTherapistAssignmentDO selectActivePrimaryByPatientId(Long patientId) {
        List<RehabTherapistAssignmentDO> list = selectList(new LambdaQueryWrapperX<RehabTherapistAssignmentDO>()
                .eq(RehabTherapistAssignmentDO::getPatientId, patientId)
                .eq(RehabTherapistAssignmentDO::getAssignStatus, "active")
                .eq(RehabTherapistAssignmentDO::getRoleType, "primary")
                .orderByDesc(RehabTherapistAssignmentDO::getStartTime));
        return list.isEmpty() ? null : list.get(0);
    }

    default List<RehabTherapistAssignmentDO> selectActiveListByTherapistUserId(Long therapistUserId) {
        return selectList(new LambdaQueryWrapperX<RehabTherapistAssignmentDO>()
                .eq(RehabTherapistAssignmentDO::getTherapistUserId, therapistUserId)
                .eq(RehabTherapistAssignmentDO::getAssignStatus, "active")
                .orderByDesc(RehabTherapistAssignmentDO::getStartTime));
    }

}
