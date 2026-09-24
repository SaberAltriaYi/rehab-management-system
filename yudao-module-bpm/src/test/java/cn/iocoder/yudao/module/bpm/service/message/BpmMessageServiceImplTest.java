package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** 短信故障不得回滚流程实例。 */
class BpmMessageServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private BpmMessageServiceImpl messageService;
    @Mock
    private SmsSendApi smsSendApi;
    @BeforeEach
    void setUp() {
        WebProperties webProperties = new WebProperties();
        webProperties.setAdminUi(new WebProperties.Ui().setUrl("https://example.invalid"));
        ReflectionTestUtils.setField(messageService, "webProperties", webProperties);
    }

    @Test
    void sendApproveNotification_withoutMobileDoesNotBlockProcess() {
        doThrow(new IllegalStateException("missing mobile"))
                .when(smsSendApi).sendSingleSmsToAdmin(any());
        BpmMessageSendWhenProcessInstanceApproveReqDTO request = new BpmMessageSendWhenProcessInstanceApproveReqDTO();
        request.setStartUserId(123L);
        request.setProcessInstanceId("synthetic-1");
        request.setProcessInstanceName("合成流程");

        assertDoesNotThrow(() -> messageService.sendMessageWhenProcessInstanceApprove(request));
        verify(smsSendApi).sendSingleSmsToAdmin(any());
    }

    @Test
    void sendApproveNotification_whenAvailableStillSends() {
        BpmMessageSendWhenProcessInstanceApproveReqDTO request = new BpmMessageSendWhenProcessInstanceApproveReqDTO();
        request.setStartUserId(123L);
        request.setProcessInstanceId("synthetic-2");
        request.setProcessInstanceName("合成流程");

        messageService.sendMessageWhenProcessInstanceApprove(request);
        verify(smsSendApi).sendSingleSmsToAdmin(any());
    }

}
