# 康复管理系统 App 进度核验（2026-09-26）

## 结论

**当前是可在本机隔离环境试用的康复管理 Web/桌面预发布项目，机构业务已完成若干受限路径验收；不是已完成公开发行的桌面正式版，更不是已经上架的手机 App。** 移动端是可编译的 UniApp MVP 源码。全系统路线图仍有未交付项，不宜用未经定义的百分比表示完成度。

## 项目和历史材料口径

- `/Users/saber/Documents/playground` 是多个项目检出的工作目录。顶层 [README](../README.md) 是另一套 FastAPI 报告系统；本报告的「App」指 `rehab-module-enable-clean/` 中的 RuoYi Vue Pro 二次开发「运动康复评估与业务管理系统」，而不是把所有目录混作一套程序。
- 已查看 `/Users/saber/Downloads/arena`：其中有两份 Arena 工作区快照，含此前模型产出的实施计划、构建核验、隔离浏览器验收记录和源码；另有一个与康复系统无关的网页 ZIP。未发现可核对逐条问答的聊天记录文件。因此下文的「此前目标」来自这些**留存成果**，不是声称看到了完整对话逐字稿。归档计划与当前仓库的 [分阶段实施计划](../rehab-module-enable-clean/docs/mcp-implementation-plan.md) 对得上。
- 权威本地检出是 `rehab-module-enable-clean` 的 `master`，当前 HEAD `49b3e40`（2026-09-24）；2026-09-26 只读查询 GitHub `master` 也是同一提交：[提交记录](https://github.com/SaberAltriaYi/rehab-management-system/commit/49b3e40395bcd37b37b30f3dfc49d27c28939188)。不能拿 `ruoyi-vue-pro` 旧部署检出或 Arena 备份当作当前发布包。[历史工作树差异说明](../rehab-module-enable-clean/docs/mcp-business-modules-progress.md)

## 已到达的阶段

| 方向 | 已有证据 | 进度边界 |
| --- | --- | --- |
| 本机 Web 后台 | 2026-09-26 只读检查：`rehab-local-admin-8443`、隔离后端及独立 MySQL/Redis 容器仍在运行；`https://127.0.0.1:8443/index` 返回 HTTP 200，后端 `/actuator/health` 返回 `UP`。9 月 24 日使用合成账号完成 15/15 条所选页面/API 路径、5 类业务对象共 15 次新增/修改/删除 UI 操作；库存入 5、出 2 后余 3，超额出库被拒。详见[本机单版本验收](../rehab-module-enable-clean/docs/local-single-8443-acceptance-2026-09-24.md)。 | 今天未登录、未重新跑业务 E2E；200 和 `UP` 只证明可达/健康，不证明全角色、全业务或签名/TLS 发行验收。库存审批用 HTTPS 接口加页面余额复核，不是完整逐键 UI 流程。 |
| 康复业务主链 | [项目 README](../rehab-module-enable-clean/README.md) 列出患者/Episode、评估、17 页 V4.1 报告 DOCX/PDF、计划、签到、进度、复评。9 月 20 日的[批次 02 验收](../rehab-module-enable-clean/docs/mcp-execution-batch-02.md) 用合成评估复测了 SFMA/综合评估的**手动草稿保存**、编辑归属和离开确认；56/56 可见菜单只读导航通过。 | 这些证据不等于完整临床工作流验收。SFMA 自动保存、乐观并发修订、弱网恢复、完整原始资料包/确定性导出和统一 Client 等仍属[后续路线图](../rehab-module-enable-clean/docs/mcp-implementation-plan.md)。 |
| 机构运营模块 | 9 月 23 日在**独立合成数据库**完成迁移 001–023，隔离测试覆盖部分 BPM/CRM/ERP/Member/GoView 页面、租户/角色拒绝、库存回滚和 Flowable 双用户审批；详见[隔离验收](../rehab-module-enable-clean/docs/isolated-browser-acceptance-2026-09-23.md)及上述 9 月 24 日本机验收。 | 仅开放审阅通过的页面。第三方报表设计器、任意 SQL、AI、商城、支付、IoT 等保持关闭；未宣称所有流程、权限矩阵和旧业务数据迁移已验收。现行 `:8443` 使用新隔离库：据[验收记录](../rehab-module-enable-clean/docs/local-single-8443-acceptance-2026-09-24.md)，旧患者记录/附件未迁移，旧项目 Docker 卷已按当时用户选择清除，不能把现行站点当作旧库升级完成。 |
| 构建与质量 | 9 月 23 日 opt-in 后端 831 项测试中 795 通过、36 跳过、0 失败；默认和 opt-in 包、当时内部前端打包成功。[构建记录](../rehab-module-enable-clean/docs/build-verification-2026-09-23.md)；9 月 24 日另一次后端 clean test 99 项通过，见[安装包进度](mcp-installer-progress-2026-09-24.md)。 | 这些是**历史执行结果，今天未重跑**。管理端 `pnpm ts:check` 仍有 1129 条既有诊断；9 月 24 日标准 `build:internal` 未完成，诊断性构建遇到 `t.stringLiteral is not a function`。当前 `dist-internal` 目录日期为 9 月 23 日，`desktop/runtime/1.0.0` 不存在，不能用旧前端产物冒充当前提交的已验收运行包。 |
| Windows/macOS 桌面版 | GitHub 公开的最新桌面发布仍是 2026-08-03 的 [preview.3](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/desktop-v1.0.0-preview.3)，有 Windows x64 安装器和 macOS universal DMG。 | 都是 `_unsigned` 内部测试包，比 9 月 24 日当前源码旧；[正式发布清单](../rehab-module-enable-clean/docs/desktop-release-checklist.md)仍未勾选。管理端构建、runtime、干净设备回归、Windows 签名和 macOS 签名/公证未完成。 |
| iOS/Android/微信客户端 | [UniApp README](../rehab-module-enable-clean/yudao-ui/yudao-ui-rehab-uniapp/README.md)列出员工/患者 MVP 功能和 H5、微信、App-Plus 构建方式；本机存在三种 `dist/build/` 目录。 | 本次扫描客户端目录未找到 APK/AAB/IPA/WGT；App-Plus 编译输出**不是**原生安装包。[manifest](../rehab-module-enable-clean/yudao-ui/yudao-ui-rehab-uniapp/src/manifest.json) 的 DCloud/微信 AppID 为空。患者登录源码默认关闭：后端仅凭手机号＋患者编号匹配，未完成独立验证、防枚举和安全审查；令牌未接入 Keychain/Keystore。尚无上架或审核通过证据。 |

## 当前主要阻断和建议顺序

1. **桌面可重现打包**：定位 Vue JSX/Babel 构建异常，让原样标准 `pnpm build:internal` 通过；基于目标提交重新生成、校验 `desktop/runtime/1.0.0`，补后端/前端/启动器测试、隔离恢复和干净设备安装/升级/卸载证据。不要复用旧 `dist-internal` 或 preview.3 声称是新版本。
2. **发行安全**：完成 Windows 签名、macOS Developer ID 签名/公证、校验和、权限/TLS/备份恢复清单。公开发行前不要把未审业务模块、任意 SQL 或真实患者资料接入外部 AI。
3. **手机端发布门禁**：先改造患者身份认证、限频/防枚举、令牌安全存储；再确定 HTTPS API 域名、AppID/包名/签名归属，生成 APK/IPA 并做真机与微信平台验收。此前保持患者登录关闭。
4. **业务完整度**：按[实施计划](../rehab-module-enable-clean/docs/mcp-implementation-plan.md)继续 SFMA 草稿并发/自动保存、原件和多格式资料包、统一 Client/CRM/预约及机构运营闭环；逐项消化 TypeScript 债与 36 项跳过测试，补角色/租户/设备/恢复的全链路证据。

**核验限制：**本轮没有读取凭据、患者数据或修改业务代码/数据库；只写了这份进度报告。没有重新执行整套测试，也未做登录后的新浏览器验收。一次完整 `git status` 在工作树读取阶段久未结束，因此不作「工作区无未提交改动」的保证；Git 提交和公开 release 的比对仅说明已确认的 HEAD/标签。此前模型记录中的「尚未完成」须结合后续 9 月 23–24 日的验收阅读，不能直接当作今天的最终状态。
