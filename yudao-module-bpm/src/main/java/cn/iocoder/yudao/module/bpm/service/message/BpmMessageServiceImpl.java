package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.convert.message.BpmMessageConvert;
import cn.iocoder.yudao.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskTimeoutReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * BPM 消息 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmMessageServiceImpl implements BpmMessageService {

    @Resource
    private SmsSendApi smsSendApi;

    @Resource
    private WebProperties webProperties;

    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        sendSmsSafely(reqDTO.getStartUserId(), BpmMessageEnum.PROCESS_INSTANCE_APPROVE, templateParams);
    }

    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("reason", reqDTO.getReason());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        sendSmsSafely(reqDTO.getStartUserId(), BpmMessageEnum.PROCESS_INSTANCE_REJECT, templateParams);
    }

    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        sendSmsSafely(reqDTO.getAssigneeUserId(), BpmMessageEnum.TASK_ASSIGNED, templateParams);
    }

    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", reqDTO.getProcessInstanceName());
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(reqDTO.getProcessInstanceId()));
        sendSmsSafely(reqDTO.getAssigneeUserId(), BpmMessageEnum.TASK_TIMEOUT, templateParams);
    }

    /**
     * 短信是辅助通知，不应因用户未填写手机号或短信网关故障回滚流程审批事务。
     * 不记录异常消息，避免将手机号或短信内容写入应用日志。
     */
    private void sendSmsSafely(Long userId, BpmMessageEnum message, Map<String, Object> templateParams) {
        try {
            smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(userId,
                    message.getSmsTemplateCode(), templateParams));
        } catch (RuntimeException ex) {
            log.warn("BPM 短信通知失败，templateCode={}，userId={}，exceptionType={}",
                    message.getSmsTemplateCode(), userId, ex.getClass().getSimpleName());
        }
    }

    private String getProcessInstanceDetailUrl(String taskId) {
        return webProperties.getAdminUi().getUrl() + "/bpm/process-instance/detail?id=" + taskId;
    }

}
