package cn.iocoder.yudao.module.rehab.dal.mysql.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabAuditLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAuditLogMapper extends BaseMapperX<RehabAuditLogDO> {

    default PageResult<RehabAuditLogDO> selectPage(RehabAuditLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RehabAuditLogDO>()
                .eqIfPresent(RehabAuditLogDO::getModuleType, reqVO.getModuleType())
                .eqIfPresent(RehabAuditLogDO::getModuleId, reqVO.getModuleId())
                .eqIfPresent(RehabAuditLogDO::getOperationType, reqVO.getOperationType())
                .eqIfPresent(RehabAuditLogDO::getOperatorUserId, reqVO.getOperatorUserId())
                .eqIfPresent(RehabAuditLogDO::getResultStatus, reqVO.getResultStatus())
                .betweenIfPresent(RehabAuditLogDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabAuditLogDO::getCreateTime)
                .orderByDesc(RehabAuditLogDO::getId));
    }

    default List<RehabAuditLogDO> selectListByModule(String moduleType, Long moduleId) {
        return selectList(new LambdaQueryWrapperX<RehabAuditLogDO>()
                .eq(RehabAuditLogDO::getModuleType, moduleType)
                .eq(RehabAuditLogDO::getModuleId, moduleId)
                .orderByDesc(RehabAuditLogDO::getCreateTime)
                .orderByDesc(RehabAuditLogDO::getId));
    }

}
