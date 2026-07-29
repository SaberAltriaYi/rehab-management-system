package cn.iocoder.yudao.module.rehab.service.ai.client;

import java.util.Map;

/**
 * Rehab AI 客户端抽象
 */
public interface RehabAiClient {

    /**
     * 生成结构化 JSON
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param schemaName   schema 名称
     * @param jsonSchema   JSON schema
     * @param options      生成选项
     * @return 生成结果
     */
    RehabAiClientResponse generateStructured(String systemPrompt, String userPrompt, String schemaName,
                                             Map<String, Object> jsonSchema, RehabAiClientOptions options);

}
