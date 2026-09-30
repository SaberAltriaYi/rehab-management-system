# 顺序复核：构建与发布阻断（2026-09-27）

后续同日局部类型修补及同源桌面资源重建见[补充报告](mcp-admin-type-runtime-rebuild-2026-09-27.md)；本报告保留首次顺序复核时的真实退出状态。

目标为 `master` HEAD `49b3e40` 加当前未提交改动；不提交、不部署、不启用患者入口、不对现有数据库执行迁移。详见[首次审查](mcp-project-integrity-review-2026-09-27.md)与[门禁修补](mcp-integrity-checks-build-hardening-2026-09-27.md)。所有“通过”均要求该次命令实际退出 0，软中止不算通过。

## 环境与修补

- 原 `Documents/Playground` 工作树的类型检查持续约 235 秒仍在逐个读取 `node_modules` 的小型 `.d.ts`，**软中止，无测试结论**。经确认本机磁盘约余 20–23 GiB，16 GiB 内存并曾占用约 16 GiB 交换空间；没有终止其他用户进程。
- 用户批准创建并在结束后清除一次性临时副本。直接 `cp -cR .` 因历史 `target/test-classes` 巨量生成文件极慢，已停止并只清理本次新建的不完整副本。改为 `git archive HEAD` + `.git` 写时复制 + `git diff HEAD --binary` + 未跟踪文件：对 **152 个已跟踪改动和 18 个未跟踪文件逐项字节比对**，Git 索引一致，HEAD 一致。新副本约 86 MB（安装依赖前）；不包含无关旧 `target/node_modules`。以下构建和单测在这个隔离副本中运行，依赖以冻结锁文件**离线安装**。全部复核后仅清理获批的 `/tmp/rehab-sequential-verify.oL5Rf8` 副本，命令退出 0；原树运行资源仍在。
- 敏感扫描器 `script/rehab/check-repository-sensitive-materials.sh` 已修复：`git grep` 的读取/执行错误不能再作为“没有匹配”误报通过；额外用模拟退出 2 的 Git 验证失败闭锁。管理端 `package.json` 显式固定与 CI、UniApp 一致的 `pnpm@10.15.1`。
- 干净检出上被忽略的 `src/types/auto-imports.d.ts` / `auto-components.d.ts` 不存在，直接 `ts:check` 会虚增数千个未知名字错误。`pnpm build:internal` 已验证生成两份声明且字节内容与原工作树相同；三条 CI/发行 workflow 改成**安装 → 构建生成声明 → 阻断式类型检查**，YAML 解析与步骤顺序静态断言均通过。**未取消 TS 硬门禁。**

## 顺序运行结果

| 阶段 | 实际结果与证据 | 边界 |
| --- | --- | --- |
| 敏感扫描 | 原工作树跟踪文件扫描退出 0（约 315 秒）；另对 18 个未跟踪文件做相同高置信度模式扫描，0 命中、0 跳过；失败闭锁探针通过 | 模式未命中不等于证明无任何凭据或真实患者数据；忽略文件和全历史不在该检查范围 |
| 管理端 | `pnpm@10.15.1 install --frozen-lockfile --offline` 通过；`build:internal` 通过（57 秒）。干净检出不先构建的类型检查为 7,515 条；生成声明后 `ts:check` 退出 **2，600 条真实诊断**，主要 TS2339 242、TS2322 142、TS2345 70；集中在 Mall、Pay、BPM 设计器等模块 | CI/发行**仍红灯**，不能把成功构建视作类型检查通过；不以 `|| true` 或降低 TS 严格性掩盖债务 |
| UniApp | 冻结锁文件离线安装通过；`type-check` 退出 0（2 秒）；H5、微信小程序、App 代码构建顺序退出 0（10/4/4 秒），生成 `dist/build/{h5,mp-weixin,app}` | 代码构建不等于微信平台发布或签名原生安装包 |
| JDK 17 定向 | `mvn -B -o -pl yudao-module-rehab -am -Dtest=RehabAppPatientControllerTest,PatientInvitationTokensTest -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0；目标 7 项、0 失败、0 跳过 | 测试合成请求与邀请码原语，不开启旧患者认证 |
| JDK 17 默认全 Reactor | `mvn -B -o test` 退出 0；Surefire XML **832 项：796 通过、36 跳过、0 失败/错误** | 默认配置不包含 opt-in 模块；跳过的测试不算通过 |
| JDK 17 可选机构构建线 | `mvn -B -o -Prehab-business-integration -pl yudao-server -am test` 退出 0；Report 9 项全通过，ERP 无独立 Surefire 测试 | 不表示正式服务已启用 ERP/Report，不运行生产迁移 |
| 迁移 | `python3 -m unittest discover -s deploy/internal -p test_migration_guard.py -v` 退出 0，**24 项：21 通过、3 个真实 MySQL 集成测试因未 opt-in 跳过**；`sh deploy/internal/migrate.sh verify-files` 退出 0 | 没有对真实数据库执行 `apply`，隔离容器前滚/恢复仍待另行验收 |
| 桌面运行资源 | 原树被忽略的历史 `desktop/runtime/1.0.0` 写时复制到隔离副本：849 个源文件与副本文件数一致，`runtime-manifest.sha256` 字节一致。副本执行 `node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0` **退出 1**，报告 `校验清单遗漏运行资源文件：BUILD-INFO 2.txt`。只读盘点：清单列 840 项，实有 849 个文件（含清单本身），缺失 0 项，另有 8 个未列入清单的历史顶层 ` 2` 副本文件 | **资源完整性门禁失败**，不能发布。原有 BUILD-INFO 中前端源码与产物摘要也均和本轮重新构建的 frontend receipt 不一致；未改原树历史资源，未把旧资源认定为当前构建产物 |

## 剩余发布阻断

1. 管理端**600 条类型诊断**是真实失败。按当前错误分布优先清理 Mall/Pay、BPM 设计器等组件及实际交付模块，并每批重跑全量 `ts:check`；在零错误以前保留 CI/发行硬门禁。
2. 历史桌面资源 `check-runtime` **退出 1**：除 manifest 外存在未入清单的 `BUILD-INFO 2.txt`、`LICENSE 2`、`NOTICE 2.md`、`RUNTIME-README 2.txt`、`THIRD_PARTY_NOTICES 2.md`、`VERSION 2.json`、`docker-compose 2.yml`、`runtime-manifest 2.sha256`。不删除原树资源、不改宽 manifest 校验；以**同一源码/后端 JAR/前端 receipt**重新生成干净运行资源并重新验证，才有完整性和发布来源证明。数据库隔离卷 E2E、签名、真机与患者强认证仍未验收，患者旧入口必须维持 HTTP 403。
