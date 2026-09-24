<template>
  <view class="page-shell">
    <view class="welcome-card">
      <view>
        <text class="eyebrow">康复管理 · {{ mode === 'staff' ? '治疗师工作台' : '患者中心' }}</text>
        <text class="welcome-title">{{ mode === 'staff' ? '你好，今天也一起做好康复管理。' : '你好，' + (patientHome.patientName || '欢迎回来') }}</text>
        <text class="welcome-subtitle">{{ mode === 'staff' ? '查看患者进度与需要关注的事项。' : '按计划训练，记录每一次进步。' }}</text>
      </view>
      <text class="refresh-link" @tap="refresh">刷新</text>
    </view>
    <view v-if="errorMessage" class="notice-box error-notice">{{ errorMessage }}</view>

    <template v-if="mode === 'staff'">
      <view class="metric-grid">
        <view class="metric-card primary-metric"><text class="metric-value">{{ staffSummary.myPatientCount ?? 0 }}</text><text class="metric-label">我的患者</text></view>
        <view class="metric-card"><text class="metric-value">{{ staffSummary.pendingReassessmentCount ?? 0 }}</text><text class="metric-label">待复评</text></view>
        <view class="metric-card warning-metric"><text class="metric-value">{{ staffSummary.highRiskCount ?? 0 }}</text><text class="metric-label">高风险</text></view>
        <view class="metric-card"><text class="metric-value">{{ staffSummary.abnormalCheckinCount ?? 0 }}</text><text class="metric-label">异常打卡</text></view>
      </view>
      <view class="card focus-card">
        <view class="card-heading-row"><text class="section-title">今日关注</text><text class="small-count">{{ staffSummary.todayNeedFocusCount ?? 0 }} 项</text></view>
        <view class="focus-row"><text class="focus-dot orange"></text><text>待处理风险提醒</text><text class="focus-number">{{ staffSummary.pendingAlertCount ?? 0 }}</text></view>
        <view class="focus-row"><text class="focus-dot blue"></text><text>未读通知</text><text class="focus-number">{{ staffSummary.unreadNotificationCount ?? 0 }}</text></view>
      </view>
      <view class="card">
        <text class="section-title">快捷入口</text>
        <view class="action-grid">
          <view class="action-tile" @tap="go('/pages/patients/index')"><text class="action-icon blue-icon">人</text><text class="action-title">我的患者</text><text class="action-caption">查看归属患者</text></view>
          <view class="action-tile" @tap="go('/pages/notifications/index')"><text class="action-icon orange-icon">铃</text><text class="action-title">通知提醒</text><text class="action-caption">{{ staffSummary.unreadNotificationCount ?? 0 }} 条未读</text></view>
        </view>
      </view>
    </template>

    <template v-else>
      <view class="card patient-summary-card">
        <view class="card-heading-row"><text class="section-title">康复概览</text><text class="stage-pill">{{ patientHome.currentStage || '阶段待更新' }}</text></view>
        <text class="summary-line">{{ patientHome.currentPlanSummary || '当前暂无训练计划摘要。' }}</text>
        <view class="patient-stat-row">
          <view><text class="patient-stat-value">{{ patientHome.todayTaskCount ?? 0 }}</text><text class="patient-stat-label">今日训练任务</text></view>
          <view><text class="patient-stat-value">{{ patientHome.unreadNotificationCount ?? 0 }}</text><text class="patient-stat-label">未读通知</text></view>
        </view>
      </view>
      <view class="card">
        <text class="section-title">今天的康复安排</text>
        <view class="info-row"><text class="info-label">最近打卡</text><text class="info-value">{{ patientHome.latestCheckinSummary || '暂无打卡记录' }}</text></view>
        <view class="info-row"><text class="info-label">复评提醒</text><text class="info-value">{{ patientHome.nextReassessmentReminder || '暂无提醒' }}</text></view>
        <view v-if="patientHome.precautions" class="precaution">注意事项：{{ patientHome.precautions }}</view>
      </view>
      <view class="card">
        <text class="section-title">快捷入口</text>
        <view class="action-grid">
          <view class="action-tile" @tap="go('/pages/tasks/index')"><text class="action-icon blue-icon">练</text><text class="action-title">今日训练</text><text class="action-caption">查看任务并打卡</text></view>
          <view class="action-tile" @tap="go('/pages/reports/index')"><text class="action-icon green-icon">报</text><text class="action-title">评估报告</text><text class="action-caption">查看可见摘要</text></view>
          <view class="action-tile" @tap="go('/pages/notifications/index')"><text class="action-icon orange-icon">铃</text><text class="action-title">通知提醒</text><text class="action-caption">{{ patientHome.unreadNotificationCount ?? 0 }} 条未读</text></view>
        </view>
      </view>
      <view class="notice-box safety-note">训练记录仅用于康复管理，不替代医疗诊断。若出现明显不适，请暂停训练并联系治疗师。</view>
    </template>

    <view class="footer-actions"><text class="muted">{{ busy ? '正在同步…' : '数据通过配置的康复服务读取' }}</text><text class="logout-link" @tap="logout">退出登录</text></view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { clearSession, getSession, type SessionMode } from '@/lib/session'
import { patientApi, staffApi } from '@/lib/rehab-api'

const mode = ref<SessionMode>('staff')
const busy = ref(false)
const errorMessage = ref('')
const staffSummary = ref<Record<string, any>>({})
const patientHome = ref<Record<string, any>>({})

onShow(refresh)

async function refresh(): Promise<void> {
  const session = getSession()
  if (!session) { uni.reLaunch({ url: '/pages/login/index' }); return }
  mode.value = session.mode
  busy.value = true
  errorMessage.value = ''
  try {
    if (session.mode === 'staff') staffSummary.value = (await staffApi.dashboard()) || {}
    else patientHome.value = (await patientApi.home()) || {}
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取工作台失败。'
  } finally { busy.value = false }
}

function go(url: string): void { uni.navigateTo({ url }) }
function logout(): void { clearSession(); uni.reLaunch({ url: '/pages/login/index' }) }
</script>

<style scoped>
.welcome-card { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 24rpx; padding: 30rpx 28rpx; border-radius: 26rpx; background: linear-gradient(135deg,#1d5fb9,#3e8bea); color: #fff; box-shadow: 0 18rpx 44rpx rgba(39,107,219,.2); }
.eyebrow, .welcome-title, .welcome-subtitle { display: block; }
.eyebrow { color: rgba(255,255,255,.76); font-size: 21rpx; }
.welcome-title { max-width: 520rpx; margin-top: 18rpx; font-size: 35rpx; font-weight: 750; line-height: 1.38; }
.welcome-subtitle { margin-top: 12rpx; color: rgba(255,255,255,.83); font-size: 22rpx; line-height: 1.5; }
.refresh-link { padding: 7rpx 0 7rpx 12rpx; color: #fff; font-size: 22rpx; }
.metric-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 18rpx; margin-bottom: 22rpx; }
.metric-card { display: flex; min-height: 150rpx; flex-direction: column; justify-content: center; padding: 22rpx 24rpx; border: 1rpx solid #e8ecf3; border-radius: 22rpx; background: #fff; }
.primary-metric { background: #edf5ff; border-color: #d9e9ff; }
.warning-metric { background: #fff8ed; border-color: #f4e6ca; }
.metric-value { color: #1b3352; font-size: 42rpx; font-weight: 800; }
.metric-label { margin-top: 8rpx; color: #778398; font-size: 22rpx; }
.card-heading-row { display: flex; align-items: center; justify-content: space-between; }
.focus-card .section-title, .patient-summary-card .section-title { margin-bottom: 18rpx; }
.small-count { color: #79869b; font-size: 22rpx; }
.focus-row, .info-row { display: flex; align-items: center; min-height: 62rpx; color: #435066; font-size: 24rpx; }
.focus-dot { width: 14rpx; height: 14rpx; margin-right: 14rpx; border-radius: 50%; }
.focus-dot.orange { background: #e8a441; } .focus-dot.blue { background: #3f84dd; }
.focus-number { margin-left: auto; color: #1d2b40; font-weight: 700; }
.action-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 16rpx; }
.action-tile { display: flex; min-height: 178rpx; flex-direction: column; align-items: flex-start; padding: 22rpx; border: 1rpx solid #edf0f5; border-radius: 19rpx; background: #fbfcfe; }
.action-icon { display: flex; width: 52rpx; height: 52rpx; align-items: center; justify-content: center; border-radius: 16rpx; font-size: 25rpx; font-weight: 750; }
.blue-icon { background: #e8f1ff; color: #276bdb; } .orange-icon { background: #fff2df; color: #bd7814; } .green-icon { background: #e8f7f0; color: #25835c; }
.action-title { margin-top: 14rpx; color: #28364c; font-size: 25rpx; font-weight: 700; }
.action-caption { margin-top: 7rpx; color: #8691a1; font-size: 20rpx; }
.stage-pill { padding: 10rpx 16rpx; border-radius: 99rpx; background: #edf5ff; color: #276bdb; font-size: 21rpx; }
.summary-line { display: block; margin: 4rpx 0 22rpx; color: #5c687b; font-size: 24rpx; line-height: 1.6; }
.patient-stat-row { display: flex; gap: 60rpx; padding-top: 18rpx; border-top: 1rpx solid #edf0f4; }
.patient-stat-row > view { display: flex; flex-direction: column; }
.patient-stat-value { color: #1d5fb9; font-size: 35rpx; font-weight: 800; }
.patient-stat-label { margin-top: 6rpx; color: #7a8799; font-size: 21rpx; }
.info-row { align-items: flex-start; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid #f0f2f6; }
.info-row:last-of-type { border-bottom: 0; }
.info-label { width: 140rpx; flex-shrink: 0; color: #8190a4; }
.info-value { flex: 1; color: #334155; line-height: 1.5; text-align: right; }
.precaution { margin-top: 16rpx; padding: 18rpx; border-radius: 14rpx; background: #fff7e8; color: #7c5a18; font-size: 22rpx; line-height: 1.55; }
.safety-note, .error-notice { margin-bottom: 20rpx; font-size: 22rpx; }
.footer-actions { display: flex; align-items: center; justify-content: space-between; margin: 28rpx 4rpx 0; font-size: 21rpx; }
.logout-link { padding: 10rpx; color: #60718a; }
</style>
