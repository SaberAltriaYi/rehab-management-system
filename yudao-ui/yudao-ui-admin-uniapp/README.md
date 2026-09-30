# rehab-admin-mini (Step 5 MVP)

管理端 UniApp（治疗师/文员移动辅助端），对接已有 rehab 后端接口。

## 功能页面
- 登录 `/pages/login/index`
- 工作台 `/pages/index/index`
- 我的患者 `/pages/patient/index`
- 患者摘要 `/pages/patient/summary`
- 风险提醒 `/pages/alert/index`
- 打卡历史 `/pages/checkin/history`
- 随访备注 `/pages/followup/index`

## 接口前缀
- `http://127.0.0.1:48080/admin-api`
- 统一封装在 `utils/request.js`

## 对接 API
- `POST /app-admin/auth/login`
- `GET /app-admin/dashboard/summary`
- `GET /app-admin/patients/my-page`
- `GET /app-admin/patients/summary`
- `GET /app-admin/patients/checkins`
- `GET /app-admin/alerts/page`
- `POST /app-admin/followup-note/create`
- `GET /app-admin/followup-note/page`

## 说明
- 该端不替代 Web 后台，仅用于移动快速查看与随访记录。
- 数据权限由后端控制：治疗师默认仅可见自己负责患者。
