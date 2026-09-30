# 完整性复核与构建门禁修补（2026-09-27）

> **后续核查：**本文记录首次修补时点。针对原先被文件系统阻断的敏感扫描、前后端构建/测试及迁移单测，参见[顺序复核实测结果](mcp-sequential-validation-2026-09-27.md)；以该文的实际退出码和跳过数为准。

目标：`rehab-module-enable-clean`，`master` HEAD `49b3e40` 加上**未提交**的现有改动。本文只记录本轮实际完成和取得终态的证据；前一轮报告见 [项目完整性审查](mcp-project-integrity-review-2026-09-27.md)。未触碰现有患者数据库/业务卷，未开放患者入口，未部署、推送或发布。

## 已实施

1. `.github/workflows/ci.yml`：管理端安装依赖后运行**阻断式** `pnpm ts:check`；新增独立 UniApp job，在 Node 22、pnpm 10.15.1 下以冻结锁文件安装，依次运行 `type-check` 与 H5／微信小程序／App **源码构建**。`release.yml` 和 `desktop-release.yml` 均将管理端类型检查加入发布/打包前置，并与已有桌面 CI 统一 pnpm 10.15.1。**这会使既有类型错误暴露为 CI/标签发行失败；没有伪造通过，也未运行 GitHub Actions。**微信构建不等于平台审核，App 构建不等于签名 APK/IPA。
2. `deploy/internal/README.md` 与 `deploy/lan/README.md`：明确空库账本只预置历史 001–019；清单已到 023，020–023 是需要独立审查并实际执行的增量，不能在已有业务库重放旧初始化。`test_migration_guard.py` 增加文档版本与 manifest 最新版本的回归断言。
3. `PatientInvitationTokens`：要求外部传入的摘要为规范的 64 字符小写十六进制；畸形和非 ASCII 值在摘要比较前拒绝。增加负例测试；患者控制器测试增加合法 JSON 形式的旧登录／绑定请求仍返回 HTTP 403 的 MockMvc 回归。这**不是**短信核验、单次邀请消费或完整患者认证，现有门禁保持关闭。

## 本轮核查与限制

| 核查 | 结果 | 范围说明 |
| --- | --- | --- |
| Git 对象完整性 | `git fsck --no-reflogs --connectivity-only` 和从当前 `.git` 的私有临时副本运行 `git fsck --full --no-reflogs` 均退出码 0；仅提示 3 个 dangling tags | 浅克隆已有对象的检查；不证明远端所有历史和未提交工作树一致性。本轮自建临时副本已清除，源目录未触碰。 |
| 目标路径工作树 | 对本轮涉及的 11 个路径运行限定 pathspec 的 `git status --short`，可见 6 个已跟踪修改和 5 个未跟踪文件；目标路径未暂存/已暂存差异的 `git diff --check` 均退出 0 | `.github/workflows/ci.yml` 的状态为 `M `（本来已有暂存），未做 stage/commit；这不是全仓状态结论 |
| CI YAML | 当前三个 workflow 均经 PyYAML 静态解析（6、1、4 个 job），桌面发行的 `pnpm ts:check` 步骤已核对 | 尚未在 GitHub runner 执行，未证明依赖/构建 job 通过 |
| Java 邀请原语 | 通过本轮源码提取后 `javac --release 8 -encoding UTF-8` 和合成数据 smoke：同租户正确摘要接受，跨租户／畸形摘要拒绝 | 仅验证无外部依赖的原语，不替代 Maven/JUnit 与应用级端到端验收 |
| 迁移文档契约 | 对当前 manifest 和两份已更新部署文档的独立只读断言通过，最新版本为 023；新增 Python 测试源码 AST 解析、目标文件 `git diff --check` 通过 | 新加入的 `test_migration_guard.py` 和本轮后端 JUnit 因工作树文件读取极慢未取得测试终态，不记为通过 |
| 患者路由源码 | 静态读取当前 `RehabAppPatientController.java`：旧 `/auth/login`、`/auth/bind` 显式返回 403；患者资料/报告等接口先调用拒绝方法 | 不是 HTTP 层实测；新加的 MockMvc 回归尚未在 Maven 中通过 |
| 敏感材料 | 原工作树扫描进入 `git grep` 后约 4 分 40 秒仍无终态，软中止且确认无残留进程；对**Git 索引中的内容**独立运行相同高置信度密钥模式 `git grep --cached -q`，退出 1（未命中） | 索引中的未命中不能覆盖未暂存/未跟踪文件，也不覆盖脚本后续检查或未知密钥模式；全仓工作树安全结论仍**未验证** |
| 其余重型核查 | 对 UniApp `pnpm type-check`、桌面 `check-runtime`、JDK 17 定向 Maven 测试与迁移单测均发起了检查 | 多项在数分钟内无终态、均软中止；取消包装的退出码 0 不是测试通过，须在稳定的非同步副本/CI 重跑并归档退出码 |

## 仍阻断“完整构建／正式发行”的事项

- 管理端现有大批 TypeScript 错误尚未清零，本轮未取得可信的**当前** `ts:check` 数量；CI 新门禁预计失败，这是发现缺口而不是通过记录。先按 System/BPM → CRM/ERP → 其他模块逐批修复并复跑，不降低 TS 严格性或使用 `|| true`。
- 全工作树 `git status` 在约 80 秒只刷新 8% 索引；直接复制工作树 100 秒约 900 文件，已中止并按授权清理临时副本。Mac `/tmp` 所在磁盘仅余约 13 GiB（使用率 97%）；瓶颈原因尚未确诊，不能宣称当前未提交改动已被全量扫描。
- 患者授权邀请记录、机构核验、短信服务、原子一次性消费、限频防枚举、撤销/轮换的患者令牌、微信 AppID/合法域名/真机和生产备份恢复审批仍缺失。旧手机号＋患者编号入口和患者资料接口继续 403；不要以此次原语／测试增补申请真实患者开放。
- Windows/macOS 签名安装器、机构客户端新形态、真实数据库增量迁移和真机/平台验收仍需外部资源及隔离环境，不在此轮验证范围。

**建议接续：**在有足够磁盘空间的非同步检出/CI 固定目标提交并审阅未提交改动；顺序重跑敏感扫描、管理端 `ts:check`、UniApp 检查/三目标构建、JDK 17 定向/全 Reactor、迁移单测与资源 `check-runtime`，最后使用专用隔离 Docker 卷做 E2E；每项报告命令、退出码、跳过数和产物哈希。正式患者启用另需安全与业务责任人验收。
