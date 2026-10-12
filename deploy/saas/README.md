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
以上命令只验证 Compose 配置，不会安装或启动。完整首次账号加固和 SaaS bootstrap 尚未集成，不能直接把 config 成功视为可上线。
不要输出完整 compose config 到日志，因为插值结果可能含密码。

后端模式：yudao.deployment.mode=saas、yudao.tenant.enable=true、yudao.license.enforce=false。
SaaS 模式以服务器订阅门禁替代 Enterprise License 门禁，并非无授权模式；隔离关闭或同时启用 Enterprise License 时启动失败。
平台管理员必须由受控引导流程建立，普通注册 API 无法授予平台权限。平台自身租户登录也需要有效订阅。

## 页面与接口
- /saas/register：机构入驻（公开、按 IP 限频；不收集密码或患者资料）。
- /saas/platform：平台页面；服务器校验平台身份。当前 UI 支持审核、拒绝、暂停、套餐与用量查看。
- /admin-api/system/saas/platform/renew：恢复/续期（tenantId、expires）。
- /admin-api/system/saas/platform/plans POST：创建套餐（name、maxUsers、maxPatients、motionQuota、storageQuota）。
审批需选择有效的业务权限套餐 packageId，不允许系统套餐 0；临时密码必须通过安全渠道交付。

## 发布阻断（不能绕过）
- 审批中真实 TenantService / UserService 的混合事务完整回滚与并发行为尚未进行完整应用集成验证。
- 全部业务入口的配额计数、邀请治疗师、患者身份验证与授权关联未完成。
- 原患者端有明确的安全拒绝门禁；本次没有移除。不能用患者编号+手机号冒充身份认证。
- 桌面 SaaS OAuth 客户端、首次平台账号引导、完整安装脚本和新 DMG 尚未交付。
- 云端域名、TLS、备份恢复、全链路合成验收仍需验证。
支付不在本阶段范围内。
