<template>
  <view style="padding:24rpx">
    <view class="card">
      <view style="font-size:34rpx;font-weight:700">{{ summary.patientName || '您好' }}</view>
      <view style="color:#64748b;margin-top:8rpx">当前阶段：{{ summary.currentStage || '-' }}</view>
      <view style="color:#64748b;margin-top:8rpx">下次复评：{{ summary.nextReassessmentReminder || '暂无' }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">核心信息</view>
      <view style="margin-top:10rpx">最新报告：{{ summary.latestReportSummary || '暂无' }}</view>
      <view style="margin-top:10rpx">当前计划：{{ summary.currentPlanSummary || '暂无当前训练计划' }}</view>
      <view style="margin-top:10rpx">今日任务数：{{ summary.todayTaskCount || 0 }}</view>
      <view style="margin-top:10rpx">最近打卡：{{ summary.latestCheckinSummary || '暂无' }}</view>
      <view style="margin-top:10rpx">未读通知：{{ summary.unreadNotificationCount || 0 }}</view>
      <view style="margin-top:10rpx;color:#b91c1c" v-if="(summary.unreadNotificationCount || 0) > 0">
        有新的提醒，请及时查看通知中心
      </view>
      <view style="margin-top:10rpx;color:#b45309">注意事项：{{ summary.precautions || '-' }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">AI 患者摘要（已审核）</view>
      <view style="margin-top:10rpx;color:#64748b" v-if="!aiSummary">暂无已审核 AI 摘要</view>
      <view style="margin-top:10rpx;white-space:pre-wrap;line-height:1.7" v-else>{{ aiSummary.renderedText }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">AI 随访提醒（已审核）</view>
      <view style="margin-top:10rpx;color:#64748b" v-if="!aiFollowup">暂无 AI 随访提醒</view>
      <view style="margin-top:10rpx;white-space:pre-wrap;line-height:1.7" v-else>{{ aiFollowup.renderedText }}</view>
    </view>

    <view class="card">
      <view style="display:flex;gap:10rpx;flex-wrap:wrap">
        <button size="mini" class="btn-primary" @click="go('/pages/task/today')">今日任务</button>
        <button size="mini" class="btn-plain" @click="go('/pages/report/index')">我的报告</button>
        <button size="mini" class="btn-plain" @click="go('/pages/plan/index')">我的计划</button>
        <button size="mini" class="btn-plain" @click="go('/pages/history/index')">训练历史</button>
        <button size="mini" class="btn-plain" @click="go('/pages/notification/index')">通知中心</button>
      </view>
    </view>
  </view>
</template>

<script>
import { getHomeSummary } from '../../api/home'
import { getLatestAiFollowup, getLatestAiSummary } from '../../api/ai'

export default {
  data() {
    return {
      summary: {},
      aiSummary: null,
      aiFollowup: null
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
      this.summary = await getHomeSummary()
      this.aiSummary = await getLatestAiSummary()
      this.aiFollowup = await getLatestAiFollowup()
    }
  }
}
</script>
