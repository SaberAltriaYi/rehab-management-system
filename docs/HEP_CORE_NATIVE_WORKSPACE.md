# Rehab Core 内置 HEP 工作台

## 入口与身份

- Core Web：`http://127.0.0.1:5173/login`
- 导航：**康复管理 → 家庭康复 HEP**
- 页面：`/rehab/hep`；患者详情兼容入口：`/rehab/hep/console?patientId=<数字ID>`
- 患者仍从独立 HEP 患者 H5／微信小程序使用本人账号，治疗师不得代打卡。

主系统登录后由后端 `RehabHepWorkspaceController` 以当前治疗师／机构管理员用户 ID
和租户上下文临时签发 15 分钟 RS256 JWT，服务端连接本机 HEP API，
结果包裹为 Core `CommonResult` 返回给 Vue 页面。
主系统不会在浏览器保存第二套 HEP 登录令牌，也不向 HEP 传递任意自选租户或 URL。

## 角色和功能

| 角色 | Core 内可操作内容 |
| --- | --- |
| 康复治疗师 | 只查看已分配的 HEP 患者、创建草稿动作、上传本人草稿媒体、Core 开方签发、Core 处方导入、风险与反馈复核、模板管理 |
| 机构管理员 | 机构概况、Core 身份绑定、动作／模板审批、动作映射、会员准入、计划与打卡记录、7/30 天依从性、事件队列 |
| 患者 | 独立患者端查看本人处方、打卡、视频、疼痛与 RPE，不进入 Core 管理工作台 |

Core `@PreAuthorize` 对每个转发端点分别检查 HEP 权限，
HEP 服务再对角色、租户、治疗师分配关系和患者绑定进行独立校验。
机构管理员与治疗师不会因为能够打开 HEP 工作台就自动拥有彼此的管理权限。

## 数据和临床约束

- Core 继续拥有患者、疗程、治疗师和已签发临床处方的主数据。
- Core 已签发的处方必须经过明确 Core 动作 ID → HEP 已审核动作 ID 映射才可导入。
- HEP 负责训练快照、患者逐组打卡、疼痛、RPE、风险、视频及回传队列。
- 训练事件进入 Core 后由治疗师复核；不能代替复评、病历签发或临床判断。
- 主系统媒体上传默认使用 Core 当前 16MB 单文件限制，支持 JPG、PNG、MP4、WebM。
  HEP 私有存储的上传上限较大，但从 Core 上传受 Core 入口限制。
- 核心场景仍需使用经过授权的专用模拟机构账号进行浏览器到数据库的正向验收；
  现有自动化测试不能代替真实用户操作。

## 菜单迁移和环境

迁移：`sql/mysql/rehab-hep-native-workspace-v1.sql`。
为已存在的康复治疗师角色开放治疗师操作，为机构管理员开放 HEP 管理权限，
并在康复管理下创建菜单 `98900`。后续新建的机构若没有对应角色，仍需按机构角色
授权。变更权限后重新登录可刷新菜单／权限缓存。

Core 的 HEP 内部服务目标默认仅允许 `http://127.0.0.1:8088`，
不能通过这个转接层指定外部 URL。云端部署需将本机回环访问替换为
有认证的内部网关，并重新审核安全策略。

## 构建与验证

```bash
cd /Users/saber/my-project/rehab/rehab-management-system
mvn -pl yudao-module-rehab -am -Dtest=RehabHepWorkspaceControllerTest,RehabHepPrescriptionFlowTest -Dsurefire.failIfNoSpecifiedTests=false test

cd yudao-ui/yudao-ui-admin-vue3-app
pnpm ts:check
pnpm build:local

cd /Users/saber/my-project/hep-home-training-platform
mvn -f backend/pom.xml test
```

本机 LaunchAgent 名称为 `com.saber.rehab.core` 和 `com.saber.rehab.hep`。
未登录访问 `/admin-api/rehab/hep-workspace/*` 应返回 Core 业务码 401，
未经身份验证直接访问 HEP API 应返回 HTTP 401。
