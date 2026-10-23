<template>
  <section class="registration-form">
    <div class="eyebrow">REHAB · 机构入驻</div>
    <h2>申请机构账号</h2>
    <p class="intro">机构申请由独立 Owner HTTPS 服务接收，提交后取得申请编号并等待人工审核。申请本身不是登录账号。</p>
    <a class="submit-button" :href="ownerRegistrationUrl">前往安全机构申请表（机构名称、联系人、邮箱）</a>
    <div class="form-actions"><el-button link type="primary" @click="emit('back-login')">返回登录</el-button></div>
    <small>请勿在此表单填写患者资料。审核不会自动产生费用。</small>
  </section>
</template>

<script setup lang="ts">
const emit = defineEmits<{ (event: 'back-login'): void }>()
const ownerRegistrationUrl = new URL(
  '/owner/register',
  import.meta.env.VITE_OWNER_CONTROL_URL || 'https://127.0.0.1:9444/owner/console'
).toString()
</script>

<style scoped>
.registration-form { width: 100%; }
.eyebrow { color: #397b70; font-size: 12px; letter-spacing: 3px; }
h2 { margin: 12px 0 8px; font-size: 28px; color: #234743; }
.intro { margin: 0 0 22px; color: #667b77; line-height: 1.7; }
.submit-button { display: block; padding: 14px; border-radius: 8px; background: var(--el-color-primary); color: #fff; text-align: center; text-decoration: none; }
.submit-button:hover { background: var(--el-color-primary-light-3); }
.form-actions { margin-top: 14px; text-align: center; }
small { display: block; margin-top: 20px; color: #7f918b; line-height: 1.6; }
</style>
