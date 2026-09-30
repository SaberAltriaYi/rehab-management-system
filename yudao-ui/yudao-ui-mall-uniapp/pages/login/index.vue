<template>
  <view style="padding: 40rpx">
    <view class="card">
      <view style="font-size: 36rpx; font-weight: 700; margin-bottom: 8rpx">康复患者端</view>
      <view style="color:#64748b;margin-bottom:20rpx">查看计划、完成打卡、接收复评提醒</view>
      <input v-model="form.phone" placeholder="手机号" style="margin-bottom: 12rpx; border:1rpx solid #e2e8f0;border-radius:10rpx;padding:16rpx" />
      <input v-model="form.bindCode" placeholder="绑定码（patient_no）" style="margin-bottom: 16rpx; border:1rpx solid #e2e8f0;border-radius:10rpx;padding:16rpx" />
      <button class="btn-primary" @click="onLogin" :loading="loading">登录</button>
      <button class="btn-plain" style="margin-top:12rpx" @click="goBind">首次绑定</button>
    </view>
  </view>
</template>

<script>
import { login } from '../../api/auth'
import { setLoginUser, setToken } from '../../utils/auth'

export default {
  data() {
    return {
      loading: false,
      form: {
        phone: '',
        bindCode: ''
      }
    }
  },
  methods: {
    goBind() {
      uni.navigateTo({ url: '/pages/bind/index' })
    },
    async onLogin() {
      if (!this.form.phone || !this.form.bindCode) {
        uni.showToast({ title: '请输入手机号和绑定码', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await login(this.form)
        setToken(res.accessToken)
        setLoginUser({ userId: res.userId, expiresTime: res.expiresTime })
        uni.reLaunch({ url: '/pages/home/index' })
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
