<template>
  <view style="padding:24rpx">
    <view v-if="!plan" class="card">暂无当前训练计划</view>
    <view v-else class="card">
      <view style="font-size:32rpx;font-weight:700">{{ plan.planName || plan.planNo }}</view>
      <view style="margin-top:8rpx">类型：{{ plan.planType }} | 状态：{{ plan.status }}</view>
      <view style="margin-top:8rpx">起止：{{ plan.startDate }} ~ {{ plan.endDate }}</view>
      <view style="margin-top:8rpx">任务数：{{ plan.taskCount || 0 }}</view>
      <view style="margin-top:8rpx;color:#64748b">阶段目标：{{ plan.shortTermGoalsJson || '-' }}</view>
      <view style="margin-top:8rpx;color:#b45309">注意事项：{{ plan.precautions || '-' }}</view>
      <view style="margin-top:8rpx;color:#b91c1c">禁忌：{{ plan.contraindications || '-' }}</view>
      <button class="btn-primary" style="margin-top:16rpx" @click="goTask">查看今日任务</button>
    </view>
  </view>
</template>

<script>
import { getCurrentPlan } from '../../api/plan'

export default {
  data() {
    return {
      plan: null
    }
  },
  onShow() {
    this.loadData()
  },
  methods: {
    goTask() {
      uni.navigateTo({ url: '/pages/task/today' })
    },
    async loadData() {
      this.plan = await getCurrentPlan()
    }
  }
}
</script>
