# 康复管理系统｜桌面内测版候选发布门禁及人工视觉验收

**更新：2026-10-09 22:12（本机 Mac 工程续建）**

**判定：NO-GO。此文件只用于内测候选准备，不是正式发布证书、临床审核签字或已签名安装包。**

## 范围和安全约束

候选目标：治疗师/管理员在受控工作室内测试康复业务及动作评估；使用脱敏的**合成**数据验收。患者本人、未成年人和监护人的登录/绑定接口仍须保持关闭（HTTP 403），AI 不自动确诊/签署，B 版未完成临床审批前不得作为专业报告交付。任何包含未完成患者入口的候选必须单独由产品负责人批准缩小发布范围，不能仅从门禁中删除该项。

保留现有两个 dirty 工作树，不自动合并 PR #5、提交/推送代码、使用已有患者数据库/视频、停止业务容器、删除 Docker 数据卷，或创建正式签名制品。

## 14 项机器门禁：实际工程证据与缺失的最终验收

`desktop/scripts/prepackage-readiness.mjs` 最新结果仍是 `ready=false`、`blockerCount=14`。以下“阶段性工程通过”不能等同于门禁 `passed`；所有项目必须针对**同一冻结提交**提交仓库内完整日志和 SHA-256，遵循不可伪造、失败即拦截的原门禁算法。

| 门禁 | 当前事实 | 解除条件及责任 |
| --- | --- | --- |
| `SOURCE_DIRTY` | 主仓和动作评估分支都含历史未提交修改 | 代码负责人逐项审阅原始 diff、决定分支整合/版本冻结；不得 reset 原作者工作 |
| `REVISION_MISMATCH` | 现有样例证据不绑定新候选冻结 SHA | 完成安全整合后生成真实 `prepackage-evidence/1.0.0` 绑定不可变提交和日志 |
| `source_integration` | PR #5 仍未整合；已有共享权限文件及桌面代码重叠 | 人工审阅重叠文件，整合后跑整套回归并审批合并 |
| `java_full_test` | 历史主线 128/128、动作 174/174 成功 | **整合冻结版** JDK17 rehab + Web-security 完整重跑 |
| `frontend_build` | 主线报告 828 文件、动作分支 876 文件生产构建；最新动作 Vue TS 和 13/13 单测通过 | 整合后的同一前端冻结构建、lint/typecheck、签署构建回执 |
| `database_migration` | 独立 tmpfs MySQL 的 020–025 均通过校验和、执行与账本登记 | Tauri 调度的**真实加密备份→迁移→回滚/恢复** E2E，与现场升级兼容矩阵 |
| `tenant_permission` | 真实 OAuth2 合成管理员与两个租户治疗师的列表/详情隔离和匿名门禁通过 | 补齐所有敏感文件下载、重复处理、人工签署/更正并发与审计攻击负例 |
| `biomechanics_contract` | Java–Engine 静态协议 19/19，历史 Python 引擎合成测试通过 | 真实 Java→Engine/OpenCap 分析与结果持久化、ZIP 约束及失败回退 |
| `clinical_report_b` | 仅有 B 版草案和证据政策；当前 V4.1 不等于专业 B 版 | 21 主题与附录 A–F、家长/治疗师双版、原始证据链、逐页 QA 及具有资质者签字 |
| `patient_identity` | 患者 login/bind 在运行时返回 HTTP 403，**未开通** | 真机/短信验证、监护授权、一次性邀请/撤销、频率与安全存储；或正式审批内测范围完全排除患者端 |
| `e2e_synthetic` | 四服务 HTTPS + 六视口动作评估 Chrome 页面；另以**主线报告 UI 副本**在独立四服务浏览器环境验证三个视口、真实报告选择与危险 HTML 净化 | 完整患者建档→评估→OpenCap→人工审核→报告→训练→复评端到端及错误恢复；两工作树尚未冻结整合 |
| `security_license` | 官方 npm 生产依赖扫描无当前已知漏洞；RustSec 本地公告扫描列出 0 项漏洞、17 项未维护与 1 项 unsound 警告；`--deny unsound` **失败** | Java/Rust/Python/镜像/模型许可证、SBOM、告警所涉构建目标审查、更新公告数据库及法律审批 |
| `desktop_restore_upgrade` | Tauri Rust **43/43**，新增错误覆盖导入确认不得触碰 Docker/备份的负例；无签名 arm64 可执行文件和 Runtime 工程候选 | 真正 Tauri 资源嵌入、Win10/11、Mac Intel/ARM 初装/升级/故障恢复/卸载；目前迁移失败只保留加密备份，**不具备自动回滚完成证据** |
| `code_signing` | 未生成签名/公证正式安装包，未取得批准的签名材料 | Apple Developer ID/公证、Windows Authenticode、安全扫描、内测渠道的完整制品追溯与人工批准 |

## 真实 Chrome 截图和本次修复（不能代替人工签收）

Mac 本地 Google Chrome `154.0.8037.98`，一次性独立用户配置；隔离 Docker 项目和随机回环 HTTPS。使用一次性合成 OAuth2 账户/患者，不访问真实患者。通过的视口：桌面 `1440×900`、平板 `768×1024`、手机 `390×844`；动作评估列表和详情六个视口均正确进入路由且根视口横向溢出为 0px。还验证可见登录表单及手机“查看审核”按钮能实际切换到审核页。

第一轮截图发现：平板顶栏面包屑竖排、手机流程横向滚动缺提示、登录首帧未渲染完成。已修改动作分支响应式断点（小于 960px 为移动布局）、流程可滑动和键盘焦点/提示，并使 Chrome 自动化等待真实登录表单后截图；**第三轮重新生成 Runtime 后四服务、020–025、OAuth2 权限和 Chrome 浏览器全部退出 0**。中间一次错误的断言把正常的 50px 顶栏高度认作换行，失败日志保留，并修正为检查面包屑文字被挤窄。

本地最新动作截图及 JSON：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/browser-visual-20261009-v3/`。包含 `login-desktop.png`、`login-mobile.png`、`motion-list-{desktop,tablet,mobile}.png`、`motion-detail-{desktop,tablet,mobile}.png`、`motion-review-mobile.png`、`visual-results.json`；`humanVisualSignoff=false`。截图的个人/患者内容均为合成数据。

### 2026-10-09 22:47：报告中心实机浏览器及桌面负例补测

在唯一隔离的随机 Compose 项目里使用合成患者、疗程、评估和**未签字的草稿报告**，通过真实 Java REST API 返回患者报告列表与报告预览。主线最新 Vue 生产构建副本只挂入该项目的一次性 Nginx 静态目录，后端仍采用已核验的动作分支 Runtime；**这不是最终整合工作树**。Google Chrome 154 的 1440×900、768×1024、390×844 三视口均渲染报告中心，根视口横向溢出为 0。手机端实际点击“选择评估报告”并打开预览；在合成预览 HTML 中注入无害的 `<script>`、`<iframe>`、`onerror` 和 `javascript:` 测试样本，确认 DOMPurify 已移除危险标签、事件属性与危险链接，脚本未执行。

第一次报告中心真实截图发现移动弹窗的右侧固定宽表格挤压操作列、分页条侵占宽度以及时间范围筛选截断。已重构小屏报告为**响应式卡片**，保持复核/审批/锁版/解锁/预览/DOCX/PDF/版本/审计的原权限检查，并把移动日期筛选设为全宽。修复后主线 Vue 全量 TS 与 828 文件的内部生产构建退出 0；第二次真实 Chrome 测试新增校验手机卡片显示、宽表隐藏、日期控件边界和弹窗内部横向溢出、HTML 净化均通过，四服务、020–025、两租户 OAuth2 和患者 API 403 再次通过。合成临时容器已清理，已有 Docker 数据卷数保持 24。

最新报告中心截图、测试指标 JSON：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/report-browser-20261009-v2/`（包含桌面/平板/手机列表、手机报告卡片、HTML 预览）；**不包含真实患者数据，`humanVisualSignoff=false`**。真实 Chrome E2E 日志 `rehab-beta-report-browser-v2-20261009.log` SHA-256 `0a3674adc90a0e67dcb09bddf5b312e8d34f6385ebea44a8f04c303ee263ba96`。主线 UI 工程预览压缩包 `rehab-beta-report-ui-preview-20261009.tar.gz`，SHA-256 `72d7d4d7931a747c6811f0e907606bb802287d244439a6e7d7023aa65f923335`，不是安装包。

Tauri Rust 新增 `unconfirmed_full_import_does_not_touch_docker_or_existing_backups`，以合成加密备份哨兵验证覆盖导入的确认文字不匹配时不会执行 Docker 命令或修改备份。格式检查与 **43/43 单测通过**，日志 `rehab-beta-rust-import-guard-tests-final-20261009.log` SHA-256 `5407b640360973f8a99676426b32eb03ed0c444feb076234f5255fbbdd796012`。但实际服务的增量 DDL 迁移失败仅保留预升级加密备份、停止后续迁移，**未自动执行恢复**；覆盖导入失败仍需人工恢复。不能据此解锁桌面安装/回滚门禁。

RustSec 本地现有公告数据库扫描 `Cargo.lock`：0 项已列漏洞、17 项未维护、1 项 unsound（`glib 0.18.5`，`RUSTSEC-2024-0429`）；`cargo audit --no-fetch --deny unsound` 退出 1，风险扫描原始 JSON SHA-256 `72f63a58e879ad50a40c2ff3952b875ca5298b69580a82edd5742a647e72f6eb`。目标限定的 `cargo tree --locked --offline --target aarch64-apple-darwin -i glib` 返回“nothing to print”，**仅说明该 glib 包不在当前 macOS arm64 目标的可列依赖图**；Windows 图因本地缺少 `objc2-quartz-core` 元数据无法离线确认。公告数据库新鲜度与各目标可达性仍需独立复核，`security_license` 未关闭。

### 2026-10-09 23:02：Bridge 恢复后的严格可见性与制品复核

在重连后已确认前次间断期间的**真实执行结果**：主线 Vue 全量 TypeScript 与报告中心移动卡片版本的生产构建均退出 0（828 输出文件，source SHA-256 `af07c38cb9add4996ce33e7f7096a1db52d831083e0850bc6959e073e11024e9`，output SHA-256 `b86c864e0c4cf43185c56366091298133511e697e35f32e846c27d569ff9c6f7`）；启动器 Rust 43/43、格式检查、桌面 Node 15/15、Python/Shell 语法检查与两个工作树 `git diff --check` 均通过。

报告中心真实 Chrome **第三轮 E2E 退出 0**。进一步收紧断言：报告预览必须由**手机响应式卡片里真实可见、具有非零边界的按钮**触发，不允许借助隐藏的桌面表格按钮模拟成功。1440×900、768×1024、390×844 三个视口无根页面水平溢出；手机日期范围控件完整位于视口中，卡片可见、桌面宽表隐藏、患者报告弹窗自身没有水平溢出。浏览器显示的恶意 HTML 测试样本经 DOMPurify 消毒后无 `script` / `iframe` /事件属性/ `javascript:` 危险链接，测试脚本未执行。一次性四服务完成 020–025 checksum、真实 OAuth2 双租户和匿名权限回归，退出后测试容器数 0、原 Docker volumes 24。

新的只读合成截图目录：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/report-browser-20261009-v3/`；浏览器日志 `rehab-beta-report-browser-v3-20261009.log` SHA-256 `87bddda5781d7d89474f45329f6c2243137d60e8c2c04c848ff4570e9d48a2ec`，Rust 43 项测试日志 `rehab-beta-rust-import-guard-tests-20261009.log` SHA-256 `89c5348f07030c39ce0c0d6cd259ecf814cccd23654ead02666c79a16ef9c4e2`，桌面 Node 测试日志 `rehab-beta-report-mobile-desktop-node-20261009.log` SHA-256 `cce35c9ffbdee67ef896a12ba7388795f6be5fd034f21fcb459dd610ee0b7245`。均位于私有证据目录，单文件 0600、截图目录 0700；`humanVisualSignoff=false`，不代表已获得真人工签署。

供应链暂不能解锁：`glib 0.18.5` 在 macOS ARM 已列 Cargo 图未出现；本次尝试全目标 `cargo tree --locked --offline --target all` 因缺少 `async-executor 1.14.0` 包而失败（101），全平台目标可达性仍**未被证实**。不得将本次真实 Chrome、加密备份负例和 43 项 Rust 通过当作端到端桌面安装/自动回滚或 RustSec 安全审核 GO。`prepackage-readiness.mjs` 再次返回 `ready=false`、`blockerCount=14`。

### 真人工视觉验收待签清单

- [ ] Mac Chrome / Safari：桌面登录、租户切换、光标/焦点、报错及登出
- [ ] 平板 768px 和手机 390px：导航展开、表格横向滚动、筛选、抽屉、弹窗与流程可读性
- [ ] 动作评估：上传与错误、试次映射、处理中、失败重试、审核签署与留痕；完成版本不可误编辑
- [ ] **最新主线报告中心**：合成列表/卡片选择/DOMPurify 预览已在 Chrome 完成，仍待真人工复核其操作语义、审批授权、版本锁定、DOCX/PDF 导出和报告逐页质量
- [ ] 患者管理、计划、训练打卡、疼痛/RPE、复评、通知的真实端到端 UI
- [ ] 浅色/深色、200% 缩放、键盘可用性、辅助技术朗读、色彩对比度及不截断
- [ ] Windows Chrome/Edge、Mac Intel/ARM 真机安装运行，并确认正式安装权限
- [ ] 所有临床和未成年人隐私文案、署名、许可、免责声明由负责人复核

人工验收人：__________________　日期：____________　版本/平台：____________

人工最终结论：□通过　□有条件通过（附问题单）　□不通过　签名：________________

当前**没有任何授权人员签署**以上结论，不得代为填写或宣称已完成“完整人工视觉验收”。

## 内测候选工程物（不是正式安装包）

- 私有 Chromium 验收日志 `rehab-real-browser-e2e-v3-20261009.log` 的 SHA-256：`880a0c55c34105bb18526021c46e055a4f821243b48a37eb2e3fdce246d86145`；第一次/中间失败原始日志完整保留。
- 实测浏览器工程 Runtime 压缩包：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/rehab-beta-browser-validated-runtime-20261009.tar.gz`，约 145 MB，SHA-256：`535cb7d3d6fd0c5be70d7788da7efe2bcb14df8c62765d95bde3ad3e525da6bc`，权限 0600。
- 先前仅编译通过的**未签名、未打包** macOS arm64 Tauri 启动器仍为独立工程制品；资源档案与启动器尚未封装成可安装、可升级且签名的正式 Beta 应用。
- 两份前端 npm 生产许可证清单 `rehab-beta-main-npm-licenses-20261009.json` 和 `rehab-beta-motion-npm-licenses-20261009.json` 位于 Mac 私有目录；此项是初步检索，不是全部供应链合法许可证明。
- RustSec 审计 JSON、相关未完成维护/unsound 告警、离线目标图局限以及新的 Chrome v2 截图均保存在同一受限目录。主线和动作分支有未提交的原始修改，尚未形成共享版本冻结清单。

### 放行决策

#### 2026-10-10 接任复测与新安全增量（仍为 NO-GO）

- Mac ShunCode Bridge 实际检查：主线仍为 `master@3d778538a3`，动作分支为 `feature/motion-assessment@2f7f515973`，两个工作树均未提交。两侧共有 **6 个重叠的 dirty 文件**（权限服务及测试、Runtime 工具及测试、前端 package.json/锁文件）；未合并 PR #5。
- 主线最新报告 UI 完整 `pnpm ts:check` 和 `pnpm build:internal` **退出 0**，828 文件；回执输出 SHA-256 `b86c864e0c4cf43185c56366091298133511e697e35f32e846c27d569ff9c6f7`，本轮构建源/输出哈希与历史回执一致。
- 第四轮 Mac **真实 Chrome** 报告中心 E2E（`--report-browser-smoke`）退出 **0**：1440×900、768×1024、390×844 页面无根水平溢出；手机卡片、隐藏宽表、日期筛选、可见卡片预览按钮、DOMPurify 过滤不安全 HTML、合成 OAuth2 双租户、020–025 SQL 账本、患者 HTTP 403 均通过。新截图及 `visual-results.json`：`/Users/saber/my-project/rehab/validation-evidence/2026-10-09/report-browser-20261010-v4/`（7 张 PNG，`humanVisualSignoff=false`）。原始日志 `rehab-beta-report-browser-v4-20261010.log` SHA-256 `1840a7a3dce28d44f82d5bfbf5d20b433c8281720aadbad78d16c60d13809631`。合成容器无残留，原 Docker 卷仍为 24。
- 动作分支 Tauri 新增升级前两份加密备份密文 SHA-256 / 类型 / 路径校验，大文件流式哈希；新增已安装 Runtime 公开网页副本的最小权限修正（源资源与私密文件不改变）。Rust **45/45** 和格式检查均退出 **0**，日志 `rehab-tauri-runtime-perms-tests-v2-20261010.log` SHA-256 `08082b3649b6cd4552164f58d625fc4a5a6a0dc1d1de7e768ad783827c83b9b8`。
- 工程制品注意：历史 Runtime tar 在 `umask 077` 下常规解压后出现网页 `0600/0700`，`check-runtime` 失败；已保留失败原始观察。经私有副本修正后生成 `rehab-beta-runtime-public-web-permissions-20261010.tar.gz`（SHA-256 `d27ac98ef9f0e924c936b13a263853bda3de626a08ec16c741ba32d5d12e1ee7`），仅通过 `tar -xzpf` **保留权限的重新解压**、Runtime 校验和 Compose `--preflight-only`；在限制性 umask 下不带 `-p` 解压仍不符合权限检查。此包只是工程测试候选，不是安装包。
- 当前未实测 **Tauri 启动器本身的加密备份→真实 DDL→故障恢复及附件一致性**；密文 SHA 校验不证明可解密或可恢复。人工视觉确认、真实触控/键盘/屏幕阅读器、Windows/Safari、临床签署、许可证、代码签名和版本冻结仍缺。只读发布门禁 `ready=false`、`blockerCount=14`、退出码 **1**。无发布放行。

**当前 NO-GO**，禁止发布正式内测安装包。下一轮优先顺序：1) 用户/代码负责人批准安全整合与冻结；2) 真正 Tauri 备份升级回滚与跨平台安装；3) 完整临床 B 版、人审与患者范围决定；4) 浏览器人审签字及供应链合规；5) 获授权的签名/公证；6) 全部证据绑定冻结 SHA，再执行 `prepackage-readiness.mjs`，仅 0 阻断才能宣布 GO。

### 2026-10-10 续建：真实 Rust 启动器业务方法的合成 Docker 升级/恢复 E2E

**工程验证完成，正式发布继续 NO-GO。** 在 `feature/motion-assessment` 的 `service.rs` 增加默认 `#[ignore]` 的 `service_live_synthetic.rs` 集成测试，并为主线 `desktop/scripts/test-runtime-e2e-ephemeral.sh` 增加 `--launcher-fault-restore`。测试只有在新建随机 `rehab-ephemeral-*` Docker 项目、原有四服务 Compose 严格预检通过后才启动；MySQL、Redis 仍是 tmpfs，附件使用**临时测试目录**的 bind mount，以便 `compose exec/run` 共享合成附件；全部临时路径、挂载和回环端口均经过预检。测试执行器仅在测试时将 Rust 默认项目名改写成这个隔离项目，不更改正式启动器项目名。

真实执行的顺序：从 001–019 脱敏账本和虚构数据库/附件哨兵开始 → 使用真正 `LauncherService::upgrade_database` 生成 age 加密 SQL/附件备份 → 020 成功且登记 → 在测试私有副本把 021 替换为**SHA-256 与测试清单匹配的故意无效 SQL** → 验证 021 未登记且后续停止 → 两份实际加密备份通过 SHA 校验和密钥解密 → 修改合成 SQL/附件哨兵制造恢复差异 → 在隔离项目中**显式人工决策模拟**调用既有 `restore_transfer_sources` → 核查原哨兵内容、020 表撤销、账本恢复至 19、四服务健康 → 恢复真实打包 021 与原始清单，由真正 `upgrade_database` 顺序执行 020–025 → 最终账本 25、动作评估 11 表、合成记录未丢失、四服务健康。两轮升级前均保留各自的加密备份清单；所有明文解密仅在 Rust 私有临时目录中进行。

- 首次失败属于**测试断言误将 020 的 `report_go_view_project` 表认成动作评估表**，测试退出 101；原日志 `rehab-launcher-real-fault-restore-e2e-v1-20261010.log` 已保留。第二轮只修正断言后的“部分 DDL → 备份解密 → 显式恢复”退出 **0**，日志 `rehab-launcher-real-fault-restore-e2e-v2-20261010.log` SHA-256 `ae28b436aa85556801379760d5ac6e2d61d8074ae2e9611e8157bf05355993e3`。
- 第三轮又增加完整恢复后 020–025 **真实 Rust 调度升级**，`--launcher-fault-restore` **exit 0**；独立 Rust Docker 集成 **1 passed，0 failed**。最终日志 `rehab-launcher-real-fault-restore-and-success-v3-20261010.log` SHA-256 `0e6f16cd54c12edecdf6776416562b7b4a53b7cde273277ceb2580e9ba768ea8`。Rust 常规单测 **45 passed，0 failed，1 ignored**，格式检查通过；单测日志 `rehab-launcher-restore-unit-v3-20261010.log` SHA-256 `20ab1785bdfa17c16fe6442bdaa3aaaaee70200996ba18ea324af8c8e5874ac6`。
- 私有证据目录仍为 `/Users/saber/my-project/rehab/validation-evidence/2026-10-09/`（目录 0700、日志 0600），全部合成容器已退出并清理，无任何临时项目残留；原 Docker volumes 仍为 **24**。没有操作生产数据库、现有卷、患者资料，也没有 merge/commit/push 或正式签名。

**严格边界：** 上述是针对真实 Rust 核心方法、使用临时测试执行器的**合成 Docker 工程 E2E**，不是实际打包的 Tauri GUI/安装器操作，也没有为生产版本实现**自动回滚**。还未覆盖数据库/附件并发写入时的一致性、恢复失败后的可重试和日志对账、Win/Mac 真机升级/卸载、文件访问授权、人工签署与临床审批。发布脚本只读重跑 `ready=false`、`blockerCount=14`、`exit 1`；日志 `rehab-readiness-after-live-launcher-20261010.json` SHA-256 `f9376160773c1b42d4c283856cd7a68eb124648941334be47c20ef324ed8dca9`。**`desktop_restore_upgrade` 仍为 BLOCKED。**

整合准备只读差异审计：`docs/releases/SOURCE_INTEGRATION_AUDIT_20261010.md`；共享的 6 个 dirty 路径中两份 Java 权限文件当前一致，`runtime-tools` 必须人工保留权限与迁移两套能力，Vue 依赖/锁文件必须保留主线 `dompurify@3.4.16`。补跑桌面 Node `15/15` 通过（exit 0），日志 `rehab-launcher-post-e2e-node-tests-20261010.log` SHA-256 `247b7f6a9dc19f100e07e1575d361e1cbe6cd5eef5714bd7e4ba20d9742f43f2`。主线及动作分支 Git diff --check、Shell 语法检查通过。
