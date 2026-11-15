# D1 后续｜Mac 内部 Launcher 持久锁定 C2 Core：实际安装与验收

日期：2026-11-15（北京时间）；用户选择方案 A：**修复现有已安装内部版 Launcher 点击“启动”可能恢复旧 Core 的缺陷**。仅适用本机内部 QA 及合成试点，不构成正式发行/临床使用批准。没有执行 D2/D3/D4/D5、HEP 037 或真实患者相关变更。

## 1. 已完成的工作

1. 定位根因：原已安装 `LauncherService::start_inner` 始终执行 `docker compose up --detach --build`，而基础运行包的 `server/yudao-server.jar` 仍为历史版本；因此即使 D1 容器已手动换成新 Core，后续“启动”仍可能覆盖新镜像。
2. 在 `studio-internal` 专用代码中新增受源码固定模板保护的 **`core-c2-qa-image.override.yml`**。其内容仅覆盖一个 Compose 字段：`services.server.image: rehab-c2-not-deployed:f3570d7b6db4`。配置放在本机私有 `~/Library/Application Support/com.saberaltriayi.rehab.internal/config/`，权限 `0600`，不是符号链接。启动器会逐字节比对内置模板；修改内容、扩大权限或替换为符号链接均拒绝。
3. 新版 Start 先核对镜像 tag 的真实 immutable ID：`sha256:b0f26de5e12d6cddd8a5fd2a0b1e7e000af6aa6e6f207da05c5ab0b798fca660`；符合时使用 **`up --detach --no-build --pull never`**，启动后再次比对实际 Core 容器镜像 ID。镜像缺失、摘要漂移或无信任 pin 却正在运行新 Core 时**失败关闭**，不自动拉取、替换 JAR 或回退旧版。普通版不受此本机 QA pin 影响。
4. 新版 Restart 使用原有 Docker `restart`，前后都验证镜像；Core 被 Compose 重新创建后自动执行原 Admin 容器的 `nginx -t` 和 `nginx -s reload`，使反向代理更新上游地址。保持身份令牌覆盖文件的**独立**审核/权限机制，未启用 Owner→Core 机器 API，也不更改 TLS/数据库/业务卷。
5. 本机 QA 应用已编译并通过 `codesign --verify --deep --strict`。**实际安装**替换 `/Applications/康复管理系统内部版.app`，先温和退出原 Launcher、原子切换到 QA 应用、重新打开；原应用完整备份在私有验证目录及 `/Applications/.rehab-d1-pin-original-20261115.app`，二者都未删除。新应用保持内部标识 `com.saberaltriayi.rehab.internal`，签名仍是 **ad-hoc** 而不是正式 Developer ID 公证。
6. 原安装包内部资源曾包含 **037 候选**，但本机实际运行资源仍是 **036**。此轮新 QA 应用打包受检查的 **036** 资源、实际启动使用该 036 资源和 D1 核验的 C2 镜像，绝不因此触发 037。

## 2. 真实测试与可复核证据

| 检查项目 | 结果 |
|---|---|
| 内部版 Rust（包括 pin 文件篡改、0644 权限、symlink、身份覆盖共存、镜像摘要漂移、未配置 pin 拒绝旧版重建、Nginx 校验失败） | **68 PASS、0 FAIL、2 ignored** |
| 普通版 Rust（行为不变） | **64 PASS、0 FAIL、2 ignored** |
| 桌面前端 Vitest / 本地维护脚本 | **通过** |
| 全仓桌面脚本安全回归 | **131 PASS、0 FAIL** |
| 036 运行资源清单哈希 | **通过** |
| 内部 QA `.app` ad-hoc 代码签名及 Bundle Identifier | **通过** |
| 当前已安装新可执行文件 SHA-256 | `8cacd50e3053c68890ce7bf9cf2261ff7fe01314234b404e02fdb6eccf1e7f8e`，验证一致 |
| 被替换的旧 Launcher SHA-256 / 完整备份 | `b0eab0ed6d90711de377f60ef9d466897ea34984eebadeff498a2484dd819f44`，备份一致 |
| 当前新 Launcher GUI 进程、单实例锁重新获得 | **通过** |
| `docker compose config` 与原始配置逐字段对比 | **仅 Core server.image 一项差异** |
| 使用新版启动器相同 Compose 参数执行真实 `up --detach --no-build --pull never` | **通过**；四个原容器 ID/镜像保持一致 |
| 使用同一 pin 上下文执行 Core 单服务实际 `restart` | **通过**；重启后仍为 C2 immutable image ID，Spring Boot healthy |
| Nginx 重载、Owner 9444 / Core 18443 登录页与代理 API TLS | **HTTP 200**，TLS 验证成功 |
| Core JAR SHA-256 | `46503ed03d0e63dbbb37ed51c9b19d17be78037a365b0e07346ea45eabeea09c`，一致 |
| Core MySQL 迁移账本与 SaaS 正式计划/开户记录 | **036:36 / 0 / 0**，没有业务迁移或开户 |

第一次安装尝试的 SHA 预检因手写期望摘要漏了 3 个字符而**在退出旧 GUI 前**安全拒绝；检查确认原 GUI 与后端均未变化。修正为完整 64 字节的 SHA-256 后重新独立核验并执行安装，成功。该错误属于安装辅助命令抄录问题，不是二进制发生变化，不应隐藏。

## 3. 正式当前状态及边界

- **现用 Launcher：**已换成内部 QA 版本并重新打开；安装程序自身与项目源码文件均未放入患者资料或凭据。
- **现用 Core：**仍为 D1 C2 候选镜像；MySQL、Redis、Nginx 服务未因应用替换而重建；一次 Core 受控重启成功；正式 SQL 版本仍 036。
- **Owner `test`：**`APPROVED / ACTIVE / 20`；Core `NOT_CONFIGURED`，正式 SaaS 计划 0、开户记录 0。Owner 机器通道仍关闭。
- **继续 NO-GO：**`effectiveCorePilotQuotaVerified`、`corePermissionPackageHasExactPilotScope` 和 `ownerCoreMachineChannelReady`，以及独立临床/HEP/发行门禁。镜像版本验证通过不自动允许创建正式机构。

## 4. 已知限制与回滚

1. 这是**本机内部 QA ad-hoc 签名应用**，不是 Developer ID、公证或公开发行。它依赖本机已存在的、经固定 SHA-256 验证的 C2 镜像与私有 pin 文件；若 Docker 镜像/标签被删除或摘要改变，Launcher 会**拒绝启动**，必须先通过受控安装流程恢复正确镜像。不能把“失败关闭”当作正式自动更新功能。
2. 已测试新版代码及其**等价的真实 Compose 启动/单服务重启命令**，也已验证新 GUI 进程正常启动；**未通过 GUI 自动化点击启动按钮完成逐界面端到端测试**，未执行 Mac 主机重启演练。按技术事实报告，不声称这两项已覆盖。
3. 原应用完整保留，回滚仅能在再次核实 Core 镜像、应用资源及业务数据库兼容后进入受控维护窗口：**先确认是否需要恢复原 Core 镜像及安全状态，再替换 Launcher**。仅把旧 Launcher 放回 `/Applications` 而继续使用当前新 Core，可能再次触发旧版 `up --build`，故不能将文件备份误当作可直接无条件回退的安全操作。
4. C1/D1 全新私有加密备份及其恢复密钥继续保留，不能上传或分享。没有执行 `docker compose down -v`、删除业务卷、修改生产数据库或清除任何历史安装包。

## 5. 后续建议

保留当前内部版 Launcher 的私有 pin。针对**正式可分发**的 Core/Launcher 一致版本，应将已验证的新后端 JAR、容器和整个运行包固化为可追溯且签名的下一版本，而非永久依赖本机 QA 镜像标签。单独核验新版本的代码签名、迁移、备份、临床验收与业务测试。后续 D2（最小套餐）、D3（额度计划）、D4（机器身份）、D5（唯一试点开户）仍须逐项独立授权。

## 6. 证据路径

- Mac1 私有 QA 验证与原 `.app` 备份：`/Users/saber/my-project/rehab/validation-evidence/qa-20261115-launcher-core-pin/`。
- 旧 `.app` 的另一份保留副本：`/Applications/.rehab-d1-pin-original-20261115.app`。
- 本机私有固定 pin 文件：`~/Library/Application Support/com.saberaltriayi.rehab.internal/config/core-c2-qa-image.override.yml`。
- 源码：`desktop/launcher/src-tauri/src/docker.rs`、`desktop/launcher/src-tauri/src/service.rs`，及 `resources/core-c2-qa-image.override.yml`。
- 最早 D1 已执行并验证的 Core 部署：`docs/execution/OWNER_CORE_D1_LIVE_BACKEND_CUTOVER_20261115.md`。
