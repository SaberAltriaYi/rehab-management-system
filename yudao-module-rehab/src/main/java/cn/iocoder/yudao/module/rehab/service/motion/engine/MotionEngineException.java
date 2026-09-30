package cn.iocoder.yudao.module.rehab.service.motion.engine;

import lombok.Getter;

/**
 * 引擎调用异常。message 已脱敏（不含请求体、令牌或患者信息），可安全写入任务表。
 */
@Getter
public class MotionEngineException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public MotionEngineException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

}
