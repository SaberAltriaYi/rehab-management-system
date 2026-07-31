# 康复管理系统桌面打包实施计划

## 1. 审计基线与当前架构

- GitHub 仓库：`SaberAltriaYi/rehab-management-system`
- 基线分支：`master`
- 基线提交：`1131ae156e70bcde0e67a77669505db4115fa490`
- 后端：Java 8 目标字节码、Spring Boot 2.7.18、Maven 多模块，发布文件为
  `yudao-server/target/yudao-server.jar`。
- 前端：Vue 3、Vite、Element Plus，真实工程路径为
  `yudao-ui/yudao-ui-admin-vue3-app`，真实命令为
  `pnpm install --frozen-lockfile && pnpm build:internal`，输出目录由
  `.env.internal` 的 `VITE_OUT_DIR=dist-internal` 决定。
- 数据服务：MySQL `8.4.10`、Redis `7.4.10-alpine3.21`，标签同时固定多架构
  `sha256` 摘要。
- 运行时：Temurin `8u492-b09-jre-jammy` 与 Nginx `1.30.4-alpine3.24` 同样固定
  多架构摘要；Nginx 提供 HTTPS 管理端和同源 `/admin-api` 代理。
- 数据：Compose 命名卷保存 MySQL、Redis、康复附件和服务日志；19 个数据库迁移由固定
  SHA-256 清单和 `internal_schema_history` 账本管理。
- 安全边界：默认仅可信内部环境，AI、商城、支付、IoT、BPM 等未交付模块关闭；MySQL、
  Redis 和后端不发布主机端口。

审计发现 GitHub `master` 只包含 `deploy/internal/frontend-release.ref`，没有提交上述前端
工程源码，因此仅检出 `master` 的 CI 无法执行文档声明的 `build:internal`。实现阶段将从现有
已验证提交 `799c1161bc41669539cc170d4f077ab9cfc2653c` 导入真实前端工程快照，使桌面发布
流程自包含、可复现，不依赖开发机目录或可变远端分支。

仓库当前没有 `AGENTS.md` 或 `AGENTS.override.md`。计划文件落地后将创建根目录
`AGENTS.md`，记录项目结构、前后端与桌面端命令、禁止提交项和发布门禁。

## 2. 桌面方案

第一版采用 Tauri v2 桌面启动器，不重写康复管理页面，也不替换 Spring Boot、MySQL 或
Redis。新增 `desktop/launcher/`：

- WebView 只呈现简洁中文启动器界面；
- Rust 后端负责 Docker/Compose 检测、参数安全构造、端口检查、随机密码、运行配置、
  TLS、服务生命周期、健康检查、备份、日志脱敏、诊断和单实例锁；
- 所有外部命令使用可执行文件和参数数组调用，不拼接 shell 字符串；
- Docker command runner 可注入，单元测试不依赖本机 Docker；
- 关闭启动器不停止后台服务。

业务系统继续由默认浏览器打开本机 HTTPS 地址。启动器不嵌入患者页面，不忽略 TLS 错误，
不添加遥测、广告、统计或云端上传。

## 3. 最小运行资源包

CI 先构建后端和内部前端，再由
`desktop/scripts/build-runtime.mjs` 生成 `desktop/runtime/1.0.0/`。仅包含：

- 后端可执行 JAR；
- `dist-internal/`；
- 桌面专用 Dockerfile、Compose 和 Nginx 模板；
- `application-internal.yaml` 所需运行约定；
- 构建期在隔离 MySQL 中依次执行完整初始化、19 项固定迁移及安全加固后导出的脱敏快照；
- 快照保留 `internal_schema_history` 账本，但不包含历史原始演示脚本、患者记录或云端密钥；
- LICENSE、版本和构建清单。

资源检查脚本拒绝 `.git`、`.env`、私钥、备份密钥、备份文件、测试数据、IDE 文件、
`node_modules`、Maven/Node 缓存及无关 `target` 文件。最终用户不需要 Maven、JDK、
Node.js 或 pnpm。

安装包不内置 Docker 镜像。首次启动由 Docker 拉取固定版本的 MySQL、Redis、Nginx 和
JRE 基础镜像，并在界面显示当前阶段；不使用 `latest` 基础镜像，不删除已有镜像或容器。

## 4. Windows 与 macOS 差异

### Windows

- 目标：Windows x64；
- 安装器：Tauri NSIS `康复管理系统_1.0.0_x64-setup.exe`；
- 开始菜单、卸载入口由 Tauri/NSIS 提供，桌面快捷方式作为安装选项；
- 不要求 Git Bash、WSL、Homebrew、Java、Maven、Node 或 pnpm；
- 未签名安装器明确记录 SmartScreen 提示；
- 代码签名通过 CI Secret 条件启用，不把证书写入仓库。

### macOS

- 目标：`universal-apple-darwin`，同时覆盖 Apple Silicon 与 Intel；
- 安装器：`康复管理系统_1.0.0_universal.dmg`；
- 应用从 `/Applications` 运行；
- 无 Developer ID 时生成带 `unsigned` 标识的测试构建；
- Developer ID 签名和 notarization 仅在相应 CI Secret 完整时执行。

macOS GUI 进程不假设 shell 初始化文件提供 Docker 路径。Rust 后端在标准 Docker Desktop
路径、进程环境和系统 PATH 中解析可执行文件。

## 5. Docker 依赖与服务管理

首次启动依次检查：

1. Docker CLI 路径；
2. Docker daemon；
3. `docker compose version`；
4. 本机端口；
5. 运行资源完整性；
6. 配置、密码和 TLS；
7. 固定镜像拉取；
8. Compose 服务启动；
9. MySQL、Redis、后端、管理端健康状态。

Docker 不可用时只给出中文安装/启动指引，不静默下载或安装 Docker Desktop。Compose
项目名固定为 `rehab-desktop`，重复点击启动会先检查当前状态，不创建第二套服务。

普通停止使用 `docker compose stop`；普通启动、停止、重启和升级均禁止
`docker compose down -v`。

## 6. 配置与数据持久化

使用操作系统应用数据目录：

- Windows：`%APPDATA%\\com.saberaltriayi.rehab\\`
- macOS：`~/Library/Application Support/com.saberaltriayi.rehab/`

目录结构：

```text
config/
  settings.json
  .env
  first-start/
  tls/
runtime/
  1.0.0/
backups/
logs/
secrets/
  backup.passphrase
  mysql-client.cnf
  initial-admin-password.txt
version.json
launcher.lock
```

MySQL、Redis、附件和服务器日志继续使用稳定的 Docker 命名卷。运行资源和配置位于用户数据
目录，应用更新只增加新的版本资源目录，不改变 Compose 项目名和卷名。

首次启动生成三个彼此不同、至少 32 个随机字符的 MySQL 业务密码、MySQL root 密码和
Redis 密码，以及独立备份密钥。运行密码只写入权限受限的 `.env`、MySQL 客户端配置及
`secrets/` 首次登录/备份文件，不进入日志、诊断、Git 或安装目录。默认绑定
`127.0.0.1`，默认端口冲突时提示用户选择其他端口。

第一版不默认开放局域网。未来启用局域网设置时必须由用户主动确认、选择具体私网 IP、重新
生成匹配证书，并继续禁止 `0.0.0.0` 和公网端口转发。

## 7. 备份、升级、卸载与删除

- 启动器“创建备份”通过容器内 `mysqldump` 和 `tar` 导出数据库与附件，由 Rust 在主机侧
  加密、校验并写入 `backups/`；
- 备份密钥与运行配置保持在应用数据目录，日志不记录密钥或业务内容；
- 检测到新运行资源版本时保留旧版本；更新说明和 UI 要求用户在安装新版本前先创建备份；
- 数据库迁移沿用固定校验和和账本，不假设自动回滚；
- 更新失败保留旧资源、数据卷、备份和日志；
- 卸载桌面程序默认不删除应用数据目录或 Docker 数据卷；
- “删除所有本地数据”只在危险区提供，要求用户输入精确确认文字并二次确认，显式停止并删除
  固定名称的数据卷；普通流程永不使用 `down -v`。

## 8. 签名与公证

Windows 预留：

- `WINDOWS_CERTIFICATE`
- `WINDOWS_CERTIFICATE_PASSWORD`
- `WINDOWS_TIMESTAMP_URL`

macOS 预留：

- `APPLE_CERTIFICATE`
- `APPLE_CERTIFICATE_PASSWORD`
- `APPLE_SIGNING_IDENTITY`
- `APPLE_API_ISSUER`
- `APPLE_API_KEY`
- `APPLE_API_KEY_P8`

签名和公证步骤均条件执行。缺少 Secret 时 CI 仍生成明确标记为 unsigned 的测试安装包。
正式发布前必须由权利人配置 Windows 代码签名证书、Apple Developer ID Application
证书和 App Store Connect API 凭据。

## 9. 自动化与验收

新增 `.github/workflows/desktop-release.yml`。提交到以 `master` 为目标的 PR 时做完整安装器
回归；合并后支持 `workflow_dispatch` 手动构建，推送 `v*` 标签时构建并创建 draft Release：

1. JDK 17 下运行康复模块及 Web 安全测试；
2. 执行隔离后端构建；
3. 使用 pnpm 锁文件构建内部前端；
4. 生成并检查最小运行资源；
5. Windows runner 运行 Rust/前端测试并构建 NSIS；
6. macOS runner 运行 Rust/前端测试并构建 universal DMG；
7. 生成 SHA-256 和构建信息并上传 artifacts；
8. 标签构建全部成功后创建 draft GitHub Release。

桌面 Rust 测试覆盖密码、配置、数据目录、端口、Docker 参数和错误、健康超时、日志脱敏、
升级持久化、卸载默认保留和单实例锁。桌面前端使用 Vitest 检查状态呈现、危险操作确认和
诊断脱敏边界。

## 10. 风险和第一版暂不实现

- Docker Desktop 仍是前置条件；安装包不把 MySQL/Redis 变成嵌入式数据库。
- 首次拉取镜像需要网络，速度取决于 Docker Hub 可达性。
- 未签名 Windows/macOS 构建会触发 SmartScreen/Gatekeeper 提示，不作为正式对外发行包。
- 第一版不实现静默 Docker 安装、不配置路由器、不自动开启局域网、不上传日志、不做云备份。
- 第一版不实现自动更新服务；版本化资源、升级前备份和稳定数据卷为后续安全更新保留基础。
- 数据库恢复属于高风险操作，第一版文档化现有受控恢复流程，不在普通启动器主页提供一键恢复。
- 上游 Java 8/Spring Boot 2.7 风险登记继续有效；桌面封装不改变该运行时风险边界。
- RustSec 对当前锁文件未发现 vulnerability；会报告 Tauri Linux 目标的 GTK3 绑定及若干
  构建期 proc-macro/Unicode 间接依赖为 unmaintained，并报告一项仅 Linux GTK 链路的
  `glib` unsound 警告。Windows/macOS 不编译 GTK 链路，但仍需随 Tauri 上游升级复核。
