package cn.iocoder.yudao.module.rehab.service.log;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.audit.vo.RehabAuditLogRespVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.log.RehabAuditLogDO;
import cn.iocoder.yudao.module.rehab.dal.mysql.log.RehabAuditLogMapper;
import cn.iocoder.yudao.module.rehab.service.RehabDataPermissionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.ErrorCodeConstants.PATIENT_NO_PERMISSION;

@Service
@Validated
public class RehabAuditLogServiceImpl implements RehabAuditLogService {

    @Resource
    private RehabAuditLogMapper auditLogMapper;
    @Resource
    private RehabDataPermissionService dataPermissionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long createAuditLog(String moduleType, Long moduleId, String operationType, Long operatorUserId,
                               String operatorRole, Object beforeData, Object afterData,
                               String resultStatus, String remark) {
        RehabAuditLogDO log = RehabAuditLogDO.builder()
                .moduleType(moduleType)
                .moduleId(moduleId)
                .operationType(operationType)
                .operatorUserId(operatorUserId)
                .operatorRole(operatorRole)
                .beforeDataJson(beforeData == null ? null : JsonUtils.toJsonString(beforeData))
                .afterDataJson(afterData == null ? null : JsonUtils.toJsonString(afterData))
                .ip(ServletUtils.getClientIP())
                .userAgent(ServletUtils.getUserAgent())
                .resultStatus(resultStatus)
                .remark(remark)
                .build();
        auditLogMapper.insert(log);
        return log.getId();
    }

    @Override
    public PageResult<RehabAuditLogRespVO> getAuditLogPage(RehabAuditLogPageReqVO reqVO, Long operatorUserId) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        PageResult<RehabAuditLogDO> pageResult = auditLogMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(toRespList(pageResult.getList()), pageResult.getTotal());
    }

    @Override
    public List<RehabAuditLogRespVO> getModuleAuditLogs(String moduleType, Long moduleId, Long operatorUserId) {
        if (!dataPermissionService.isSuperAdmin(operatorUserId) && !dataPermissionService.isTherapist(operatorUserId)) {
            throw exception(PATIENT_NO_PERMISSION);
        }
        return toRespList(auditLogMapper.selectListByModule(moduleType, moduleId));
    }

    private List<RehabAuditLogRespVO> toRespList(List<RehabAuditLogDO> logs) {
        if (CollUtil.isEmpty(logs)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = logs.stream().map(RehabAuditLogDO::getOperatorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        return logs.stream().map(item -> {
            RehabAuditLogRespVO vo = BeanUtils.toBean(item, RehabAuditLogRespVO.class);
            if (item.getOperatorUserId() != null && ObjUtil.isNotEmpty(userMap.get(item.getOperatorUserId()))) {
                vo.setOperatorName(userMap.get(item.getOperatorUserId()).getNickname());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
