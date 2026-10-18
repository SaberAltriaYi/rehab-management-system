<template>
  <main class="owner-login">
    <section class="owner-card">
      <div class="owner-brand">REHAB OWNER PORTAL</div>
      <h1>系统拥有者控制台</h1>
      <p>仅用于平台租户审核、授权、套餐和机构生命周期管理。</p>
      <el-alert
        title="拥有者授权管理已迁移到独立 HTTPS 控制面，此页面不再加载旧版 SaaS 接口。"
        type="warning"
        :closable="false"
      />
      <a class="console-link" :href="ownerControlUrl">进入独立 Owner 控制台</a>
      <p class="console-hint"
        >本地地址：{{ ownerControlUrl }}。独立控制面使用自己的 Owner
        MFA，不复用主系统租户登录状态。</p
      >
      <a class="back-link" :href="mainAppLoginUrl">返回机构员工登录</a>
    </section>
  </main>
</template>

<script setup lang="ts">
const mainAppLoginUrl = `${import.meta.env.VITE_MAIN_APP_URL || 'http://127.0.0.1:5173'}/login`
const ownerControlUrl =
  import.meta.env.VITE_OWNER_CONTROL_URL || 'https://127.0.0.1:9444/owner/console'
</script>

<style scoped>
.owner-login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 10%, #193b55, #07131f 68%);
  color: #f3f8ff;
}
.owner-card {
  width: min(520px, 100%);
  padding: 40px;
  border: 1px solid rgb(160 208 255 / 25%);
  border-radius: 24px;
  background: rgb(8 24 38 / 94%);
  box-shadow: 0 24px 80px rgb(0 0 0 / 35%);
}
.owner-brand {
  color: #7dc7ff;
  font-size: 12px;
  letter-spacing: 3px;
}
h1 {
  margin: 12px 0 8px;
  font-size: 30px;
}
p {
  margin: 0 0 20px;
  color: #b6c8d8;
  line-height: 1.7;
}
.console-link {
  display: block;
  margin-top: 24px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #7dc7ff;
  color: #07131f;
  font-weight: 700;
  text-align: center;
  text-decoration: none;
}
.console-hint {
  margin-top: 14px;
  font-size: 13px;
  color: #9fb5c6;
  overflow-wrap: anywhere;
}
.back-link {
  display: block;
  margin-top: 18px;
  color: #9bd3ff;
  text-align: center;
}
</style>
