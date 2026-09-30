<template>
  <view style="padding:24rpx">
    <view class="card" v-for="item in list" :key="item.id">
      <view style="font-weight:700">{{ item.checkinDate }}</view>
      <view style="margin-top:8rpx">完成率：{{ item.overallCompletionRate || '-' }}%</view>
      <view style="margin-top:4rpx">疼痛：{{ item.painScoreBefore || '-' }} -> {{ item.painScoreAfter || '-' }}</view>
      <view style="margin-top:8rpx;color:#64748b">{{ item.overallComment || '-' }}</view>
      <view v-if="item.taskExecutionSummary && item.taskExecutionSummary.length" style="margin-top:10rpx; font-size:24rpx; color:#475569">
        <view v-for="(row, idx) in item.taskExecutionSummary" :key="idx">{{ row }}</view>
      </view>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { getPatientCheckins } from '../../api/patient'

export default {
  data() {
    return {
      patientId: null,
      pageNo: 1,
      pageSize: 10,
      list: []
    }
  },
  onLoad(query) {
    this.patientId = Number(query.patientId)
    this.fetchPage(true)
  },
  methods: {
    loadMore() {
      this.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getPatientCheckins({ patientId: this.patientId, pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
