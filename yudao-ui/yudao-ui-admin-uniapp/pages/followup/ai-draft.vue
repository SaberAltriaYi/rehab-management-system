<template>
  <view style="padding:24rpx">
    <view class="card">
      <view style="font-size:30rpx;font-weight:700">AI 随访草案</view>
      <view style="font-size:22rpx;color:#64748b;margin-top:8rpx">patientId: {{ patientId }}</view>
      <view v-if="!followup" style="color:#94a3b8;margin-top:16rpx">暂无草案</view>
      <view v-else style="margin-top:12rpx;white-space:pre-wrap;line-height:1.75">{{ followup.renderedText }}</view>
      <view style="font-size:22rpx;color:#64748b;margin-top:12rpx" v-if="followup">
        审核状态：{{ followup.reviewStatus }} | 安全状态：{{ followup.safetyStatus }}
      </view>
      <view style="font-size:22rpx;color:#94a3b8;margin-top:8rpx" v-if="followup">{{ followup.createTime }}</view>
    </view>
  </view>
</template>

<script>
import { getLatestAiFollowup } from '../../api/ai'

export default {
  data() {
    return {
      patientId: null,
      followup: null
    }
  },
  onLoad(query) {
    this.patientId = Number(query.patientId)
    this.loadData()
  },
  methods: {
    async loadData() {
      this.followup = await getLatestAiFollowup(this.patientId)
    }
  }
}
</script>
