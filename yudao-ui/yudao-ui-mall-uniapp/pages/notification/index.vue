<template>
  <view style="padding:24rpx">
    <view class="card" v-for="item in list" :key="item.id">
      <view style="display:flex;justify-content:space-between;align-items:center">
        <view style="font-weight:700">{{ item.title }}</view>
        <view style="font-size:22rpx;color:#64748b">{{ item.readStatus }}</view>
      </view>
      <view style="margin-top:8rpx">{{ item.content }}</view>
      <view style="font-size:22rpx;color:#94a3b8;margin-top:8rpx">{{ item.createTime }}</view>
      <button size="mini" class="btn-plain" style="margin-top:8rpx" @click="markRead(item.id)">标记已读</button>
    </view>
    <button class="btn-plain" @click="loadMore">加载更多</button>
  </view>
</template>

<script>
import { getNotifications, readNotification } from '../../api/notification'

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
    loadMore() {
      this.pageNo += 1
      this.fetchPage(false)
    },
    async fetchPage(reset) {
      const page = await getNotifications({ pageNo: this.pageNo, pageSize: this.pageSize })
      const rows = page.list || []
      this.list = reset ? rows : this.list.concat(rows)
    }
  }
}
</script>
