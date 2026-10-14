# 康复动作评估 n8n 工作流

此目录提供可导入 n8n 的最小编排模板。工作流只传递评估任务编号和结构化结果，不把原始视频放进 n8n 长期流转。

导入后配置以下环境变量或凭据：

- \`REHAB_SYSTEM_BASE_URL\`
- \`REHAB_SYSTEM_TOKEN\`
- \`MOTION_ENGINE_BASE_URL\`
- \`MOTION_ENGINE_TOKEN\`
- \`REPORT_AI_BASE_URL\`（可选，关闭 AI 时由系统模板生成）

工作流顺序：

1. 系统创建评估任务并调用 Webhook。
2. 校验 assessmentId、taskId、tenantId 和 idempotencyKey。
3. 调用管理系统获取任务。
4. 调用运动评估引擎执行解析、OpenSim 辅助处理和确定性规则计算。
5. 将结构化结果回写管理系统，进入待治疗师审核。

患者姓名、身份证、电话和原始视频不发送给 AI。n8n 不可用时，管理系统仍应能直接执行同一任务。
