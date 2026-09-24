<template>
  <view class="page-shell">
    <view class="card intro-card"><text class="section-title">通知提醒</text><text class="muted">通知内容以服务端授权范围为准。</text></view>
    <view v-if="errorMessage" class="notice-box error-text">{{ errorMessage }}</view>
    <view v-if="items.length === 0 && !busy" class="card empty-state">暂无通知。</view>
    <view v-for="item in items" :key="item.id" :class="['card','notification-card',{unread: !isRead(item)}]" @tap="openNotification(item)">
      <view class="notification-top">
        <view class="title-row"><text v-if="!isRead(item)" class="unread-dot"></text><text class="notification-title">{{ item.title || '康复管理通知' }}</text></view>
        <text v-if="item.severity" class="severity">{{ item.severity }}</text>
      </view>
      <text class="notification-content">{{ item.content || '点击查看通知详情。' }}</text>
      <view class="notification-meta"><text v-if="item.patientName">{{ item.patientName }}</text><text>{{ formatDate(item.createTime) }}</text><text>{{ isRead(item) ? '已读' : '未读 · 点击查看' }}</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getSession } from '@/lib/session'
import { patientApi, staffApi, type NotificationItem } from '@/lib/rehab-api'

const items = ref<NotificationItem[]>([])
const busy = ref(false)
const errorMessage = ref('')
onShow(() => {
  if (!getSession()) { uni.reLaunch({ url: '/pages/login/index' }); return }
  void loadNotifications()
})
async function loadNotifications(): Promise<void> {
  const session = getSession()
  if (!session) return
  busy.value = true; errorMessage.value = ''
  try {
    const result = session.mode === 'staff' ? await staffApi.notifications() : await patientApi.notifications()
    items.value = result?.list || []
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '读取通知失败。' }
  finally { busy.value = false }
}
function isRead(item: NotificationItem): boolean {
  const status = String(item.readStatus || '').toLowerCase()
  return status === 'read' || status === '1' || status === '已读'
}
function formatDate(value?: string): string { return value ? value.replace('T', ' ').slice(0, 16) : '' }
async function openNotification(item: NotificationItem): Promise<void> {
  const session = getSession()
  if (!session) return
  try {
    if (!isRead(item)) {
      if (session.mode === 'staff') await staffApi.readNotification(item.id)
      else await patientApi.readNotification(item.id)
      item.readStatus = 'read'
    }
    uni.showModal({ title: item.title || '通知详情', content: item.content || '无更多内容。', showCancel: false, confirmText: '知道了' })
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '更新通知状态失败。' }
}
</script>

<style scoped>
.intro-card .section-title { margin-bottom: 10rpx; }
.empty-state { color: #7e8a9b; padding: 54rpx 22rpx; text-align: center; }
.notification-card { padding: 24rpx; }
.notification-card.unread { border-color: #d5e6ff; background: #fcfdff; }
.notification-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.title-row { display: flex; flex: 1; align-items: center; gap: 10rpx; }
.unread-dot { width: 13rpx; height: 13rpx; flex-shrink: 0; border-radius: 50%; background: #2f7be0; }
.notification-title { color: #26344a; font-size: 26rpx; font-weight: 700; line-height: 1.4; }
.severity { flex-shrink: 0; color: #a36218; font-size: 20rpx; }
.notification-content { display: block; margin-top: 13rpx; color: #626f81; font-size: 22rpx; line-height: 1.6; }
.notification-meta { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10rpx; margin-top: 17rpx; color: #929cab; font-size: 19rpx; }
</style>
