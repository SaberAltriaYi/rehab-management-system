<template>
  <view style="padding: 40rpx">
    <view class="card">
      <view style="font-size: 36rpx; font-weight: 700; margin-bottom: 12rpx">康复管理端</view>
      <view style="color: #64748b; margin-bottom: 24rpx">治疗师/文员移动工作台</view>
      <input v-model="form.username" placeholder="账号" style="margin-bottom: 16rpx; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 18rpx" />
      <input v-model="form.password" password placeholder="密码" style="margin-bottom: 24rpx; border: 1rpx solid #e2e8f0; border-radius: 12rpx; padding: 18rpx" />
      <button class="btn-primary" @click="onLogin" :loading="loading">登录</button>
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
        username: '',
        password: ''
      }
    }
  },
  methods: {
    async onLogin() {
      if (!this.form.username || !this.form.password) {
        uni.showToast({ title: '请输入账号和密码', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await login(this.form)
        setToken(res.accessToken)
        setLoginUser({ userId: res.userId, expiresTime: res.expiresTime })
        uni.reLaunch({ url: '/pages/index/index' })
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
