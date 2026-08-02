# 运动康复评估与业务管理系统

简称：**康复管理系统**

版本：**V1.0 / 1.0.0**

这是一个面向运动康复工作室内部使用的本地管理系统，覆盖患者建档、康复周期、评估、报告、
训练计划、训练执行、进度追踪、风险提醒、随访和复评等业务。

项目基于芋道 `ruoyi-vue-pro` 二次开发。康复业务、桌面启动器、内部部署、安全加固和发布流程
由本项目补充；上游作者、版权信息及 MIT License 保持不变。

> 本系统用于康复评估和业务记录，不替代医疗诊断。当前版本默认关闭 AI、商城、支付、IoT、
> BPM 等未交付模块，不包含遥测、广告、第三方统计或患者数据云上传。

## 下载桌面安装包

桌面测试版发布页：

[下载康复管理系统 V1.0 桌面预发布修复版 preview.2](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/desktop-v1.0.0-preview.2)

| 平台 | 安装包 | 支持范围 |
| --- | --- | --- |
| Windows | `rehab-management-system_1.0.0_windows-x64_unsigned-setup.exe` | Windows 10/11 x64 |
| macOS | `rehab-management-system_1.0.0_macos-universal_unsigned.dmg` | Apple Silicon 与 Intel |

安装包内已经包含编译后的 Spring Boot 后端、Vue 管理端、Docker Compose、Nginx 配置和数据库
初始化资源。最终用户不需要安装 JDK、Maven、Node.js 或 pnpm。

### 当前预发布版说明

当前安装包尚未完成 Windows Authenticode 签名和 Apple Developer ID 公证，因此文件名带
`_unsigned`，Windows SmartScreen 或 macOS Gatekeeper 可能显示安全提醒。它们只用于可信
工作室内部测试，不建议作为公开生产发行包传播。

下载后请同时下载发布页中的 `SHA256SUMS.txt` 并校验文件。不要使用来源不明或校验不一致的
安装包。

## 主要功能

| 模块 | 功能 |
| --- | --- |
| 工作台 | 我的患者、待复评、高风险、低依从性及近期业务概览 |
| 患者管理 | 患者档案、标签、状态、治疗师归属、附件和操作记录 |
| 康复周期 | 以 Episode 管理一次完整康复周期及阶段流转 |
| 康复评估 | 静态评估、身体成分、NASM-CES、SFMA、FMS、YBT、OpenCap、观察和综合评估 |
| SFMA | 按顶层动作筛查、分解评估和纠正方向记录评估过程 |
| 评估报告 | 汇总模块结果、风险、结论及建议，支持报告生成与导出 |
| 训练计划 | 为患者和康复周期创建、调整、复制、启用和归档计划 |
| 训练任务 | 将计划拆分为可执行训练任务，管理动作、频次和完成要求 |
| 训练打卡 | 记录训练完成、疼痛、不适、备注及依从性 |
| 进度追踪 | 汇总任务执行、训练完成率和近期康复趋势 |
| 复评管理 | 复评触发、到期提醒、复测结果和前后变化对照 |
| 风险提醒 | 疼痛、低依从性、待复评和持续高风险提醒 |
| 随访通知 | 记录随访备注，向患者侧生成可读提醒 |
| 后台管理 | 用户、角色、菜单、部门、租户、日志、文件和系统配置 |
| 审计与权限 | 登录认证、租户隔离、患者可见范围、操作日志和最小权限控制 |

当前没有独立预约模块。可交付业务链路为：

```text
患者建档
  → 创建康复周期 Episode
  → 完成初评/专项评估
  → 审核并导出评估报告
  → 制定训练计划
  → 下发训练任务
  → 训练打卡与随访
  → 查看进度和风险
  → 触发复评
  → 调整计划或结束康复周期
```

## 技术架构

| 层级 | 技术 |
| --- | --- |
| 桌面启动器 | Tauri v2、Rust、TypeScript、Vite |
| 管理端 | Vue 3、Vite、Element Plus、Pinia |
| 后端 | Java 8、Spring Boot 2.7.18、Maven 多模块 |
| 权限与会话 | Spring Security、Token、Redis、多租户 |
| 数据库 | MySQL 8.4 |
| 缓存 | Redis 7.4 |
| 网关与静态资源 | Nginx 1.30 |
| 容器编排 | Docker Compose v2 |
| 自动化发布 | GitHub Actions、NSIS、DMG |

桌面安装包不是把 MySQL 或 Redis 改成嵌入式数据库。V1.0 继续使用 Docker Compose 运行
MySQL、Redis、Spring Boot 和 Nginx，以保持现有数据模型、事务、迁移脚本和部署方式。

## 桌面版安装

### 前置条件

1. 安装当前受支持的 Docker Desktop。
2. 启动 Docker Desktop，并等待 Engine Running。
3. 确认 `docker compose version` 可以正常执行。
4. 建议至少 2 核 CPU、4 GB 可用内存和 20 GB 可用磁盘。
5. 首次启动需要能够访问 Docker 镜像仓库。

启动器只检测 Docker，不会静默下载或安装 Docker Desktop。

### Windows

1. 从发布页下载 Windows `.exe` 和 `SHA256SUMS.txt`。
2. 在 PowerShell 中校验：

   ```powershell
   Get-FileHash ".\rehab-management-system_1.0.0_windows-x64_unsigned-setup.exe" -Algorithm SHA256
   ```

3. 运行安装器。安装器会创建开始菜单入口，并可选择创建桌面快捷方式。
4. 启动 Docker Desktop。
5. 从开始菜单打开“康复管理系统”。

Windows 版本不要求 Git Bash，也不要求用户在 WSL 中运行脚本。

详细说明见 [Windows 安装与卸载](docs/desktop-install-windows.md)。

### macOS

1. 从发布页下载 universal DMG 和 `SHA256SUMS.txt`。
2. 在终端中校验：

   ```bash
   shasum -a 256 "rehab-management-system_1.0.0_macos-universal_unsigned.dmg"
   ```

3. 打开 DMG，将“康复管理系统”拖入 Applications。
4. 启动 Docker Desktop。
5. 从 Applications 打开“康复管理系统”。

详细说明见 [macOS 安装与卸载](docs/desktop-install-macos.md)。

不要通过全局关闭 Gatekeeper 或 TLS 校验来部署正式版本。

## 第一次启动

首次点击“启动服务”时，启动器会：

1. 检查 Docker CLI、Docker daemon 和 Compose v2。
2. 校验并复制安装包内的 V1.0.0 运行资源。
3. 生成彼此不同的 MySQL 业务密码、MySQL root 密码和 Redis 密码。
4. 生成备份加密口令和本机 TLS 证书。
5. 检查默认端口 `8080` 和 `8443`；冲突时提示修改端口。
6. 拉取固定版本、固定摘要的基础镜像。
7. 创建 MySQL、Redis、康复附件和日志数据卷。
8. 初始化空数据库并执行 19 个受校验迁移版本。
9. 启动 MySQL、Redis、后端和管理端并等待健康检查。
10. 显示首次登录信息。

默认登录信息：

| 项目 | 内容 |
| --- | --- |
| 租户 | `工作室内部` |
| 用户名 | `admin` |
| 初始密码 | 由启动器生成 16 位随机密码，并仅在首次启动阶段显示 |
| 默认地址 | `https://127.0.0.1:8443` |

首次登录后必须立即修改管理员密码，并为每位成员创建独立账号、角色和最小权限。日常工作不要
共用管理员账号。

早期 `desktop-v1.0.0-preview.1` 构建曾错误生成 48 位管理员临时密码。更新后的登录接口兼容
该旧临时密码；登录后请立即在“个人中心 → 密码设置”改为 4–16 位新密码。新安装包只生成
16 位管理员临时密码，MySQL、Redis 和备份口令仍保持 48 位。

首次浏览器访问可能出现本机证书提醒。长期使用时应将用户数据目录下
`config/tls/server.crt` 加入本机受信任证书存储，不要关闭浏览器 TLS 校验。

## 启动器使用

启动器主页提供：

- Docker、MySQL、Redis、后端和管理端状态；
- 启动、停止和重启服务；
- 服务健康后打开管理系统；
- 修改本机 HTTP/HTTPS 端口；
- 查看经过敏感信息脱敏的近期日志；
- 打开用户数据目录；
- 创建数据库和附件加密备份；
- 复制不含密码、Token 和患者记录的诊断信息；
- 查看最近备份和最近错误；
- 通过精确确认文字执行彻底删除。

关闭启动器默认不会停止后台服务。普通停止使用 `docker compose stop`，不会删除数据卷。

## 数据位置

应用配置、证书、日志索引和备份位于操作系统用户数据目录：

```text
Windows: %APPDATA%\com.saberaltriayi.rehab\
macOS:   ~/Library/Application Support/com.saberaltriayi.rehab/
```

业务数据使用以下 Docker named volumes：

```text
rehab-desktop-mysql-data
rehab-desktop-redis-data
rehab-desktop-rehab-data
rehab-desktop-server-logs
```

安装目录不保存数据库和患者附件。重启、普通更新、重装和卸载桌面程序都不会删除这些卷。

## 备份、更新与卸载

### 创建备份

在四个服务全部健康时点击“创建备份”。备份输出到用户数据目录的 `backups/`：

```text
rehab-<UTC时间>.sql.age
rehab-<UTC时间>-attachments.tar.gz.age
rehab-<UTC时间>.json
```

备份包含患者数据，必须存放在受控加密介质。备份文件和解密口令应分开保存，不得上传到公开
Issue、网盘或 Git 仓库。

### 更新

1. 在旧版本启动器中创建备份。
2. 退出启动器，但不删除数据。
3. 安装新版本。
4. 启动服务并完成健康检查。
5. 核对登录、患者数量、评估、附件和迁移账本。

升级不会自动回滚数据库迁移。失败时应保留旧数据卷、日志和升级前备份。

### 卸载

正常卸载只删除桌面程序，默认保留数据库、Redis、附件、日志和备份。只有在启动器仍可运行、
Docker 正常，并且用户完成危险区二次确认时，才允许删除全部本地数据。

禁止将以下命令作为普通停止或卸载操作：

```bash
docker compose down -v
```

完整说明见 [备份与迁移](docs/desktop-backup-migration.md)。

## 局域网部署

桌面 V1.0 默认只监听 `127.0.0.1`，不接受 `0.0.0.0`，也不自动配置局域网或路由器端口
转发。

需要同一 Wi-Fi/局域网访问时，可以使用局域网一键部署脚本。将示例 IP 替换成部署设备的固定
私网 IPv4：

macOS、Linux 或 NAS：

```bash
chmod +x install.sh
./install.sh 192.168.1.100
```

Windows PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1 192.168.1.100
```

安装完成后，在成员设备访问 `http://部署设备IP:8080/ca.crt` 安装局域网 CA，然后打开
`https://部署设备IP:8443`。首次登录信息只在部署设备本地生成，不进入发布包或 Git。
详细说明见 [局域网一键部署文档](deploy/lan/README.md)。

需要手工控制每个部署步骤时，使用经过独立验收的 `deploy/internal/` 方案：

```bash
cp deploy/internal/.env.example deploy/internal/.env
chmod 600 deploy/internal/.env

deploy/internal/generate-tls.sh
deploy/internal/generate-backup-key.sh
deploy/internal/preflight.sh

docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml up -d --build

deploy/internal/check-database.sh
deploy/internal/smoke-test.sh
```

必须选择部署机明确的私网 IPv4，禁止使用 `0.0.0.0`，并在成员设备安装局域网 CA。不要配置
公网端口转发。

详细说明见 [工作室内部部署手册](deploy/internal/README.md)。

## 本地开发

### 开发环境

- JDK 17 LTS（编译目标兼容 Java 8）
- Maven 3.9
- Node.js 20 或 22
- pnpm 10.15.1
- Rust stable
- Docker Desktop / Docker Engine + Compose v2

### 后端测试与构建

```bash
mvn -B -ntp -pl yudao-module-rehab -am test

mvn -B -ntp \
  -pl yudao-framework/yudao-spring-boot-starter-web \
  -am \
  -Dtest=ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

deploy/internal/build-server-isolated.sh
```

后端产物：

```text
yudao-server/target/yudao-server.jar
```

### 内部管理端

```bash
cd yudao-ui/yudao-ui-admin-vue3-app
pnpm install --frozen-lockfile
pnpm build:internal
```

前端产物：

```text
yudao-ui/yudao-ui-admin-vue3-app/dist-internal/
```

### 桌面启动器

```bash
cd desktop/launcher
pnpm install --frozen-lockfile
pnpm test
pnpm cargo:test
```

Windows 安装器：

```powershell
pnpm tauri build --bundles nsis --target x86_64-pc-windows-msvc
```

macOS universal DMG：

```bash
pnpm tauri build --bundles dmg --target universal-apple-darwin
```

### 最小运行资源

```bash
node desktop/scripts/build-sanitized-bootstrap.mjs
node desktop/scripts/build-runtime.mjs
node desktop/scripts/check-runtime.mjs desktop/runtime/1.0.0
```

只允许在没有现存 `rehab-desktop-*` 数据卷的隔离 Docker 环境运行真实端到端测试：

```bash
desktop/scripts/test-runtime-e2e.sh
```

## GitHub Actions 发布

工作流文件：`.github/workflows/desktop-release.yml`

支持：

- Actions 页面手动运行；
- 向 `master` 提交 Pull Request 时验证；
- 推送 `v*` 标签时构建两个平台；
- 先运行后端测试、前端构建和真实 Docker E2E；
- Windows runner 生成 NSIS；
- macOS runner 生成 universal DMG；
- 上传安装包、SHA-256 和构建信息；
- 标签构建全部通过后创建 draft Release；
- 没有签名 Secret 时仍生成明确标记为 unsigned 的测试构建。

正式发布前的签名配置见 [桌面签名与公证](docs/desktop-signing.md)。

## 项目目录

```text
.
├── desktop/
│   ├── launcher/                 # Tauri v2 桌面启动器
│   ├── runtime-template/         # 最小 Docker 运行模板
│   ├── scripts/                  # 运行资源构建、检查和 E2E
│   └── sql/                      # 脱敏空库处理
├── deploy/internal/              # 局域网内部部署与运维
├── docs/                         # 桌面安装、签名、备份和排障文档
├── sql/mysql/                    # MySQL 基线与迁移入口
├── yudao-module-rehab/           # 康复业务后端模块
├── yudao-server/                 # Spring Boot 启动模块
└── yudao-ui/
    └── yudao-ui-admin-vue3-app/  # Vue 3 内部管理端
```

## 安全与隐私

- 默认仅绑定 `127.0.0.1`；
- 不添加遥测、广告、第三方统计或患者数据上传；
- 三个基础设施密码彼此不同，首次启动随机生成；
- 密码、Token、患者字段和手机号不会进入诊断信息；
- 配置和密钥保存在用户数据目录，而非安装目录；
- Docker 镜像固定版本和摘要，不使用 `latest`；
- 患者附件限制文件类型、文件名和大小；
- 正常操作不会执行 `docker compose down -v`；
- 数据删除必须二次确认并输入精确确认文字；
- AI、商城、支付、IoT、BPM 默认关闭；
- 数据库迁移使用固定校验和账本，不假设可以自动回滚。

如需报告安全问题，请不要在公开 Issue 附加数据库、原始日志、截图、备份、密码、Token 或真实
患者信息。

## 已知限制

- 桌面版仍需要 Docker Desktop；
- 首次运行需要下载固定基础镜像；
- V1.0 桌面启动器仅支持本机访问；
- 没有独立预约模块；
- 没有自动更新服务；
- 数据恢复属于受控管理员流程，不在主页提供一键恢复；
- 当前发布为 unsigned 预览版，正式分发仍需 Windows 签名及 macOS 公证；
- 患者字段未做数据库列级加密，部署主机必须启用磁盘加密、账号隔离和加密备份。

## 相关文档

- [桌面版使用与部署](docs/desktop-packaging.md)
- [Windows 安装与卸载](docs/desktop-install-windows.md)
- [macOS 安装与卸载](docs/desktop-install-macos.md)
- [备份与迁移](docs/desktop-backup-migration.md)
- [签名与公证](docs/desktop-signing.md)
- [故障排查](docs/desktop-troubleshooting.md)
- [发布检查清单](docs/desktop-release-checklist.md)
- [局域网一键部署](deploy/lan/README.md)
- [内部生产部署](deploy/internal/README.md)
- [V1.0 变更记录](CHANGELOG.md)
- [V1.0 软件著作权材料目录](docs/software-copyright/v1.0/README.md)
- [权属说明](COPYRIGHT.md)
- [上游与第三方声明](NOTICE.md)

## 上游与许可证

本项目基于以下开源项目进行二次开发：

- 芋道 `ruoyi-vue-pro`：<https://github.com/YunaiV/ruoyi-vue-pro>
- 芋道 Vue3 管理后台：<https://gitee.com/yudaocode/yudao-ui-admin-vue3>
- 芋道开发文档：<https://doc.iocoder.cn/>

本项目不是芋道官方项目。“芋道”“RuoYi-Vue-Pro”及相关标识的权利归原项目及其作者所有。
本项目保留上游作者、提交历史、版权与许可证信息，具体许可条款见 [LICENSE](LICENSE)、
[NOTICE.md](NOTICE.md) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

使用、修改和分发前请同时遵守仓库中保留的上游许可证及第三方许可证。

SFMA、FMS、NASM-CES 等方法内容目前无授权或其他使用依据，不属于杨玺龙的软件著作权权利
主张；软件著作权鉴别材料排除相关规则、表单和说明。继续使用、展示或分发相关内容前，应取得
合法依据，或替换为经过审查的原创通用表达。自研代码及权利范围详见
[COPYRIGHT.md](COPYRIGHT.md)。
