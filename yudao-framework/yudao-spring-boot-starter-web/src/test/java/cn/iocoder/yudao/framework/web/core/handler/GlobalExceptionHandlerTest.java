package cn.iocoder.yudao.framework.web.core.handler;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnBadRequestForMalformedJsonBody() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("test",
                mock(ApiErrorLogCommonApi.class));
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error: object cannot be converted to long");

        CommonResult<?> result = handler.methodArgumentTypeInvalidFormatExceptionHandler(exception);

        assertEquals(BAD_REQUEST.getCode(), result.getCode());
        assertEquals("请求参数类型错误: 请求体格式不正确", result.getMsg());
    }

}
