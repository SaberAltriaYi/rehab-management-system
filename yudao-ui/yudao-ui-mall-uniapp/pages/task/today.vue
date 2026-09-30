<template>
  <view style="padding:24rpx">
    <view v-if="tasks.length === 0" class="card">暂无今日任务</view>
    <view v-for="(task, index) in tasks" :key="task.id" class="card">
      <view style="font-size:30rpx;font-weight:700">{{ index + 1 }}. {{ task.taskName }}</view>
      <view style="margin-top:8rpx">剂量：{{ task.dosageText || `${task.sets || '-'}组 x ${task.repetitions || '-'}次` }}</view>
      <view style="margin-top:8rpx">频率：每周 {{ task.frequencyPerWeek || '-' }} 次 | 节奏：{{ task.tempo || '-' }}</view>
      <view style="margin-top:8rpx;color:#64748b">提示：{{ task.instructionText || '-' }}</view>
      <view style="margin-top:8rpx;color:#b91c1c">疼痛限制：{{ task.painLimitRule || '-' }}</view>
    </view>

    <button class="btn-primary" @click="goCheckin" v-if="tasks.length">提交今日打卡</button>
  </view>
</template>

<script>
import { getTodayTasks } from '../../api/plan'

export default {
  data() {
    return {
      tasks: []
    }
  },
  onShow() {
    this.loadData()
  },
  methods: {
    goCheckin() {
      uni.setStorageSync('REHAB_PATIENT_TODAY_TASKS', this.tasks)
      uni.navigateTo({ url: '/pages/checkin/submit' })
    },
    async loadData() {
      this.tasks = await getTodayTasks()
    }
  }
}
</script>
