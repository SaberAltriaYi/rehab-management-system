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
2. 校验 assessmentId、tenantId 和幂等键；taskId 可选，系统会从启动任务响应中取得真实 taskId。
3. 调用管理系统启动动作评估任务。
4. 轮询任务状态，失败、取消或超过60次轮询立即停止。
5. 任务完成后调用 AI 草稿生成接口，再读取评估状态。
6. 结果回到管理系统，进入待治疗师审核；n8n 不执行签署和正式评分修改。

患者姓名、身份证、电话和原始视频不发送给 AI。n8n 不可用时，管理系统仍应能直接执行同一任务。
