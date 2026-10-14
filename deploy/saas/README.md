# SaaS V2 开发候选部署

此目录是开发候选配置，**不是已验收的 Beta 安装包**。禁止直接用于生产。
Enterprise 历史包与分支不受影响；不要向已有 Enterprise 容器或数据卷套用此覆盖文件。

## 已有能力
- 机构注册申请、平台列表、审批建机构和管理员、拒绝、暂停、恢复/续期、创建与查询套餐。
- 独立 platform_admins 授权；租户管理员不是平台管理员。
- SaaS HTTP 与租户后台任务订阅门禁，保留原鉴权、租户身份比对和业务 RBAC。
- migration 028 注册队列与平台操作审计，不包含账户密码或真实患者信息。

## 构建与配置
```bash
# 在 feature/saas-platform-v2 工作树中，以 JDK 17 执行
JAVA_HOME="$(/usr/libexec/java_home -v 17)" deploy/internal/build-server-isolated.sh
cd yudao-ui/yudao-ui-admin-vue3-app
pnpm install --frozen-lockfile
pnpm build:internal
```
此分支 .env.internal 已启用 VITE_SAAS_ENABLE，构建器仍会剔除外部 VITE_* 注入，保留构建凭据校验。

独立测试环境需要自行生成新的数据库/Redis 随机密码和可信 TLS 测试证书；不得复用业务环境 .env、证书私钥或卷。
底层 internal Compose 的证书挂载路径和 HTTPS 约束保持不变。完成内部预检后，使用独立 Compose project 合并此覆盖文件，例如：
```bash
docker compose --project-name rehab-saas-synthetic-UNIQUE \
  --env-file /absolute/path/to/synthetic-only.env \
  -f deploy/internal/docker-compose.yml \
  -f deploy/saas/docker-compose.saas.yml config --quiet
```
以上命令只验证 Compose 配置，不会安装或启动。另有 `install-synthetic.sh` 开发安装脚本，仅接受新目录与全新独立合成环境，并生成私密首次账号文件；仍不能把 config 成功或健康检查成功视为可上线。
不要输出完整 compose config 到日志，因为插值结果可能含密码。

后端模式：yudao.deployment.mode=saas、yudao.tenant.enable=true、yudao.license.enforce=false。
SaaS 模式以服务器订阅门禁替代 Enterprise License 门禁，并非无授权模式；隔离关闭或同时启用 Enterprise License 时启动失败。
平台管理员必须由受控引导流程建立，普通注册 API 无法授予平台权限。平台自身租户登录也需要有效订阅。

## 页面与接口
- /saas/register：机构入驻（公开、按 IP 限频；不收集密码或患者资料）。
- /saas/platform：平台页面；服务器校验平台身份。UI 支持审核、拒绝、暂停、恢复/续期、创建套餐及用量查看。
- /admin-api/system/saas/platform/renew：恢复/续期（tenantId、expires）。
- /admin-api/system/saas/platform/plans POST：创建套餐（name、maxUsers、maxPatients、motionQuota、storageQuota）。
审批需选择有效的业务权限套餐 packageId，不允许系统套餐 0；临时密码必须通过安全渠道交付。

## 发布阻断（不能绕过）
- 审批中真实 TenantService / UserService 的混合事务完整回滚与并发行为尚未进行完整应用集成验证。
- 全业务配额写入仍未完成。治疗师一次性邀请已实现 API 与页面及 H2 事务测试，但真实应用/MySQL 并发完整闭环待验收；患者身份与授权关联仍未完成。
- 原患者端有明确的安全拒绝门禁；本次没有移除。不能用患者编号+手机号冒充身份认证。
- 桌面 SaaS OAuth、生产级首次平台账号引导及跨平台安装包尚未交付。合成环境安装脚本不等于生产安装器。
- 云端域名、TLS、备份恢复、全链路合成验收仍需验证。
支付不在本阶段范围内。

## 2026-10-13 新增开发功能
- `/saas/staff`：本机构管理员创建/撤销/查看治疗师邀请。需先配置 `rehab_therapist` 角色及其业务权限。
- `/saas/join`：输入机构编号和一次性凭据接受邀请，不继承已有浏览器会话，也不自动登录。
- migration 029 仅存储租户/用途域隔离的 256 位随机邀请凭据的 SHA-256 摘要；72 小时有效，单次使用。
- 邀请领取锁定订阅及邀请行；邀请范围内账号配额按实际非删除账号数核验。其他创建入口尚未使用同一互斥锁，不能宣称全系统配额并发已保证。
- 邀请不自动发送邮件，管理员必须通过可信渠道交付本人。不得把凭据放在 URL 查询参数或日志中。

## 合成环境安装（开发验收，不是生产部署）
先以 JDK 17 构建后端、提交已审查源码、再执行 `pnpm build:internal` 取得匹配该提交的前端凭据。
```bash
bash deploy/saas/install-synthetic.sh --synthetic-only /absolute/new/saas-synthetic-directory
```
脚本拒绝已有目录与脏工作树，使用独立随机 Compose project、随机端口、全新随机密钥、脱敏基线及仅绑定本机的 TLS。
首次账号只写入该目录的 `FIRST_LOGIN.txt`（权限 0600），不输出到终端。整个目录包含密钥，禁止提交或打包公开。
首次登录后修改密码。停止环境时使用该目录的 Compose project 执行 `stop`；不要对历史项目操作，也不要执行 `down -v`。
健康检查通过不代表 P6 全业务闭环通过；此脚本不包含动作引擎或已认证患者门户，也不用于升级既有环境。

## 线上与安装包资源门槛
云端域名、独立云主机/账号、DNS/TLS 管理权限、备份目的地、告警接收人仍须核实后才可部署。
2026-10-13 本机 `security find-identity -v -p codesigning` 返回 0 个有效身份；不可声称已生成签名/公证的 macOS 包。
Windows 原生 runner、签名与安装/卸载验证仍未完成。历史 Enterprise workflow 排除 `v2-saas-*` 标签，避免误发旧版安装器。

## 统一 Engine 隔离安装（DEV）

`install-synthetic.sh` 支持可选 `REHAB_ENGINE_IMAGE`，必须是
`ghcr.io/saberaltriayi/rehab-engine-dev@sha256:<64位digest>`，不接受可变 tag。
先由部署管理员认证 GHCR 并拉取对应镜像；SaaS 终端用户不需要镜像仓库凭据。

```bash
REHAB_ENGINE_IMAGE='ghcr.io/saberaltriayi/rehab-engine-dev@sha256:<实际核验的digest>' \
  bash deploy/saas/install-synthetic.sh --synthetic-only /absolute/new/private-state
```

- 状态目录必须不存在；不会修改旧数据库、卷或旧端口。
- 启用 Engine 时生成新服务令牌，私密写入 `.env`，不打印；Engine 无宿主机映射端口。
- Engine 为非 root、只读文件系统、移除 capabilities、禁止提权；主服务通过内部 Bearer 通道调用。
- Java 服务使用独立 SaaS JDK 17 容器定义，不修改 Enterprise 容器定义。
- `version-lock.json` 记录主项目源码、实际服务镜像 ID、Engine digest，明确 `clinical_accepted=false`。
- 未指定 Engine 时仍为原四服务模式；启用后为五服务模式。健康检查通过不代表完整临床流程验收。
- 本地合成证书不应作为公网信任根；不得使用全局 TLS 忽略开关上线。

容器发布流水线 `saas-ghcr-dev.yml` 保留完整所选后端测试、前端类型检查与生产依赖审计门禁。
检查失败时不发布服务端／管理端镜像，不能用独立连接客户端成功来替代这些检查。
