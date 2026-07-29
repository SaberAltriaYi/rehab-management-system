package cn.iocoder.yudao.module.rehab.service.ai.client;

import lombok.Data;

/**
 * AI 客户端调用参数
 */
@Data
public class RehabAiClientOptions {

    /**
     * 模型名
     */
    private String model;
    /**
     * 温度
     */
    private Double temperature;
    /**
     * 最大输出 token
     */
    private Integer maxOutputTokens;
    /**
     * 推理强度（low / medium / high）
     */
    private String reasoningEffort;
    /**
     * 超时（秒）
     */
    private Integer timeoutSeconds;
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    /**
     * mock 模式
     */
    private Boolean mockMode;
}
