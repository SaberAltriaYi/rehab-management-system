<template>
  <view style="padding:24rpx">
    <view class="card">
      <input v-model="query.keyword" placeholder="搜索姓名/编号/手机号" style="border:1rpx solid #e2e8f0;border-radius:12rpx;padding:16rpx" />
      <view style="margin-top:12rpx;display:flex;gap:10rpx">
        <button size="mini" class="btn-primary" @click="search">查询</button>
        <button size="mini" class="btn-plain" @click="reset">重置</button>
      </view>
    </view>

    <view class="card" v-for="item in list" :key="item.id">
      <view style="font-weight:700">{{ item.name }}（{{ item.patientNo }}）</view>
      <view style="font-size:24rpx;color:#64748b;margin-top:6rpx">阶段：{{ item.currentStage }} ｜ active plan：{{ item.activePlanStatus || '无' }}</view>
      <view style="font-size:24rpx;color:#64748b;margin-top:6rpx">最近打卡：{{ item.latestCheckinDate || '-' }} ｜ 高风险：{{ item.hasHighRiskTrigger ? '是' : '否' }}</view>
      <view style="margin-top:10rpx">
        <button size="mini" class="btn-plain" @click="goSummary(item.id)">患者摘要</button>
      </view>
    </view>

    <view style="padding: 12rpx 0">
      <button class="btn-plain" @click="loadMore">加载更多</button>
    </view>
  </view>
</template>

<script>
import { getMyPatients } from '../../api/patient'

export default {
  data() {
    return {
      list: [],
      query: { keyword: '', pageNo: 1, pageSize: 10 }
    }
  },
  onShow() {
    this.fetchPage(true)
  },
  methods: {
    goSummary(id) {
      uni.navigateTo({ url: `/pages/patient/summary?id=${id}` })
    },
    search() {
      this.query.pageNo = 1
      this.fetchPage(true)
    },
    reset() {
      this.query.keyword = ''
      this.query.pageNo = 1
      this.fetchPage(true)
    },
    loadMore() {
      this.query.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getMyPatients(this.query)
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
