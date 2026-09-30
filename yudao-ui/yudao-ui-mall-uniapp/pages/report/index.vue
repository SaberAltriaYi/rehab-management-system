<template>
  <view style="padding:24rpx">
    <view class="card" v-for="item in list" :key="item.id">
      <view style="font-size:30rpx;font-weight:700">{{ item.reportNo }}</view>
      <view style="margin-top:8rpx">类型：{{ item.reportType }} | 状态：{{ item.reportStatus }}</view>
      <view style="margin-top:8rpx;color:#64748b">主要问题：{{ item.issueSummary }}</view>
      <view style="margin-top:8rpx;color:#64748b">建议：{{ item.recommendationSummary }}</view>
      <view style="font-size:24rpx;color:#94a3b8;margin-top:8rpx">{{ item.createTime }}</view>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { getReports } from '../../api/report'

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
      const page = await getReports({ pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
