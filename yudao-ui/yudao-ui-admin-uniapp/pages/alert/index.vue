<template>
  <view style="padding:24rpx">
    <view class="card">
      <view style="display:flex;gap:8rpx">
        <picker mode="selector" :range="severities" @change="onSeverityChange">
          <view class="btn-plain" style="padding:12rpx 18rpx;border-radius:10rpx">级别：{{ query.severity || '全部' }}</view>
        </picker>
        <picker mode="selector" :range="statuses" @change="onStatusChange">
          <view class="btn-plain" style="padding:12rpx 18rpx;border-radius:10rpx">状态：{{ query.status || '全部' }}</view>
        </picker>
        <picker mode="selector" :range="types" @change="onTypeChange">
          <view class="btn-plain" style="padding:12rpx 18rpx;border-radius:10rpx">类型：{{ query.alertType || '全部' }}</view>
        </picker>
        <button size="mini" class="btn-primary" @click="search">筛选</button>
      </view>
    </view>

    <view class="card" v-for="item in list" :key="item.id">
      <view style="font-weight:700">{{ item.alertType }}｜{{ item.severity }}</view>
      <view style="font-size:22rpx;color:#64748b;margin-top:6rpx">患者：{{ item.patientName || '-' }}</view>
      <view style="margin-top:8rpx">{{ item.triggerMessage }}</view>
      <view style="font-size:24rpx;color:#64748b;margin-top:8rpx">状态：{{ item.status }} ｜ 触发指标：{{ item.triggerMetric || '-' }}</view>
      <view style="margin-top:8rpx">
        <button size="mini" class="btn-plain" @click="goPatient(item.patientId)">查看患者</button>
        <button
          size="mini"
          class="btn-plain"
          v-if="item.status === 'active' || item.status === 'acknowledged'"
          @click="ack(item.id)"
        >
          标记处理
        </button>
      </view>
    </view>

    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { acknowledgeAlert, getAlertPage } from '../../api/alert'

export default {
  data() {
    return {
      severities: ['all', 'info', 'warning', 'high'],
      statuses: ['all', 'active', 'acknowledged', 'resolved', 'ignored'],
      types: ['all', 'reassessment_due', 'low_adherence', 'pain_upgrade', 'plan_due', 'report_ready', 'high_risk_unresolved'],
      query: { pageNo: 1, pageSize: 10, severity: '', status: 'active', alertType: '' },
      list: []
    }
  },
  onShow() {
    this.fetchPage(true)
  },
  methods: {
    onSeverityChange(e) {
      const value = this.severities[e.detail.value]
      this.query.severity = value === 'all' ? '' : value
    },
    onStatusChange(e) {
      const value = this.statuses[e.detail.value]
      this.query.status = value === 'all' ? '' : value
    },
    onTypeChange(e) {
      const value = this.types[e.detail.value]
      this.query.alertType = value === 'all' ? '' : value
    },
    search() {
      this.query.pageNo = 1
      this.fetchPage(true)
    },
    loadMore() {
      this.query.pageNo += 1
      this.fetchPage(false)
    },
    goPatient(patientId) {
      uni.navigateTo({ url: `/pages/patient/summary?id=${patientId}` })
    },
    async ack(id) {
      await acknowledgeAlert(id)
      this.query.pageNo = 1
      this.fetchPage(true)
    },
    async fetchPage(reset) {
      const page = await getAlertPage(this.query)
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
