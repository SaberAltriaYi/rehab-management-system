<template>
  <main class="owner-login">
    <section class="owner-card">
      <div class="owner-brand">REHAB OWNER PORTAL</div>
      <h1>系统拥有者控制台</h1>
      <p>仅用于平台租户审核、授权、套餐和机构生命周期管理。</p>
      <el-alert title="此入口不授予机构管理员平台权限" type="warning" :closable="false" />
      <LoginForm class="owner-form" default-redirect="/owner" />
      <a class="back-link" :href="mainAppLoginUrl">返回机构员工登录</a>
    </section>
  </main>
</template>

<script setup lang="ts">
import LoginForm from '@/views/Login/components/LoginForm.vue'

const mainAppLoginUrl = `${import.meta.env.VITE_MAIN_APP_URL || 'http://127.0.0.1:5173'}/login`
</script>

<style scoped>
.owner-login { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at 20% 10%, #193b55, #07131f 68%); color: #f3f8ff; }
.owner-card { width: min(520px, 100%); padding: 40px; border: 1px solid rgb(160 208 255 / 25%); border-radius: 24px; background: rgb(8 24 38 / 94%); box-shadow: 0 24px 80px rgb(0 0 0 / 35%); }
.owner-brand { color: #7dc7ff; font-size: 12px; letter-spacing: 3px; }
h1 { margin: 12px 0 8px; font-size: 30px; }
p { margin: 0 0 20px; color: #b6c8d8; line-height: 1.7; }
.owner-form { margin-top: 24px; }
.back-link { display: block; margin-top: 18px; color: #9bd3ff; text-align: center; }
</style>
