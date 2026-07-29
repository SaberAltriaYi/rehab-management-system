package cn.iocoder.yudao.module.rehab.dal.mysql.log;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabPatientOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 患者操作日志 Mapper
 */
@Mapper
public interface RehabPatientOperationLogMapper extends BaseMapperX<RehabPatientOperationLogDO> {

    default List<RehabPatientOperationLogDO> selectListByPatientId(Long patientId) {
        return selectList(new LambdaQueryWrapperX<RehabPatientOperationLogDO>()
                .eq(RehabPatientOperationLogDO::getPatientId, patientId)
                .orderByDesc(RehabPatientOperationLogDO::getCreateTime));
    }

}
