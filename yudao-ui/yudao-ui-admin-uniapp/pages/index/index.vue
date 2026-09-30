<template>
  <view style="padding: 24rpx">
    <view class="card">
      <view style="font-size: 34rpx; font-weight: 700; margin-bottom: 16rpx">康复工作台</view>
      <view style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 12rpx">
        <view class="stat">负责患者：{{ summary.myPatientCount || 0 }}</view>
        <view class="stat">待复评：{{ summary.pendingReassessmentCount || 0 }}</view>
        <view class="stat">高风险：{{ summary.highRiskCount || 0 }}</view>
        <view class="stat">今日关注：{{ summary.todayNeedFocusCount || 0 }}</view>
        <view class="stat">未读通知：{{ summary.unreadNotificationCount || 0 }}</view>
        <view class="stat">待处理提醒：{{ summary.pendingAlertCount || 0 }}</view>
      </view>
    </view>

    <view class="card">
      <view style="font-weight: 600; margin-bottom: 12rpx">快捷入口</view>
      <view style="display: flex; gap: 12rpx; flex-wrap: wrap">
        <button size="mini" class="btn-plain" @click="go('/pages/patient/index')">我的患者</button>
        <button size="mini" class="btn-plain" @click="go('/pages/alert/index')">风险提醒</button>
        <button size="mini" class="btn-plain" @click="go('/pages/notification/index')">通知中心</button>
      </view>
    </view>

    <view class="card">
      <view style="font-weight: 600; margin-bottom: 12rpx">最近患者</view>
      <view v-if="patients.length === 0" style="color:#94a3b8">暂无数据</view>
      <view v-for="item in patients" :key="item.id" style="border-top:1rpx solid #f1f5f9; padding: 12rpx 0">
        <view style="font-weight:600">{{ item.name }}（{{ item.patientNo }}）</view>
        <view style="font-size:24rpx; color:#64748b">阶段：{{ item.currentStage }} | 风险：{{ item.hasHighRiskTrigger ? '高' : '常规' }}</view>
        <view style="margin-top: 8rpx">
          <button size="mini" class="btn-plain" @click="go(`/pages/patient/summary?id=${item.id}`)">查看摘要</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getDashboardSummary } from '../../api/dashboard'
import { getMyPatients } from '../../api/patient'

export default {
  data() {
    return {
      summary: {},
      patients: []
    }
  },
  onShow() {
    this.loadData()
  },
  methods: {
    go(url) {
      uni.navigateTo({ url })
    },
    async loadData() {
      this.summary = await getDashboardSummary()
      const page = await getMyPatients({ pageNo: 1, pageSize: 5 })
      this.patients = page.list || []
    }
  }
}
</script>

<style scoped>
.stat {
  background: #f8fafc;
  border-radius: 12rpx;
  padding: 18rpx;
  color: #0f172a;
}
</style>
