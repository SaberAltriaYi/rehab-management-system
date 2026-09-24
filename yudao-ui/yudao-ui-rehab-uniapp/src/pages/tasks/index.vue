<template>
  <view class="page-shell">
    <view v-if="plan" class="plan-card card">
      <text class="plan-eyebrow">当前康复计划</text>
      <text class="plan-title">{{ plan.planName || plan.planNo || '训练计划' }}</text>
      <text class="plan-dates">{{ plan.startDate || '—' }} 至 {{ plan.endDate || '—' }}</text>
      <text v-if="plan.precautions" class="plan-note">注意事项：{{ plan.precautions }}</text>
    </view>
    <view v-else class="notice-box">当前没有可执行的康复计划，请联系治疗师确认。</view>
    <view v-if="errorMessage" class="notice-box error-text">{{ errorMessage }}</view>
    <view class="section-head"><text class="section-title">今日训练</text><text class="muted">{{ tasks.length }} 项</text></view>
    <view v-if="tasks.length === 0 && !busy" class="card empty-state">今天暂时没有训练任务。</view>
    <view v-for="task in tasks" :key="task.id" class="task-card card">
      <view class="task-head">
        <view class="task-title-wrap"><text class="task-title">{{ task.taskName || '训练任务' }}</text><text class="task-type">{{ task.moduleType || '康复训练' }}</text></view>
        <view :class="['status-toggle', { done: isDone(task.id) }]" @tap="toggleTask(task.id)"><text>{{ isDone(task.id) ? '已完成' : '未完成' }}</text></view>
      </view>
      <text class="dosage">{{ task.dosageText || `${task.sets || 0} 组 × ${task.repetitions || 0} 次` }}</text>
      <text v-if="task.instructionText" class="instruction">{{ task.instructionText }}</text>
      <text v-if="task.painLimitRule" class="pain-rule">训练中如达到：{{ task.painLimitRule }}</text>
    </view>
    <view v-if="tasks.length" class="card checkin-card">
      <text class="checkin-title">完成今日训练记录</text>
      <text class="checkin-copy">请按实际情况标记每项任务。未完成的任务会以“跳过”记录，不会自动标记为完成。</text>
      <button class="primary-button" :loading="submitting" :disabled="submitting || submitted || !plan" @tap="submitCheckin">{{ submitting ? '正在提交…' : (submitted ? '今日打卡已提交' : '提交今日打卡') }}</button>
    </view>
    <view class="notice-box safety-note">如果训练时出现明显疼痛或不适，请停止该动作并联系治疗师；本页面不提供诊断或治疗建议。</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getSession } from '@/lib/session'
import { patientApi, type PatientTask } from '@/lib/rehab-api'

const plan = ref<Record<string, any> | null>(null)
const tasks = ref<PatientTask[]>([])
const completed = ref<Record<string, boolean>>({})
const busy = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const errorMessage = ref('')

onShow(() => {
  if (getSession()?.mode !== 'patient') {
    uni.reLaunch({ url: getSession() ? '/pages/home/index' : '/pages/login/index' })
    return
  }
  void loadTasks()
})

async function loadTasks(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    const [currentPlan, todayTasks] = await Promise.all([patientApi.currentPlan(), patientApi.todayTasks()])
    plan.value = currentPlan
    tasks.value = todayTasks || []
    completed.value = {}
    submitted.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取训练计划失败。'
  } finally { busy.value = false }
}

function isDone(taskId: number): boolean { return Boolean(completed.value[String(taskId)]) }
function toggleTask(taskId: number): void {
  const key = String(taskId)
  completed.value = { ...completed.value, [key]: !completed.value[key] }
}
function localDate(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

async function submitCheckin(): Promise<void> {
  if (!plan.value?.id || tasks.value.length === 0 || submitted.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const taskExecutions = tasks.value.map((task) => {
      const done = isDone(task.id)
      return {
        taskId: task.id,
        completionStatus: done ? 'completed' : 'skipped',
        completedSets: done ? (task.sets || 0) : 0,
        completedReps: done ? (task.repetitions || 0) : 0,
        symptomFlag: false,
      }
    })
    await patientApi.createCheckin({ planId: plan.value.id, checkinDate: localDate(), taskExecutions })
    submitted.value = true
    uni.showToast({ title: '打卡已提交', icon: 'success' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '打卡提交失败。'
  } finally { submitting.value = false }
}
</script>

<style scoped>
.plan-card { padding: 28rpx; background: linear-gradient(135deg,#eef6ff,#fff); }
.plan-eyebrow, .plan-title, .plan-dates, .plan-note { display: block; }
.plan-eyebrow { color: #6483ac; font-size: 21rpx; }
.plan-title { margin-top: 10rpx; color: #1e3554; font-size: 32rpx; font-weight: 750; }
.plan-dates { margin-top: 9rpx; color: #7d8da2; font-size: 21rpx; }
.plan-note { margin-top: 18rpx; color: #785a20; font-size: 22rpx; line-height: 1.55; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin: 30rpx 4rpx 18rpx; }
.section-head .section-title { margin: 0; }
.empty-state { color: #7e8a9b; text-align: center; padding: 50rpx 24rpx; }
.task-card { padding: 25rpx 24rpx; }
.task-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.task-title-wrap { flex: 1; }
.task-title { display: block; color: #253249; font-size: 27rpx; font-weight: 700; line-height: 1.4; }
.task-type { display: block; margin-top: 6rpx; color: #8591a1; font-size: 20rpx; }
.status-toggle { min-width: 112rpx; padding: 11rpx 15rpx; border: 1rpx solid #dfe5ee; border-radius: 99rpx; color: #718096; font-size: 20rpx; text-align: center; }
.status-toggle.done { border-color: #b9e3d0; background: #eaf8f1; color: #267550; }
.dosage { display: block; margin-top: 18rpx; color: #2a5d9f; font-size: 24rpx; font-weight: 650; }
.instruction { display: block; margin-top: 12rpx; color: #5f6b7d; font-size: 22rpx; line-height: 1.6; }
.pain-rule { display: block; margin-top: 12rpx; color: #8a5e19; font-size: 21rpx; line-height: 1.5; }
.checkin-card { margin-top: 28rpx; }
.checkin-title { display: block; color: #243248; font-size: 27rpx; font-weight: 700; }
.checkin-copy { display: block; margin-top: 10rpx; color: #748197; font-size: 21rpx; line-height: 1.6; }
.safety-note { margin-top: 20rpx; font-size: 21rpx; }
</style>
