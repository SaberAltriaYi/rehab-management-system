# 康复管理系统桌面版使用与部署说明

## 架构与前置条件

康复管理系统 V1.0 桌面版是 Tauri v2 启动器，不是把现有服务改写成单机数据库。安装包已
包含构建后的 Java 后端、Vue 内部版页面、Nginx 配置和脱敏数据库初始化快照，因此最终用户
不需要 JDK、Maven、Node.js 或 pnpm；MySQL 8.4.10、Redis 7.4.10、Temurin 8u492 和
Nginx 1.30.4 均以固定标签及多架构摘要锁定，后端和管理端仍由本机 Docker Compose 运行。

安装前必须：

1. 安装当前受支持的 Docker Desktop；
2. 启动 Docker Desktop，等待其显示 Engine Running；
3. 确认磁盘至少有 20 GiB 可用空间；
4. 首次启动能访问 Docker 镜像仓库，以拉取固定版本基础镜像。

启动器不会静默下载或安装 Docker Desktop，不修改路由器，不配置公网转发，不添加遥测、
广告、统计或患者数据上传。

## 第一次启动

启动器依次执行：

1. 检查 Docker CLI、daemon 和 Compose v2；
2. 将安装包内 V1.0.0 运行资源校验后复制到用户数据目录；
3. 生成彼此不同的 48 位 MySQL 业务、MySQL root、Redis 密码和备份口令；
4. 生成本机 `localhost/127.0.0.1` TLS 证书；
5. 检查 8080 和 8443 端口；
6. 拉取固定基础镜像并构建两个本地应用镜像；
7. 创建稳定命名卷并启动服务；
8. 等待 MySQL、Redis、后端和管理端全部通过健康检查；
9. 显示租户“工作室内部”、账号 `admin` 和随机初始管理员密码，并允许从系统浏览器打开
   HTTPS 管理端。

请先保存初始密码，登录后立即修改，再点击“我已保存，停止显示”。初始密码、数据库密码、
Redis 密码和 Token 不进入启动器日志或诊断信息。

首次证书是本机自签名证书，浏览器可能提示不受信任。只允许在确认地址为
`https://127.0.0.1:<端口>` 且证书由本机启动器生成时继续；正式工作室终端建议由管理员
将生成的证书加入本机信任存储。生产代码没有忽略 TLS 校验的逻辑。

## 数据位置

应用配置目录：

- Windows：`%APPDATA%\com.saberaltriayi.rehab\`
- macOS：`~/Library/Application Support/com.saberaltriayi.rehab/`

其中包含版本文件、版本化运行资源、权限受限的运行配置、TLS、加密备份和启动器锁。实际
MySQL、Redis、康复附件和服务日志位于 Docker 固定命名卷：

- `rehab-desktop-mysql-data`
- `rehab-desktop-redis-data`
- `rehab-desktop-rehab-data`
- `rehab-desktop-server-logs`

安装目录不保存数据库或附件。关闭启动器默认不停止服务；“停止服务”使用 Compose `stop`，
不删除容器数据。普通流程不执行 `docker compose down -v`。

## 启动器功能

- 启动、停止和重启服务；
- 显示 Docker、MySQL、Redis、后端和管理端状态；
- 健康后打开系统；
- 创建数据库与附件的 age 加密备份；
- 查看经过密码、Token、手机号和潜在患者字段过滤的最近日志；
- 打开用户数据目录；
- 复制不含患者数据和敏感配置的诊断信息；
- 在服务停止时修改本机 HTTP/HTTPS 端口；
- 通过精确确认文字和二次确认执行彻底删除。

默认只绑定 `127.0.0.1`。V1.0 启动器没有开放局域网高级选项，也不接受 `0.0.0.0`；
原有 `deploy/internal/` 继续负责经人工证书和防火墙验收的局域网部署。

## 更新与卸载

更新前点击“创建备份”，确认生成 `.sql.age`、`attachments.tar.gz.age` 和 JSON 清单。
安装新版本后，运行资源进入新的版本目录；Compose 项目名和卷名保持稳定。数据库迁移必须
继续使用固定校验和及 `internal_schema_history`，不能假设迁移可自动回滚。

Windows 从“已安装的应用”卸载；macOS 退出启动器后删除 `/Applications/康复管理系统.app`。
这两种普通卸载都只移除程序，不删除用户数据目录和 Docker 卷。重新安装后启动器会重新连接
原数据。

只有在启动器危险区输入 `删除所有本地数据` 并通过系统二次确认，才会停止服务、删除上述
四个固定卷和应用数据。Docker 不可用或卷删除失败时应先修复 Docker，避免误以为数据已清除。

## 安装包和自动化

本地构建前必须先生成运行资源：

```bash
deploy/internal/build-server-isolated.sh
cd yudao-ui/yudao-ui-admin-vue3-app
pnpm install --frozen-lockfile
pnpm build:internal
cd ../../..
node desktop/scripts/build-sanitized-bootstrap.mjs
node desktop/scripts/build-runtime.mjs
node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0
# 仅在无 rehab-desktop-* 现存卷的隔离 Docker 主机执行
desktop/scripts/test-runtime-e2e.sh
```

把 `desktop/runtime/1.0.0/` 放入
`desktop/launcher/src-tauri/resources/runtime/1.0.0/` 后：

```bash
cd desktop/launcher
pnpm install --frozen-lockfile
pnpm test
pnpm cargo:test
pnpm tauri build --bundles nsis --target x86_64-pc-windows-msvc
pnpm tauri build --bundles dmg --target universal-apple-darwin
```

Windows 安装器必须在 Windows x64 runner 构建；universal DMG 必须在 macOS runner 构建。
GitHub Actions 工作流是跨平台发布的标准入口。

更多内容：

- [Windows 安装与卸载](desktop-install-windows.md)
- [macOS 安装与卸载](desktop-install-macos.md)
- [备份与迁移](desktop-backup-migration.md)
- [签名与公证](desktop-signing.md)
- [故障排查](desktop-troubleshooting.md)
- [发布检查清单](desktop-release-checklist.md)
