<template>
  <view style="padding:24rpx">
    <view class="card" v-if="profile">
      <view style="font-size:32rpx;font-weight:700">{{ profile.name }}</view>
      <view style="margin-top:8rpx">编号：{{ profile.patientNo }}</view>
      <view style="margin-top:8rpx">手机号：{{ profile.phone || '-' }}</view>
      <view style="margin-top:8rpx">当前阶段：{{ profile.currentStage || '-' }}</view>
      <view style="margin-top:8rpx">主责治疗师：{{ profile.therapistName || '-' }}</view>
    </view>
    <view class="card">
      <button class="btn-plain" @click="goNotification">通知中心</button>
      <button class="btn-plain" style="margin-top:10rpx" @click="logout">退出登录</button>
    </view>
  </view>
</template>

<script>
import { getProfile } from '../../api/profile'
import { clearAuth } from '../../utils/auth'

export default {
  data() {
    return {
      profile: null
    }
  },
  onShow() {
    this.loadData()
  },
  methods: {
    goNotification() {
      uni.navigateTo({ url: '/pages/notification/index' })
    },
    logout() {
      clearAuth()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    async loadData() {
      this.profile = await getProfile()
    }
  }
}
</script>
