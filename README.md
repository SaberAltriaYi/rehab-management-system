# 康复管理系统

> 本项目基于 **芋道源码（RuoYi-Vue-Pro）进行二次开发**，用于康复工作室内部业务管理。
> 本仓库不是芋道官方项目；“芋道”“RuoYi-Vue-Pro”及其相关标识的权利归原项目及其作者所有。

## 上游项目与开源说明

- 芋道 / RuoYi-Vue-Pro（GitHub）：<https://github.com/YunaiV/ruoyi-vue-pro>
- 芋道 / RuoYi-Vue-Pro（Gitee）：<https://gitee.com/zhijiantianya/ruoyi-vue-pro>
- 芋道 Vue3 管理后台：<https://gitee.com/yudaocode/yudao-ui-admin-vue3>
- 本项目保留原项目版权声明，并继续遵循仓库中的 [MIT License](LICENSE)。

本次二次开发主要增加和调整康复业务流程、SFMA 评估、内部部署及安全配置。若要了解芋道框架自身的能力、文档和更新，请以上游项目为准。

## 当前交付范围

系统面向同一工作室内的授权成员，支持：

- 患者档案、康复病程与预约管理
- SFMA 等评估、报告、康复计划和训练任务
- 签到、进度记录、复评、随访和通知
- 风险分级、权限控制和后台管理
- 同一 Wi-Fi / 局域网内的电脑、平板和手机浏览器访问

当前交付约束：

- **AI 功能保持关闭**，不需要配置任何 AI 密钥。
- 默认只用于可信局域网，不建议直接暴露到公网。
- 商城、支付、IoT、工作流等非康复核心模块不在本次内部交付范围内。
- 本系统用于辅助专业人员记录和管理，不替代医疗诊断或临床判断。

## 技术栈

| 部分 | 主要技术 |
| --- | --- |
| 后端 | Java 8、Spring Boot 2.7.18、Maven、MyBatis Plus |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、pnpm |
| 数据 | MySQL 8.4、Redis 7.4 |
| 部署 | Docker Compose、Nginx、局域网 HTTPS |

## 最快部署方式

推荐从已签名的预发布版本下载部署包：

[下载 rehab-lan-v1.1.0-rc1](https://github.com/SaberAltriaYi/rehab-management-system/releases/tag/rehab-lan-v1.1.0-rc1)

部署设备需要先安装 Docker Desktop（或兼容 Docker Compose 的 Docker Engine）。解压部署包后执行：

### macOS / Linux / NAS

```bash
chmod +x install.sh
./install.sh 192.168.1.100
```

### Windows PowerShell

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1 192.168.1.100
```

将示例 IP 替换为部署设备在当前局域网中的固定 IP。安装完成后：

1. 在每台使用设备上访问 `http://部署设备IP:8080/ca.crt`，安装局域网证书。
2. 打开 `https://部署设备IP:8443`。
3. 首次登录信息位于部署包内的 `deploy/lan/FIRST_LOGIN.txt`。
4. 首次登录后立即修改初始密码，并安全删除首次登录信息文件。

详细说明见 [局域网部署文档](deploy/lan/README.md)。

## 从源码开发与构建

### 后端

```bash
mvn -pl yudao-module-rehab test
./deploy/internal/build-server-isolated.sh
```

### 前端

```bash
cd yudao-ui/yudao-ui-admin-vue3-app
pnpm install --frozen-lockfile
pnpm build:internal
```

### 发布前回归

```bash
./script/rehab/internal_release_regression.sh
```

更多内部部署说明见 [内部部署文档](deploy/internal/README.md)。

## 安全要求

- 不要提交 `.env`、私钥、数据库备份密钥或 `FIRST_LOGIN.txt`。
- 为每位工作室成员创建独立账号，并按职责分配最小权限。
- 不要在路由器上为本系统配置公网端口映射。
- 定期备份数据库，并实际验证备份可以恢复。
- AI 功能继续保持关闭，除非后续完成单独的隐私、安全和成本评审。
- 上线前逐项完成 [部署前检查清单](DEPLOYMENT_CHECKLIST.md)。

## 运维资料

- [局域网部署与一键安装](deploy/lan/README.md)
- [内部生产部署说明](deploy/internal/README.md)
- [部署前检查清单](DEPLOYMENT_CHECKLIST.md)
- [安全风险登记表](deploy/internal/SECURITY_RISK_REGISTER.md)

## 致谢

感谢芋道源码（RuoYi-Vue-Pro）及其社区提供的开源基础。本项目是在该开源框架之上的康复业务二次开发和内部部署适配。
