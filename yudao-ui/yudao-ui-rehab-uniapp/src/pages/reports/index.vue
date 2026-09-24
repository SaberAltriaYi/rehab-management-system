<template>
  <view class="page-shell">
    <view class="card intro-card"><text class="section-title">评估报告</text><text class="muted">仅展示当前账号有权查看的报告摘要。</text></view>
    <view v-if="errorMessage" class="notice-box error-text">{{ errorMessage }}</view>
    <view v-if="reports.length === 0 && !busy" class="card empty-state">暂无可查看的评估报告。</view>
    <view v-for="report in reports" :key="report.id" class="card report-card">
      <view class="report-top">
        <view><text class="report-type">{{ report.reportType || '评估报告' }}</text><text class="report-no">{{ report.reportNo || '—' }}</text></view>
        <text class="status-pill">{{ report.reportStatus || '已生成' }}</text>
      </view>
      <view v-if="report.issueSummary" class="summary-block"><text class="summary-label">评估摘要</text><text>{{ report.issueSummary }}</text></view>
      <view v-if="report.recommendationSummary" class="summary-block"><text class="summary-label">建议摘要</text><text>{{ report.recommendationSummary }}</text></view>
      <text class="create-time">{{ formatDate(report.createTime) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getSession } from '@/lib/session'
import { patientApi } from '@/lib/rehab-api'

const reports = ref<Array<Record<string, any>>>([])
const busy = ref(false)
const errorMessage = ref('')
onShow(() => {
  if (getSession()?.mode !== 'patient') { uni.reLaunch({ url: getSession() ? '/pages/home/index' : '/pages/login/index' }); return }
  void loadReports()
})
async function loadReports(): Promise<void> {
  busy.value = true; errorMessage.value = ''
  try { const result = await patientApi.reports(); reports.value = result?.list || [] }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '读取报告失败。' }
  finally { busy.value = false }
}
function formatDate(value?: string): string { return value ? value.replace('T', ' ').slice(0, 16) : '' }
</script>

<style scoped>
.intro-card .section-title { margin-bottom: 10rpx; }
.empty-state { color: #7e8a9b; padding: 54rpx 22rpx; text-align: center; }
.report-card { padding: 26rpx; }
.report-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.report-type { display: block; color: #243248; font-size: 27rpx; font-weight: 700; }
.report-no { display: block; margin-top: 7rpx; color: #8792a1; font-size: 20rpx; }
.status-pill { padding: 8rpx 13rpx; border-radius: 99rpx; background: #eef5ff; color: #276bdb; font-size: 20rpx; }
.summary-block { display: flex; flex-direction: column; gap: 7rpx; margin-top: 20rpx; color: #4a586c; font-size: 22rpx; line-height: 1.6; }
.summary-label { color: #8792a1; font-size: 20rpx; }
.create-time { display: block; margin-top: 18rpx; color: #939dac; font-size: 19rpx; text-align: right; }
</style>
