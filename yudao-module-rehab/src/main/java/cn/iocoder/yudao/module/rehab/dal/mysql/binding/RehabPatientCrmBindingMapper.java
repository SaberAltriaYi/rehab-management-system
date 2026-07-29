package cn.iocoder.yudao.module.rehab.dal.mysql.binding;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.binding.RehabPatientCrmBindingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 患者 CRM 绑定 Mapper
 */
@Mapper
public interface RehabPatientCrmBindingMapper extends BaseMapperX<RehabPatientCrmBindingDO> {

    default RehabPatientCrmBindingDO selectByPatientId(Long patientId) {
        return selectFirstOne(RehabPatientCrmBindingDO::getPatientId, patientId);
    }

    default List<RehabPatientCrmBindingDO> selectListByPatientIds(List<Long> patientIds) {
        return selectList(RehabPatientCrmBindingDO::getPatientId, patientIds);
    }

    default List<RehabPatientCrmBindingDO> selectListByCrmCustomerId(Long crmCustomerId) {
        return selectList(new LambdaQueryWrapperX<RehabPatientCrmBindingDO>()
                .eq(RehabPatientCrmBindingDO::getCrmCustomerId, crmCustomerId)
                .orderByDesc(RehabPatientCrmBindingDO::getUpdateTime));
    }

    default List<RehabPatientCrmBindingDO> selectListByBindStatus(String bindStatus) {
        return selectList(new LambdaQueryWrapperX<RehabPatientCrmBindingDO>()
                .eq(RehabPatientCrmBindingDO::getBindStatus, bindStatus)
                .orderByDesc(RehabPatientCrmBindingDO::getUpdateTime));
    }

}
