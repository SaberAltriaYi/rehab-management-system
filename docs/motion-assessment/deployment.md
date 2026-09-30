# 部署、迁移与上传

## 1. 迁移记录缺口修复（已有库）

问题：旧版 Compose 在 MySQL initdb 阶段直接建出 020+ 增量表，却没有写入 `internal_schema_history`，
之后 `migrate.sh status` 显示 PENDING，`apply` 重复 `CREATE TABLE` 失败。

处理（`deploy/internal/`）：

* `init-incremental-migrations.sh`（initdb `111`，位于 `110-schema-history` 之后）：新数据卷按
  `migrations.manifest` 顺序“实际执行 → 校验通过后登记（baseline=0）”，与 `migrate.sh apply` 语义一致；
  任何一步失败即中止（fail closed）。001–019 仍由 `init-schema-history.sql` 以 baseline 登记。
* `migrate.sh adopt <版本>`：只用于旧库补记录；仅 020+、仅“纯 CREATE TABLE”脚本，需备份后显式设置
  `CONFIRM_ADOPT=ADOPT-REHAB-INITDB`；逐表比对数据库列名与顺序必须与脚本完全一致，才以
  `installed_by=adopt-verified` 登记清单中的 SHA-256；不一致则拒绝并提示人工核验。不允许用 `baseline` 跳过 020+。
* `preflight.sh`：启用引擎时校验 `MOTION_ENGINE_URL` 只能是 Compose 内部地址、`MOTION_ENGINE_TOKEN` ≥ 32 位且
  不得复用其他密码，并检查迁移清单只读挂载。
* 合同测试 `test_business_schema_contract.py`：增量 SQL 不得出现 `CREATE TABLE IF NOT EXISTS` 或以
  DROP/TRUNCATE/DELETE/UPDATE/REPLACE 开头的语句；`migrate.sh verify-files` 校验清单 SHA-256 与文件一致。
* 桌面版 `desktop/scripts/*.mjs` 同步迁移清单与 adopt 流程。
* bash 3.2（macOS）兼容：`$var` 后紧跟中文/全角字符会被当成变量名一部分，统一写 `${var}`，
  `ShellPortabilityTests` 扫描 `deploy/internal/*.sh` 防回归。

## 2. 迁移 024（动作评估）

* 文件：`sql/mysql/rehab-motion-assessment-v1.sql`，清单行 `024|a3db2d5b…|…`。
* 只新增：11 张 `rehab_motion_*` 表（InnoDB、utf8mb4、`tenant_id` + 索引）、菜单 9700/9710–9717、角色授权；
  无 DROP/UPDATE/DELETE。菜单名不含“AI”，与 `check-database.sh` 的门禁（≥34 张康复表、≥41 外键、
  禁用 AI 菜单）兼容。
* 执行：备份 → `deploy/internal/migrate.sh`（新库）或 `migrate.sh adopt`（历史库补记录）→ `check-database.sh`。
* 回滚：024 是纯新增，回滚 = 恢复迁移前备份；不要手工 DROP 以免破坏迁移记录一致性。

## 3. 分析引擎（可选）

见 `deploy/internal/README.md` “智能动作评估（可选组件）”：

```bash
# 引擎仓库
docker build -t rehab-motion-engine:1.0.0 .
# .env
MOTION_ENGINE_URL=http://motion-engine:8790
MOTION_ENGINE_TOKEN=$(openssl rand -hex 32)
# 启动
docker compose --env-file deploy/internal/.env -f deploy/internal/docker-compose.yml --profile motion up -d
```

* 镜像 ENTRYPOINT 是研究 CLI，Compose 以 `entrypoint:` 覆盖为 `python -m rehab_biomechanics.motion.service`。
* 未部署引擎时：建档、上传、人工录入、审核可用，“开始处理”以“引擎未配置”失败，不伪造结果。
* 后端配置 `yudao.rehab.motion.*`（`application-internal.yaml`）：连接 5 s / 读取 180 s 超时、worker 批量 5、
  租约 300 s、最多 5 次尝试、OpenCap 轮询 30 s、截止 12 h、`ai-enabled: false`。
* 引擎与协议版本升级后，已签署报告保持原版本；重新分析会生成新 `analysis_revision` 并把受影响的已审核分数
  标记为 needs_recheck。

## 4. 上传上限与自动筛选串行上传

评估了提高上限：现有 Spring（16 MB/32 MB）与 Nginx（32 MB）限制保护整个系统，且真实 OpenCap 导出中
单个 `.mot/.trc` 远小于 16 MB，视频按授权可选，因此**不放宽**，改为前端自动“技能”：

* “选择 OpenCap 文件夹”后按服务端策略（`GET /rehab/motion/upload-policy`）自动筛选：跳过 pkl/vtp/
  OutputMedia/图片/日志/超限文件；未取得视频授权跳过全部视频；显示跳过原因。
* 队列并发 1、逐个上传；网络错误/5xx/429 自动重试 3 次（指数退避），失败项可一键重排；
  真实样本 181 个文件 → 有授权 65 个入队、无授权 17 个。
* 如确需提高上限，必须同时修改 Spring multipart、Nginx `client_max_body_size`、`MotionFileRules` 单文件上限
  和 `src/test/resources/motion/upload-policy.json`（一致性测试会检查），并重新构建。

## 5. 部署检查

```bash
python3 -m unittest discover -s deploy/internal -p 'test_*.py'   # 迁移守卫、合同、compose、shell 可移植性
node --test desktop/scripts/runtime-tools.test.mjs
deploy/internal/preflight.sh
deploy/internal/check-database.sh   # 迁移后
```

Compose 保持 `OPENAI_ENABLE_AI_ANALYSIS: "false"`；引擎镜像固定版本 `1.0.0`。
