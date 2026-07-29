package cn.iocoder.yudao.module.rehab.service.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;

import java.util.List;

public interface RehabAuditLogService {

    Long createAuditLog(String moduleType, Long moduleId, String operationType, Long operatorUserId,
                        String operatorRole, Object beforeData, Object afterData,
                        String resultStatus, String remark);

    PageResult<RehabAuditLogRespVO> getAuditLogPage(RehabAuditLogPageReqVO reqVO, Long operatorUserId);

    List<RehabAuditLogRespVO> getModuleAuditLogs(String moduleType, Long moduleId, Long operatorUserId);

}
