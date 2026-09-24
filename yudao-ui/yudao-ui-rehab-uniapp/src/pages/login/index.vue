<template>
  <view class="login-page">
    <view class="brand-block">
      <view class="brand-mark"><text>康</text></view>
      <text class="brand-title">康复管理</text>
      <text class="brand-caption">让训练计划与康复进度清晰可见</text>
    </view>
    <view class="role-switch">
      <view :class="['role-option', { active: role === 'staff' }]" @tap="role = 'staff'">治疗师 / 管理员</view>
      <view :class="['role-option', { active: role === 'patient' }]" @tap="role = 'patient'">患者</view>
    </view>
    <view class="login-card">
      <text class="card-heading">{{ role === 'staff' ? '团队账号登录' : '患者登录' }}</text>
      <text class="card-description">{{ role === 'staff' ? '使用系统中的治疗师或管理员账号。' : '使用已绑定档案的手机号与患者编号。' }}</text>
      <view class="field-group">
        <text class="field-label">工作室 / 租户</text>
        <input v-model="tenantName" class="field-input" placeholder="工作室内部" maxlength="80" />
      </view>
      <template v-if="role === 'staff'">
        <view class="field-group"><text class="field-label">用户名</text><input v-model="username" class="field-input" placeholder="请输入用户名" maxlength="80" /></view>
        <view class="field-group"><text class="field-label">密码</text><input v-model="password" class="field-input" password placeholder="请输入密码" maxlength="128" /></view>
      </template>
      <template v-else>
        <view class="field-group"><text class="field-label">手机号</text><input v-model="phone" class="field-input" type="text" placeholder="请输入档案手机号" maxlength="32" /></view>
        <view class="field-group"><text class="field-label">患者编号</text><input v-model="patientNo" class="field-input" placeholder="例如 PAT202603100001" maxlength="64" /></view>
        <view class="notice-box patient-warning">患者登录依赖治疗师预先完成绑定。当前后端仅以“手机号 + 患者编号”校验身份，安全审查完成前默认关闭，不可用于公开患者服务。</view>
      </template>
      <text v-if="errorMessage" class="form-error">{{ errorMessage }}</text>
      <button v-if="role === 'staff'" class="primary-button submit-button" :loading="busy" :disabled="busy" @tap="submitStaff">{{ busy ? '正在登录…' : '登录' }}</button>
      <button v-else class="primary-button submit-button" :loading="busy" :disabled="busy || !patientLoginEnabled" @tap="submitPatient">{{ patientLoginEnabled ? (busy ? '正在登录…' : '登录患者端') : '患者登录安全审查中' }}</button>
    </view>
    <text class="config-hint">App / 微信小程序需配置可访问的 HTTPS API 域名；H5 可使用同源代理。详见移动端 README。</text>
    <CaptchaSlider :visible="captchaVisible" @close="captchaVisible = false" @verified="onCaptchaVerified" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CaptchaSlider from '@/components/CaptchaSlider.vue'
import { loginPatient, loginStaff } from '@/lib/auth'
import { CAPTCHA_ENABLED, PATIENT_LOGIN_ENABLED } from '@/lib/config'
import { getSession } from '@/lib/session'

const role = ref<'staff' | 'patient'>('staff')
const tenantName = ref('工作室内部')
const username = ref('')
const password = ref('')
const phone = ref('')
const patientNo = ref('')
const busy = ref(false)
const captchaVisible = ref(false)
const errorMessage = ref('')
const patientLoginEnabled = PATIENT_LOGIN_ENABLED

onShow(() => { if (getSession()) uni.reLaunch({ url: '/pages/home/index' }) })

function submitStaff(): void {
  errorMessage.value = ''
  if (!tenantName.value.trim() || !username.value.trim() || !password.value) {
    errorMessage.value = '请填写租户、用户名和密码。'
    return
  }
  if (CAPTCHA_ENABLED) { captchaVisible.value = true; return }
  void finishStaffLogin('')
}

async function finishStaffLogin(captchaVerification: string): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    await loginStaff({ tenantName: tenantName.value, username: username.value.trim(), password: password.value, captchaVerification })
    uni.reLaunch({ url: '/pages/home/index' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试。'
  } finally { busy.value = false }
}

function onCaptchaVerified(captchaVerification: string): void {
  captchaVisible.value = false
  void finishStaffLogin(captchaVerification)
}

async function submitPatient(): Promise<void> {
  errorMessage.value = ''
  if (!patientLoginEnabled) { errorMessage.value = '当前患者认证方式安全性不足，已默认关闭。'; return }
  if (!tenantName.value.trim() || !phone.value.trim() || !patientNo.value.trim()) {
    errorMessage.value = '请填写租户、手机号和患者编号。'
    return
  }
  busy.value = true
  try {
    await loginPatient({ tenantName: tenantName.value, phone: phone.value.trim(), patientNo: patientNo.value.trim() })
    uni.reLaunch({ url: '/pages/home/index' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请联系治疗师。'
  } finally { busy.value = false }
}
</script>

<style scoped>
.login-page { min-height: 100vh; padding: 54rpx 34rpx 48rpx; background: radial-gradient(circle at 92% 0%, rgba(71,142,234,.12), transparent 34%), linear-gradient(180deg,#f7faff 0%,#f5f6fa 62%); }
.brand-block { display: flex; align-items: center; flex-direction: column; padding: 26rpx 0 34rpx; }
.brand-mark { display: flex; width: 98rpx; height: 98rpx; align-items: center; justify-content: center; border-radius: 30rpx; background: linear-gradient(145deg,#3b86ef,#1f5fbd); box-shadow: 0 18rpx 40rpx rgba(39,107,219,.25); color: #fff; font-size: 48rpx; font-weight: 800; }
.brand-title { margin-top: 20rpx; color: #172338; font-size: 40rpx; font-weight: 800; }
.brand-caption { margin-top: 10rpx; color: #768196; font-size: 24rpx; }
.role-switch { display: flex; padding: 8rpx; border-radius: 18rpx; background: #e9eef7; }
.role-option { flex: 1; padding: 19rpx 8rpx; border-radius: 14rpx; color: #69778d; font-size: 25rpx; text-align: center; }
.role-option.active { background: #fff; color: #1d5fb9; font-weight: 700; box-shadow: 0 5rpx 18rpx rgba(29,67,118,.09); }
.login-card { margin-top: 24rpx; padding: 34rpx 30rpx 30rpx; border: 1rpx solid #e8ecf3; border-radius: 26rpx; background: #fff; box-shadow: 0 16rpx 44rpx rgba(28,47,78,.06); }
.card-heading, .card-description { display: block; }
.card-heading { color: #182230; font-size: 33rpx; font-weight: 750; }
.card-description { margin-top: 10rpx; color: #768196; font-size: 23rpx; line-height: 1.5; }
.field-group { margin-top: 22rpx; }
.field-label { display: block; margin-bottom: 10rpx; color: #39465a; font-size: 24rpx; font-weight: 600; }
.field-input { width: 100%; height: 86rpx; padding: 0 22rpx; border: 1rpx solid #dfe5ee; border-radius: 14rpx; background: #fbfcfe; color: #182230; font-size: 26rpx; }
.patient-warning { margin-top: 22rpx; font-size: 23rpx; }
.form-error { display: block; margin-top: 18rpx; color: #b42318; font-size: 23rpx; line-height: 1.5; }
.submit-button { margin-top: 24rpx; }
.submit-button[disabled] { background: #b8c7de; color: #fff; }
.config-hint { display: block; margin: 24rpx 10rpx 0; color: #8791a0; font-size: 21rpx; line-height: 1.6; text-align: center; }
</style>
