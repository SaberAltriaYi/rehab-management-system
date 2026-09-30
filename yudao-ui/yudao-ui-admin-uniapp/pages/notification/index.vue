<template>
  <view style="padding:24rpx">
    <view class="card" v-for="item in list" :key="item.id">
      <view style="display:flex;justify-content:space-between;align-items:center">
        <view style="font-weight:700">{{ item.title }}</view>
        <view style="font-size:22rpx;color:#64748b">{{ item.readStatus }}</view>
      </view>
      <view style="font-size:22rpx;color:#64748b;margin-top:6rpx">
        {{ item.patientName ? `${item.patientName}（${item.patientNo || '-'}）` : '系统通知' }}
      </view>
      <view style="margin-top:8rpx">{{ item.content }}</view>
      <view style="font-size:22rpx;color:#94a3b8;margin-top:8rpx">{{ item.createTime }}</view>
      <view style="margin-top:10rpx;display:flex;gap:10rpx">
        <button
          size="mini"
          class="btn-plain"
          v-if="item.readStatus !== 'read'"
          @click="markRead(item.id)"
        >
          标记已读
        </button>
        <button
          size="mini"
          class="btn-plain"
          v-if="item.actionUrl"
          @click="jump(item)"
        >
          {{ item.actionText || '查看' }}
        </button>
      </view>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { getNotificationPage, readNotification } from '../../api/notification'

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
    async markRead(id) {
      await readNotification(id)
      this.pageNo = 1
      this.fetchPage(true)
    },
    jump(item) {
      if (item.patientId) {
        uni.navigateTo({ url: `/pages/patient/summary?id=${item.patientId}` })
        return
      }
      uni.showToast({ title: '当前通知无可跳转对象', icon: 'none' })
    },
    loadMore() {
      this.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getNotificationPage({ pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>

