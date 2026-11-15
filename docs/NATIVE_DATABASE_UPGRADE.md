# 本机原生 MySQL：旧库副本核验与升级

仅适用于已经获准的旧库接管，不是新安装器。原库保持不动，在新的业务副本升级、验收后通过连接配置切换；不要改名覆盖原库。

## 不变的安全规则

- 先确认运行中 Java 进程的实际数据库、端口与当前账号机构，不根据源码默认配置猜测。
- 备份数据库、附件、启动配置、原版本 JAR；校验备份能恢复。
- 不重放 001–019 的初始化、演示数据、清理和账号停用 SQL。历史文件与校验和不可修改。
- 原生模式仍使用 `migrations.manifest` 和 `internal_schema_history`，不取消已存在的版本、顺序、未知版本及校验和校验。
- 客户端口令只在仓库外、当前用户所有、权限 600 的 MySQL option 文件中；命令行不传明文密码。连接固定 TCP 回环地址，明确指定端口与库名。

## 原生迁移适配

```bash
export REHAB_NATIVE_MYSQL_CLIENT_FILE=/absolute/private/mysql-client.cnf
export REHAB_NATIVE_MYSQL_DATABASE=rehab_upgrade_example
export REHAB_NATIVE_MYSQL_PORT=3306
sh deploy/internal/migrate.sh verify-files
sh deploy/internal/migrate.sh status
sh deploy/internal/migrate.sh apply
```

不设置这些变量时仍走原 Docker Compose 适配。原生模式没有账本同样拒绝执行，绝不会自动创建账本或重放基线。

## 缺失历史账本时

1. 从当前源码构建固定版本的脱敏基线参考，不使用业务数据生成参考。参考只在独立库恢复，例如 `rehab_reference_<名称>`。
2. 将一致性业务备份恢复到新的 `rehab_upgrade_<名称>`。原业务库不作为接管工具目标。
3. 运行只读审计：

```bash
python3 deploy/internal/native_schema_audit.py baseline \
  --client-config /absolute/private/mysql-client.cnf \
  --database rehab_upgrade_example --reference rehab_reference_example \
  --output /absolute/private/baseline-audit.json
```

4. 对所有差异逐项复核。工具比较必需列的类型、空值、默认值、字符规则、自动属性，完整索引列顺序/唯一性/前缀，以及外键目标、库范围和删除/更新规则。结构等价但名称不同的索引可保留；新增业务列、额外索引不删除。任何实际结构差异都不能靠忽略错误通过。
5. 本轮旧库只缺关系约束，已在副本预先执行新增 034 校验/补齐。遇到孤立数据或类型不兼容会失败；不得清掉孤立行、关闭外键检查或改患者归属来过关。无差异后使用同命令加 `--adopt-reviewed-copy`。
6. 001–019 登记为 `baseline=1`、`installed_by=native-audited-snapshot`：含义为“审计后的旧库结构快照”，**不声称执行过历史脚本或清理过原数据**。单独保留审计 JSON、参考和实际结构摘要。
7. 实际执行缺失的 020–023。若 024 已存在，不重跑 CREATE TABLE。独立参考库执行固定 024/025 后，使用审计工具 `motion` 模式逐项核验现有 11 表，确认此前版本齐全后才明确接管 024/025。
8. 继续实际执行后续增量直至当前版本（含 034），运行 `status` 确认校验和与账本一致。034 的预处理不会替代最终真实执行及登记。
9. 按原主键及原列逐条核对患者、周期、评估、报告、训练、账号及角色数据，允许审核过的增量脚本新增记录/列，不允许旧行被改变。AI 配置关闭是明确的例外，记录原因，不混作“完全未变化”。

## 切换与回退

演练通过后，短暂停止 Core 写入，重新备份最新源库并建立最终副本，重复同一套核验与迁移。仅在全部检查通过后更新连接配置和稳定 JAR 路径，启动新后端。保留原库和旧配置，不自动回滚数据库迁移。

运行 JAR 应位于仓库外按版本保留的目录，不能让开发构建覆盖正在运行的 JAR。工作区服务使用独立私有目录、回环监听和专用服务令牌；不创建默认账号。关闭开发模拟认证及通用敏感请求日志，业务审计仍保留。正常升级不删除旧库、附件或数据卷。

切换后已经产生新业务写入时，不可直接把连接指回旧库，否则会遗漏新记录；先停止写入并制定差异回灌方案。切换后无业务写入且启动验收失败时，恢复原连接/原 JAR 再启动，记录失败日志并保留副本。
