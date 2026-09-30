<template>
  <view style="padding:24rpx">
    <view class="card" v-if="!plan">暂无 active plan，无法提交打卡。</view>

    <view v-for="item in form.taskExecutions" :key="item.taskId" class="card">
      <view style="font-size:30rpx;font-weight:700">{{ item.taskName }}</view>
      <picker mode="selector" :range="statusList" @change="(e) => onStatusChange(item, e)">
        <view style="margin-top:8rpx">完成状态：{{ item.completionStatus }}</view>
      </picker>
      <input type="number" v-model="item.completedSets" placeholder="完成组数" style="margin-top:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="item.completedReps" placeholder="完成次数" style="margin-top:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="item.painScore" placeholder="该任务疼痛评分(0-10)" style="margin-top:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="item.difficultyLevel" placeholder="难度评分(1-10)" style="margin-top:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input v-model="item.symptomNote" placeholder="不适说明（可选）" style="margin-top:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
    </view>

    <view class="card" v-if="plan">
      <input type="number" v-model="form.painScoreBefore" placeholder="训练前疼痛评分(0-10)" style="margin-bottom:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="form.painScoreAfter" placeholder="训练后疼痛评分(0-10)" style="margin-bottom:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="form.fatigueLevel" placeholder="疲劳程度(1-10)" style="margin-bottom:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <input type="number" v-model="form.confidenceLevel" placeholder="完成信心(1-10)" style="margin-bottom:8rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
      <textarea v-model="form.overallComment" placeholder="总体反馈" style="height:140rpx;border:1rpx solid #e2e8f0;border-radius:8rpx;padding:10rpx" />
    </view>

    <button class="btn-primary" @click="submit" :loading="submitting" v-if="plan">提交打卡</button>
  </view>
</template>

<script>
import { createCheckin } from '../../api/checkin'
import { getCurrentPlan, getTodayTasks } from '../../api/plan'

export default {
  data() {
    return {
      statusList: ['completed', 'partial', 'skipped', 'pain_stop'],
      plan: null,
      submitting: false,
      form: {
        planId: null,
        painScoreBefore: null,
        painScoreAfter: null,
        fatigueLevel: null,
        confidenceLevel: null,
        overallComment: '',
        taskExecutions: []
      }
    }
  },
  async onShow() {
    await this.initData()
  },
  methods: {
    onStatusChange(item, e) {
      item.completionStatus = this.statusList[e.detail.value]
      if (item.completionStatus === 'pain_stop') {
        item.symptomFlag = true
      }
    },
    async initData() {
      this.plan = await getCurrentPlan()
      if (!this.plan) {
        return
      }
      this.form.planId = this.plan.id
      let tasks = uni.getStorageSync('REHAB_PATIENT_TODAY_TASKS') || []
      if (!tasks.length) {
        tasks = await getTodayTasks()
      }
      this.form.taskExecutions = tasks.map((task) => ({
        taskId: task.id,
        taskName: task.taskName,
        completionStatus: 'completed',
        completedSets: task.sets || null,
        completedReps: task.repetitions || null,
        painScore: null,
        difficultyLevel: null,
        symptomFlag: false,
        symptomNote: '',
        taskComment: ''
      }))
    },
    async submit() {
      if (!this.form.planId || !this.form.taskExecutions.length) {
        uni.showToast({ title: '缺少任务信息', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        const payload = {
          planId: this.form.planId,
          painScoreBefore: this.form.painScoreBefore,
          painScoreAfter: this.form.painScoreAfter,
          fatigueLevel: this.form.fatigueLevel,
          confidenceLevel: this.form.confidenceLevel,
          overallComment: this.form.overallComment,
          taskExecutions: this.form.taskExecutions.map((item) => ({
            taskId: item.taskId,
            completionStatus: item.completionStatus,
            completedSets: item.completedSets,
            completedReps: item.completedReps,
            painScore: item.painScore,
            difficultyLevel: item.difficultyLevel,
            symptomFlag: item.symptomFlag || false,
            symptomNote: item.symptomNote,
            taskComment: item.taskComment
          }))
        }
        await createCheckin(payload)
        uni.removeStorageSync('REHAB_PATIENT_TODAY_TASKS')
        uni.showToast({ title: '打卡成功', icon: 'success' })
        setTimeout(() => uni.reLaunch({ url: '/pages/home/index' }), 500)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>
