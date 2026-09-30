<template>
  <view style="padding:24rpx">
    <view class="card" v-for="item in list" :key="item.id">
      <view style="font-size:30rpx;font-weight:700">{{ item.checkinDate }}</view>
      <view style="margin-top:8rpx">完成率：{{ item.overallCompletionRate || '-' }}%</view>
      <view style="margin-top:8rpx">疼痛：{{ item.painScoreBefore || '-' }} -> {{ item.painScoreAfter || '-' }}</view>
      <view style="margin-top:8rpx;color:#64748b">反馈：{{ item.overallComment || '-' }}</view>
      <view style="margin-top:8rpx;color:#475569;font-size:24rpx" v-if="item.taskExecutionSummary && item.taskExecutionSummary.length">
        <view v-for="(line, idx) in item.taskExecutionSummary" :key="idx">{{ line }}</view>
      </view>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { getCheckinHistory } from '../../api/checkin'

export default {
  data() {
    return {
      pageNo: 1,
      pageSize: 10,
      list: []
    }
  },
  onShow() {
    this.pageNo = 1
    this.fetchPage(true)
  },
  methods: {
    loadMore() {
      this.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getCheckinHistory({ pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
