package cn.iocoder.yudao.module.rehab.service.ai.client;

import lombok.Data;

/**
 * AI 客户端返回
 */
@Data
public class RehabAiClientResponse {

    /**
     * 是否成功
     */
    private Boolean success;
    /**
     * 结构化 JSON 文本
     */
    private String outputJson;
    /**
     * 原始模型返回文本
     */
    private String outputText;
    /**
     * 原始响应 JSON
     */
    private String rawResponseJson;
    /**
     * token 使用信息 JSON
     */
    private String tokenUsageJson;
    /**
     * 实际模型名
     */
    private String model;
    /**
     * 耗时
     */
    private Long latencyMs;
    /**
     * 错误信息
     */
    private String errorMessage;
}
