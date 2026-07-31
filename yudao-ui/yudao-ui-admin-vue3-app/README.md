# 康复管理系统 Web 管理端

**正式软件名称：** 运动康复评估与业务管理系统

**版本：** V1.0（`1.0.0`）

**权利人占位：** `[软件著作权人名称]`

本目录是康复管理系统的 Vue 3 管理端，提供患者、评估、报告、训练计划、训练执行、进度、复评、随访、风险预警和后台管理界面。AI 功能在 V1.0 内部版本中保持关闭。

## 本地开发

```bash
pnpm install --frozen-lockfile
pnpm dev
```

默认 API 路径由 `.env` 与对应模式文件配置。账号、密码、统计代码、地图密钥和 API 加密密钥不得写入受版本控制的配置。

## 内部生产构建

```bash
pnpm build:internal
```

构建产物位于 `dist-internal/`，由仓库根目录的 Docker Compose 与 Nginx 配置部署。

## 上游项目与许可证

本前端基于芋道 Vue3 管理后台二次开发：

- 上游项目：<https://gitee.com/yudaocode/yudao-ui-admin-vue3>
- 关联后端：<https://github.com/YunaiV/ruoyi-vue-pro>
- 许可证：[MIT License](LICENSE)

本目录保留上游作者和许可证信息。康复业务页面、接口适配和内部部署改造的权利范围以仓库根目录的 `NOTICE.md`、`THIRD_PARTY_NOTICES.md` 与 `COPYRIGHT.md` 为准。
