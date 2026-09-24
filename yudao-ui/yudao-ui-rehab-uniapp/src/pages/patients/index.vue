<template>
  <view class="page-shell">
    <view class="intro-card card">
      <text class="section-title">我的患者</text>
      <text class="muted">仅显示当前员工账号有权访问的患者摘要。</text>
      <text class="refresh-link" @tap="loadPatients">{{ busy ? '同步中…' : '刷新列表' }}</text>
    </view>
    <view v-if="errorMessage" class="notice-box error-text">{{ errorMessage }}</view>
    <view v-if="patients.length === 0 && !busy" class="empty-state card">暂无患者记录。</view>
    <view v-for="patient in patients" :key="patient.id" class="patient-card card" @tap="openSummary(patient)">
      <view class="patient-card-top">
        <view><text class="patient-name">{{ patient.name || '未命名患者' }}</text><text class="patient-no">{{ patient.patientNo || '—' }}</text></view>
        <text v-if="patient.hasHighRiskTrigger" class="risk-tag">需关注</text>
      </view>
      <view class="patient-meta"><text>{{ patient.currentStage || '阶段未设置' }}</text><text>最近打卡：{{ patient.latestCheckinDate || '暂无' }}</text></view>
      <text class="open-hint">点击查看康复摘要 ›</text>
    </view>
    <view v-if="selected" class="card summary-card">
      <view class="summary-header"><text class="section-title">患者康复摘要</text><text class="close-summary" @tap="selected = null">收起</text></view>
      <view class="summary-item"><text class="summary-label">患者</text><text>{{ selected.patient?.name || selected.patient?.patientNo || '患者' }}</text></view>
      <view class="summary-item"><text class="summary-label">最新评估</text><text>{{ selected.latestAssessmentSummary || '暂无摘要' }}</text></view>
      <view class="summary-item"><text class="summary-label">报告摘要</text><text>{{ selected.latestReportSummary || '暂无摘要' }}</text></view>
      <view class="summary-item"><text class="summary-label">训练计划</text><text>{{ selected.activePlanStatus || selected.activePlanNo || '暂无启用计划' }}</text></view>
      <view class="summary-item"><text class="summary-label">进度</text><text>{{ selected.latestProgressSummary || '暂无进度摘要' }}</text></view>
      <view class="summary-item"><text class="summary-label">待处理触发</text><text>{{ selected.pendingTriggerCount ?? 0 }}</text></view>
      <text class="summary-footnote">摘要仅用于工作流查看；诊疗判断应由有资质的专业人员结合完整档案完成。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getSession } from '@/lib/session'
import { staffApi, type PatientMini } from '@/lib/rehab-api'

const patients = ref<PatientMini[]>([])
const selected = ref<Record<string, any> | null>(null)
const busy = ref(false)
const errorMessage = ref('')

onShow(() => {
  if (getSession()?.mode !== 'staff') {
    uni.reLaunch({ url: getSession() ? '/pages/home/index' : '/pages/login/index' })
    return
  }
  void loadPatients()
})

async function loadPatients(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    const result = await staffApi.patients()
    patients.value = result?.list || []
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取患者列表失败。'
  } finally { busy.value = false }
}

async function openSummary(patient: PatientMini): Promise<void> {
  errorMessage.value = ''
  try { selected.value = await staffApi.patientSummary(patient.id) }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '读取患者摘要失败。' }
}
</script>

<style scoped>
.intro-card { position: relative; padding-bottom: 24rpx; }
.intro-card .section-title { margin-bottom: 10rpx; }
.refresh-link { display: block; margin-top: 18rpx; color: #276bdb; font-size: 23rpx; }
.empty-state { color: #8390a2; text-align: center; padding: 58rpx 24rpx; }
.patient-card { padding: 25rpx 26rpx; }
.patient-card-top, .patient-meta, .summary-header { display: flex; align-items: center; justify-content: space-between; }
.patient-name, .patient-no { display: block; }
.patient-name { color: #1f2c40; font-size: 29rpx; font-weight: 700; }
.patient-no { margin-top: 7rpx; color: #7d899a; font-size: 21rpx; }
.risk-tag { padding: 8rpx 14rpx; border-radius: 99rpx; background: #fff2e8; color: #b65b16; font-size: 20rpx; }
.patient-meta { margin-top: 18rpx; color: #718097; font-size: 21rpx; }
.open-hint { display: block; margin-top: 18rpx; color: #2869c7; font-size: 22rpx; text-align: right; }
.summary-card { margin-top: 26rpx; }
.summary-header .section-title { margin-bottom: 12rpx; }
.close-summary { color: #276bdb; font-size: 22rpx; }
.summary-item { display: flex; gap: 14rpx; padding: 16rpx 0; border-bottom: 1rpx solid #eff2f6; color: #3d4a5e; font-size: 22rpx; line-height: 1.5; }
.summary-label { width: 142rpx; flex-shrink: 0; color: #8792a2; }
.summary-footnote { display: block; margin-top: 16rpx; color: #828d9c; font-size: 20rpx; line-height: 1.55; }
</style>
