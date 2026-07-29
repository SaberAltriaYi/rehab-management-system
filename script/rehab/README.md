# 康复内部版发布回归

`internal_release_regression.sh` 是不创建患者、不保存账号或令牌的生产只读回归入口。它会依次
执行部署预检、数据库一致性检查、HTTPS 冒烟、前端生产依赖审计和禁用模块产物扫描。

```bash
script/rehab/internal_release_regression.sh
```

需要创建患者、评估、计划等数据的端到端验收只能在隔离环境或获批的验收窗口执行，必须使用临时
账号并在结束后删除账号、令牌及全部测试业务数据。内部版保持 AI 关闭，不再提供 AI 演练脚本。

`output/` 只用于本机临时证据，除 `.gitkeep` 外已被 Git 忽略。
