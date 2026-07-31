# 运动康复评估与业务管理系统

**软件简称：** 康复管理系统

**软件版本：** V1.0（构建版本 `1.0.0`）

**软件著作权人：** `杨玺龙`

本系统面向运动康复工作室内部成员，用于患者档案、功能评估、训练干预、进度跟踪、复评随访和内部业务管理。当前版本保持 AI 功能关闭，只在可信局域网内提供服务。

## 二次开发与开源说明

本项目基于芋道源码（RuoYi-Vue-Pro）及芋道 Vue3 管理后台进行二次开发，不是芋道官方项目。“芋道”“RuoYi-Vue-Pro”及其相关标识的权利归原项目及其作者所有。

- RuoYi-Vue-Pro（GitHub）：<https://github.com/YunaiV/ruoyi-vue-pro>
- RuoYi-Vue-Pro（Gitee）：<https://gitee.com/zhijiantianya/ruoyi-vue-pro>
- 芋道 Vue3 管理后台：<https://gitee.com/yudaocode/yudao-ui-admin-vue3>
- 本仓库继续保留并遵循 [MIT License](LICENSE)。
- 详细归属与第三方声明见 [NOTICE.md](NOTICE.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [COPYRIGHT.md](COPYRIGHT.md)。

## V1.0 功能范围

- 患者档案、病程、治疗师分配和训练排期管理
- 静态姿态、活动度、动作筛查、平衡、身体成分及专项评估
- 评估质控、风险分级、报告复核、审批、锁版和 DOCX/PDF 导出
- 康复计划、训练任务、签到、执行记录和阶段进度
- 复评触发、随访、通知、风险预警和业务工作台
- 成员账号、角色权限、审计日志和后台配置
- 同一 Wi-Fi / 局域网内的电脑、平板和手机浏览器访问

使用限制：

- AI 功能保持关闭，不需要配置任何 AI 密钥。
- 默认用于可信局域网，不建议直接暴露到公网。
- 本系统用于辅助专业人员记录和管理，不替代医疗诊断或临床判断。
- 商城、支付、IoT 等上游非康复模块不属于 V1.0 交付范围。
- SFMA、FMS、NASM-CES 方法内容目前无授权或其他使用依据，不属于杨玺龙的软著权利主张；申请鉴别材料排除相关规则、表单和说明，实际继续使用、展示或分发前应取得合法依据或替换为经审查的原创通用表达。

## 技术栈

| 部分 | 主要技术 |
| --- | --- |
| 后端 | Java 8、Spring Boot 2.7.18、Maven、MyBatis Plus |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、pnpm |
| 数据 | MySQL 8.4、Redis 7.4 |
| 部署 | Docker Compose、Nginx、局域网 HTTPS |

## 一键部署

V1.0 发布包名称统一为 `sports-rehab-management-system-1.0.0.tar.gz`。正式发布后可从 [GitHub Releases](https://github.com/SaberAltriaYi/rehab-management-system/releases) 下载；发布前也可按下方命令从源码构建。

部署设备需先安装 Docker Desktop，或支持 Docker Compose 的 Docker Engine。

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

1. 在使用设备访问 `http://部署设备IP:8080/ca.crt` 并安装局域网证书。
2. 打开 `https://部署设备IP:8443`。
3. 首次登录信息仅在安装设备本地生成到 `deploy/lan/FIRST_LOGIN.txt`，不会进入发布包或 Git。
4. 首次登录后立即修改临时密码，并安全删除该文件。

详见 [局域网一键部署文档](deploy/lan/README.md)。

## 开发、测试与发布

### 后端测试与构建

```bash
mvn -pl yudao-module-rehab -am test
./deploy/internal/build-server-isolated.sh
```

### 前端安装与构建

```bash
cd yudao-ui/yudao-ui-admin-vue3-app
pnpm install --frozen-lockfile
pnpm build:internal
```

### 发布前检查与打包

```bash
./script/rehab/check-repository-sensitive-materials.sh
./script/rehab/internal_release_regression.sh
./script/rehab/build-lan-release.sh 1.0.0
```

## 安全要求

- 不提交真实患者数据、`.env` 部署文件、账号密码、访问令牌、私钥、数据库备份、备份密钥或 `FIRST_LOGIN.txt`。
- 为每位工作室成员创建独立账号，并按职责分配最小权限。
- 不在路由器配置公网端口映射；公网使用前必须单独完成安全评审。
- 定期执行加密备份，并实际验证备份可恢复。
- AI 功能继续保持关闭，除非后续完成隐私、安全和成本评审。

## 文档

- [内部生产部署说明](deploy/internal/README.md)
- [部署前检查清单](DEPLOYMENT_CHECKLIST.md)
- [安全风险登记表](deploy/internal/SECURITY_RISK_REGISTER.md)
- [V1.0 变更记录](CHANGELOG.md)
- [V1.0 软件著作权材料目录](docs/software-copyright/v1.0/README.md)

## 许可证与致谢

本仓库保留上游作者、版权信息和 MIT License。V1.0 自研新增部分的权利范围以 [COPYRIGHT.md](COPYRIGHT.md) 及最终签署的权属文件为准。感谢芋道源码及其社区提供的开源基础。
