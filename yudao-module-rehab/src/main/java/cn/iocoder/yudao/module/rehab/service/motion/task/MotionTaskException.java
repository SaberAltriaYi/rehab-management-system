package cn.iocoder.yudao.module.rehab.service.motion.task;

import lombok.Getter;

/**
 * 任务步骤失败。retryable=false 表示输入问题（重试无意义），直接进入 FAILED。
 * message 只允许包含非敏感的技术说明（不含患者信息、路径、密钥）。
 */
@Getter
public class MotionTaskException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public MotionTaskException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public static MotionTaskException fatal(String code, String message) {
        return new MotionTaskException(code, message, false);
    }

    public static MotionTaskException retry(String code, String message) {
        return new MotionTaskException(code, message, true);
    }

}
