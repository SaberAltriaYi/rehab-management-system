# 芋道康复管理系统：工作室内部部署手册

本方案用于单台可信 Mac 主机和少量工作室成员，不对公网开放：

- Nginx `1.30.4-alpine`：HTTPS 管理端及同源 `/admin-api` 代理
- Spring Boot `2.7.18` / Java 8 运行时：业务 API
- Tomcat `9.0.120`、Jackson `2.21.4`、Netty `4.2.16.Final`、MySQL Connector/J `9.7.0`
- MySQL `8.4.10`：业务数据
- Redis `7.4.10-alpine`：缓存、验证码和登录会话
- Vue 3、Vite、Element Plus：内部管理端

仅 Nginx 的 HTTP 跳转端口和 HTTPS 端口绑定到指定局域网 IP。MySQL、Redis、Java 后端均只在
Compose 内部网络通信。AI 在前端、后端和数据库菜单三处关闭，不需要任何模型密钥。

## 一、部署边界

- 使用部署机明确的局域网 IPv4 地址，禁止 `0.0.0.0`。
- 成员通过 `https://部署机IP:8443` 访问；`http://部署机IP:8080` 只做 308 跳转。
- 成员设备必须安装并信任 `deploy/internal/certs/ca.crt`。
- 当前没有独立预约模块；可交付流程为患者、Episode、评估、报告、计划、任务、打卡、进度、
  复评、风险、随访和通知。
- 内部构建不包含 AI、商城、支付、IoT、BPM/表单设计器，也不加载外部统计、字体或图标服务。
- 患者字段不做数据库列级加密，安全边界由 FileVault、HTTPS、独立账号、最小权限和加密备份组成。

最低建议：2 核 CPU、2 GB 内存、20 GB 可用磁盘。附件增长后需监控 Docker 数据目录与
`rehab-data` 卷。

## 二、构建与自动检查

建议使用 JDK 17 LTS、Maven 3.9、Node.js 20/22 和项目锁定的 pnpm。

```bash
# 后端测试和隔离构建
mvn -pl yudao-module-rehab test
mvn -pl yudao-framework/yudao-spring-boot-starter-web \
  -Dtest=ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest test
deploy/internal/build-server-isolated.sh

# 内部前端
cd yudao-ui/yudao-ui-admin-vue3-app
corepack enable
pnpm install --frozen-lockfile
pnpm exec eslint src/views/rehab src/api/rehab src/utils/formatTime.ts
pnpm build:internal
pnpm audit:prod
cd ../../..
```

发布产物：

- `yudao-server/target/yudao-server.jar`
- `yudao-ui/yudao-ui-admin-vue3-app/dist-internal/`

后端发布脚本会在系统临时目录中复制源码并构建，再校验 JAR 中没有文件同步产生的
`* 2.class` 冲突副本。项目位于 iCloud、NAS 或其他同步目录时必须使用该脚本，不要直接把
工作区 `target/` 当作发布产物。

当前前端生产依赖审计为 0 个已知漏洞。四个容器镜像的操作系统包 High/Critical 为 0；
后端 Java 库扫描从 56 项降至 8 项条件型条目，均已完成适用性核对和补偿控制，详见
`deploy/internal/SECURITY_RISK_REGISTER.md`。可用固定 Trivy `v0.72.0` 复验：

```bash
TRIVY_BIN=/可信路径/trivy deploy/internal/security-scan.sh
```

预检还会扫描内部前端产物，拒绝已停用的
form-create/wangEditor 代码进入发布包。全仓库类型检查仍有上游存量问题，发布门禁为自定义康复
代码检查、内部生产构建、自动测试和真实浏览器回归。

## 三、首次配置

```bash
cp deploy/internal/.env.example deploy/internal/.env
chmod 600 deploy/internal/.env

# 分别生成 TLS 证书和备份密钥；已有文件默认不会被覆盖
deploy/internal/generate-tls.sh
deploy/internal/generate-backup-key.sh
```

三个基础设施密码必须彼此不同、至少 24 字符。不要提交或发送 `.env`、`*.key` 或备份密钥。

| 变量 | 必需 | 说明 |
| --- | --- | --- |
| `BIND_ADDRESS` | 是 | 部署机明确的局域网 IPv4 或 `127.0.0.1` |
| `APP_PORT` | 否 | HTTP 跳转端口，默认 `8080` |
| `TLS_PORT` | 是 | 当前固定为 `8443` |
| `DB_PASSWORD` | 是 | MySQL 业务账号密码，至少 24 字符 |
| `MYSQL_ROOT_PASSWORD` | 是 | MySQL 管理密码，至少 24 字符 |
| `REDIS_PASSWORD` | 是 | Redis 密码，至少 24 字符 |
| `DB_NAME` | 否 | 默认 `ruoyi-vue-pro` |
| `DB_USERNAME` | 否 | 默认 `yudao` |
| `BACKUP_KEY_FILE` | 是 | 加密备份密钥路径 |
| `JAVA_OPTS` | 否 | 默认 `-Xms256m -Xmx768m` |
| `TZ` | 否 | 默认 `Asia/Shanghai` |

TLS 证书包含部署时的 IP。部署机 IP 变化后需要轮换证书，并在成员设备重新安装新的 CA。

## 四、首次启动

```bash
deploy/internal/preflight.sh
docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml up -d --build
deploy/internal/check-database.sh
deploy/internal/smoke-test.sh
```

全新数据卷会初始化基础表、Quartz 表、34 张康复表、41 个核心外键和完整迁移账本，停用非本期
菜单、演示账号、演示租户、非登录 OAuth2 演示客户端和 AI，轮换弱演示 secret，并清除康复演示
数据。系统仅保留后台生成会话所需、且不具备外部授权类型的 `default` 内部客户端。首次登录后立即
修改初始管理员密码，为每位成员创建独立账号，按最小权限分配角色；日常工作不得共用管理员账号。

## 五、已有数据库升级

Docker 初始化目录只在 MySQL 数据卷为空时执行。已有数据库统一通过带固定 SHA-256 的迁移账本：

```bash
# 先创建加密配对备份
deploy/internal/backup.sh

# 查看状态并应用尚未执行的版本
deploy/internal/migrate.sh status
deploy/internal/migrate.sh apply

deploy/internal/check-database.sh
deploy/internal/smoke-test.sh
```

只有在已逐项确认某个旧库已经执行相应 SQL、但尚未创建账本时，才可使用
`deploy/internal/migrate.sh baseline 015`。不得猜测基线版本。当前发布清单共 19 个版本，
数据库必须显示全部登记且校验和一致。

不要对已有业务库手工执行 `clean-demo-rehab-data.sql` 或 `internal-hardening.sql`；这两个脚本只用于
全新内部部署初始化。

## 六、日常健康检查

```bash
script/rehab/internal_release_regression.sh

docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml ps
docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml logs --since=30m server
```

只读发布回归会执行：部署预检、数据库结构/数据完整性、迁移账本、HTTPS/安全头/鉴权、生产依赖审计
和前端高风险依赖产物扫描。数据库门禁要求 34 张康复表、41 个核心外键、InnoDB、utf8mb4、
租户字段与索引正确，且无重复业务编号、孤儿关系、跨租户关系、AI 启用记录、未交付模块菜单、
启用的非登录 OAuth2 客户端或弱演示 secret。

## 七、附件

- 允许：`pdf`、`jpg`、`jpeg`、`png`、`webp`、`heic`、`doc`、`docx`、`xls`、`xlsx`、`csv`、`txt`
- 单文件最大 16 MB
- Nginx 请求上限 32 MB，用于 multipart 开销
- 文件名会去除路径和控制字符并限制长度，失败上传会清理残留文件

数据库与附件必须作为同一恢复点成对备份。

## 八、备份、恢复和演练

```bash
# 创建 AES-256-CBC + PBKDF2-SHA256 加密备份，并签名校验清单
deploy/internal/backup.sh

# 只验证，不修改数据库；无 CONFIRM_RESTORE 时返回码为 2
deploy/internal/restore.sh backups/rehab-YYYYMMDD-HHMMSS

# 在完全独立的 Compose 项目进行破坏性恢复演练，结束后自动清理
deploy/internal/rehearse-restore.sh backups/rehab-YYYYMMDD-HHMMSS
```

生产恢复必须先安排停机窗口：

```bash
CONFIRM_RESTORE=RESTORE-REHAB-INTERNAL \
  deploy/internal/restore.sh backups/rehab-YYYYMMDD-HHMMSS
```

恢复脚本会先备份当前状态，然后恢复数据库和附件，最后运行冒烟测试。数据库迁移不假设可逆；
涉及结构变更的回滚必须恢复同一个加密备份目录。

建议每天备份，保留至少 7 个日备份和 4 个周备份。备份目录与 `backup.key` 必须存放在不同的
受控加密介质，至少每季度执行一次隔离恢复演练。

## 九、更新、停止与回滚

```bash
# 更新
deploy/internal/backup.sh
docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml up -d --build
script/rehab/internal_release_regression.sh

# 停止但保留所有数据卷
docker compose --env-file deploy/internal/.env \
  -f deploy/internal/docker-compose.yml stop
```

生产环境禁止执行 `docker compose down -v`，该命令会删除数据库、Redis、附件和日志卷。

## 十、上线人工门禁

自动化不能替代以下真实责任确认：

1. 安装成员设备 CA，确认浏览器无证书警告。
2. 修改初始管理员密码，为每位成员建立独立最小权限账号。
3. 在路由器确认没有公网端口转发；在 macOS 启用应用防火墙并复核可信局域网访问。
4. 将最新加密备份复制到部署机外，将 `backup.key` 单独保管。
5. 至少两名工作室成员完成真实设备核心流程验收。
6. 由业务负责人、发布负责人和备份保管人完成上线签字。
7. 发布负责人阅读并接受 `SECURITY_RISK_REGISTER.md` 中的 Java 8/Spring Boot 2 限定边界，
   将 JDK 17 / Spring Boot 3 迁移列入下一技术周期。

可直接使用 `deploy/internal/manual/` 中的表单，不得用自动测试结果代替人员姓名、日期和签字。
