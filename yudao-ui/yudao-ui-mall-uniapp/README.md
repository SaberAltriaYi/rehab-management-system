# rehab-patient-mini (Step 5 MVP)

患者端 UniApp（报告摘要 + 计划 + 今日任务 + 打卡 + 历史 + 通知）。

## 功能页面
- 登录 `/pages/login/index`
- 身份绑定 `/pages/bind/index`
- 首页 `/pages/home/index`
- 我的报告 `/pages/report/index`
- 我的计划 `/pages/plan/index`
- 今日任务 `/pages/task/today`
- 打卡提交 `/pages/checkin/submit`
- 训练历史 `/pages/history/index`
- 通知中心 `/pages/notification/index`
- 我的 `/pages/profile/index`

## 接口前缀
- `http://127.0.0.1:48080/app-api`
- 统一封装在 `utils/request.js`

## 对接 API
- `POST /app-patient/auth/login`
- `POST /app-patient/auth/bind`
- `GET /app-patient/home/summary`
- `GET /app-patient/reports/page`
- `GET /app-patient/reports/get`
- `GET /app-patient/plan/current`
- `GET /app-patient/tasks/today`
- `POST /app-patient/checkin/create`
- `GET /app-patient/checkin/history`
- `GET /app-patient/profile`
- `GET /app-patient/notifications/page`
- `POST /app-patient/notifications/read`

## 说明
- v1 今日任务简化为 active plan 下的 active tasks。
- v1 采用「一天一条 checkin」规则，重复提交由后端拒绝并提示。
