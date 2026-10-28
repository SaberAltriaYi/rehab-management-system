# 康复内部版发布回归

`internal_release_regression.sh` 是不创建患者、不保存账号或令牌的生产只读回归入口。它会依次
执行部署预检、数据库一致性检查、HTTPS 冒烟、前端生产依赖审计和禁用模块产物扫描。

```bash
script/rehab/internal_release_regression.sh
```

需要创建患者、评估、计划等数据的端到端验收只能在隔离环境或获批的验收窗口执行，使用最小权限
临时身份。结束后停用身份、撤销测试会话，按经批准的可恢复策略归档测试记录；不要直接删除关联
数据或审计来强行清理。具体见 [S0/S1测试策略](../../docs/s0/test-identity-policy.md)。内部版保持 AI 关闭，不再提供 AI 演练脚本。

`output/` 只用于本机临时证据，除 `.gitkeep` 外已被 Git 忽略。

## S0只读资产盘点

`python3 -B script/rehab/s0_inventory.py`（Python3.11+）只输出JSON到标准输出，不改业务文件。
`--probe`仅增加固定未登录回环HTTP状态检查。用`python3 -B -m unittest discover -s script/rehab -p 'test_s0_inventory.py' -v`测试；测试不连接业务数据库或Docker。资产登记、快照和冻结含义见 [S0索引](../../docs/s0/README.md)。
