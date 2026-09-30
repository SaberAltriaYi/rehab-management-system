<template>
  <view style="padding:24rpx">
    <view class="card" v-if="summary.patient">
      <view style="font-size:32rpx;font-weight:700">{{ summary.patient.name }}（{{ summary.patient.patientNo }}）</view>
      <view style="margin-top:8rpx;color:#64748b">当前阶段：{{ summary.patient.currentStage }}</view>
      <view style="margin-top:8rpx;color:#64748b">手机号：{{ summary.patient.phone || '-' }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">评估与报告摘要</view>
      <view style="margin-top:10rpx">最近评估：{{ summary.latestAssessmentNo || '-' }}</view>
      <view style="color:#64748b">{{ summary.latestAssessmentSummary || '暂无' }}</view>
      <view style="margin-top:10rpx">最近报告：{{ summary.latestReportNo || '-' }}</view>
      <view style="color:#64748b">{{ summary.latestReportSummary || '暂无' }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">计划与进度</view>
      <view style="margin-top:10rpx">active plan：{{ summary.activePlanNo || '暂无' }}（{{ summary.activePlanStatus || '-' }}）</view>
      <view style="color:#64748b">最近进度：{{ summary.latestProgressSummary || '暂无' }}</view>
      <view style="color:#b91c1c;margin-top:8rpx">待处理提醒：{{ summary.pendingTriggerCount || 0 }}</view>
    </view>

    <view class="card">
      <view style="font-weight:600">AI 专业摘要</view>
      <view style="margin-top:10rpx;color:#64748b" v-if="!aiSummary">暂无 AI 摘要</view>
      <view style="margin-top:10rpx;white-space:pre-wrap;line-height:1.7" v-else>{{ aiSummary.renderedText }}</view>
      <view style="font-size:22rpx;color:#94a3b8;margin-top:8rpx" v-if="aiSummary">
        审核状态：{{ aiSummary.reviewStatus }} | 安全状态：{{ aiSummary.safetyStatus }}
      </view>
    </view>

    <view class="card">
      <view style="font-weight:600">AI 随访建议</view>
      <view style="margin-top:10rpx;color:#64748b" v-if="!aiFollowup">暂无 AI 随访建议</view>
      <view style="margin-top:10rpx;white-space:pre-wrap;line-height:1.7" v-else>{{ aiFollowup.renderedText }}</view>
      <view style="margin-top:10rpx">
        <button size="mini" class="btn-plain" @click="goAiFollowup">查看随访草案页</button>
      </view>
    </view>

    <view class="card">
      <view style="font-weight:600;margin-bottom:10rpx">最近随访备注</view>
      <view v-if="!summary.recentFollowupNotes || !summary.recentFollowupNotes.length" style="color:#94a3b8">暂无</view>
      <view
        v-for="note in summary.recentFollowupNotes"
        :key="note.id"
        style="border-top:1rpx solid #f1f5f9;padding:10rpx 0"
      >
        <view>{{ note.content }}</view>
        <view style="font-size:22rpx;color:#64748b">{{ note.createTime }}</view>
      </view>
    </view>

    <view style="display:flex;gap:10rpx;flex-wrap:wrap;padding-bottom:20rpx">
      <button size="mini" class="btn-primary" @click="goCheckins">打卡历史</button>
      <button size="mini" class="btn-plain" @click="goFollowup">随访备注</button>
    </view>
  </view>
</template>

<script>
import { getPatientSummary } from '../../api/patient'
import { getLatestAiFollowup, getLatestAiSummary } from '../../api/ai'

export default {
  data() {
    return {
      patientId: null,
      summary: {},
      aiSummary: null,
      aiFollowup: null
    }
  },
  onLoad(query) {
    this.patientId = Number(query.id)
    this.loadData()
  },
  methods: {
    goCheckins() {
      uni.navigateTo({ url: `/pages/checkin/history?patientId=${this.patientId}` })
    },
    goFollowup() {
      uni.navigateTo({ url: `/pages/followup/index?patientId=${this.patientId}` })
    },
    goAiFollowup() {
      uni.navigateTo({ url: `/pages/followup/ai-draft?patientId=${this.patientId}` })
    },
    async loadData() {
      this.summary = await getPatientSummary(this.patientId)
      this.aiSummary = await getLatestAiSummary(this.patientId)
      this.aiFollowup = await getLatestAiFollowup(this.patientId)
    }
  }
}
</script>
