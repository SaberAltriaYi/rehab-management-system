<template>
  <main class="owner-console">
    <header class="owner-header">
      <div>
        <div class="owner-brand">REHAB OWNER PORTAL</div>
        <h1>独立 Owner 控制面</h1>
        <p>旧版 Java SaaS 页面已停用；机构申请、授权和审计在独立 HTTPS 控制面完成。</p>
      </div>
      <div class="owner-actions">
        <el-button type="primary" @click="openOwnerControl">进入独立控制台</el-button>
        <el-button type="primary" plain @click="openMainSystem">打开主系统</el-button>
      </div>
    </header>
    <section class="handoff">
      <h2>入口已切换</h2>
      <p
        >此页面不会再调用已关闭的 <code>/system/saas/platform/*</code> 接口，因此不会出现 404
        业务面板。</p
      >
      <a :href="ownerControlUrl">{{ ownerControlUrl }}</a>
    </section>
  </main>
</template>

<script setup lang="ts">
const mainAppUrl = import.meta.env.VITE_MAIN_APP_URL || 'http://127.0.0.1:5173'
const ownerControlUrl =
  import.meta.env.VITE_OWNER_CONTROL_URL || 'https://127.0.0.1:9444/owner/console'

const openMainSystem = () => {
  window.location.assign(`${mainAppUrl}/index`)
}
const openOwnerControl = () => {
  window.location.assign(ownerControlUrl)
}
</script>

<style scoped>
.owner-console {
  min-height: 100vh;
  padding: 28px;
  background: #eef4f8;
  color: #183247;
}
.owner-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  margin: 0 auto 22px;
  max-width: 1480px;
}
.owner-brand {
  color: #276a9a;
  font-size: 12px;
  letter-spacing: 3px;
}
h1 {
  margin: 8px 0;
  font-size: 30px;
}
p {
  margin: 0;
  color: #617888;
}
.owner-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.handoff {
  max-width: 1480px;
  margin: 0 auto;
  padding: 28px;
  border: 1px solid #c8dbe8;
  border-radius: 16px;
  background: #fff;
}
.handoff h2 {
  margin-top: 0;
}
.handoff p {
  line-height: 1.8;
}
.handoff a {
  overflow-wrap: anywhere;
  color: #276a9a;
}
@media (max-width: 720px) {
  .owner-console {
    padding: 16px;
  }
  .owner-header {
    flex-direction: column;
  }
}
</style>
