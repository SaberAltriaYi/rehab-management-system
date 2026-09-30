<template>
  <view style="padding:40rpx">
    <view class="card">
      <view style="font-size:32rpx;font-weight:700;margin-bottom:10rpx">身份绑定</view>
      <view style="color:#64748b;margin-bottom:20rpx">首次登录前绑定患者身份</view>
      <input v-model="form.patientNo" placeholder="患者编号 patient_no" style="margin-bottom: 12rpx; border:1rpx solid #e2e8f0;border-radius:10rpx;padding:16rpx" />
      <input v-model="form.phone" placeholder="手机号" style="margin-bottom: 12rpx; border:1rpx solid #e2e8f0;border-radius:10rpx;padding:16rpx" />
      <input v-model="form.nickname" placeholder="昵称（可选）" style="margin-bottom: 16rpx; border:1rpx solid #e2e8f0;border-radius:10rpx;padding:16rpx" />
      <button class="btn-primary" @click="onBind" :loading="loading">提交绑定</button>
    </view>
  </view>
</template>

<script>
import { bind } from '../../api/auth'

export default {
  data() {
    return {
      loading: false,
      form: {
        patientNo: '',
        phone: '',
        nickname: '',
        bindType: 'self'
      }
    }
  },
  methods: {
    async onBind() {
      if (!this.form.patientNo || !this.form.phone) {
        uni.showToast({ title: '请填写编号和手机号', icon: 'none' })
        return
      }
      this.loading = true
      try {
        await bind(this.form)
        uni.showToast({ title: '绑定成功，请登录', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 500)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
