<template>
  <main class="owner-console">
    <header class="owner-header">
      <div>
        <div class="owner-brand">REHAB OWNER PORTAL</div>
        <h1>系统拥有者管理</h1>
        <p>集中审核机构申请、分配套餐、授权租户并查看平台运行状态。</p>
      </div>
      <div class="owner-actions">
        <el-button @click="router.push('/system/tenant')">打开租户总览</el-button>
        <el-button type="primary" plain @click="router.push('/index')">返回主系统</el-button>
      </div>
    </header>
    <Platform />
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import Platform from '@/views/saas/Platform.vue'

const router = useRouter()
</script>

<style scoped>
.owner-console { min-height: 100vh; padding: 28px; background: #eef4f8; color: #183247; }
.owner-header { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; margin: 0 auto 22px; max-width: 1480px; }
.owner-brand { color: #276a9a; font-size: 12px; letter-spacing: 3px; }
h1 { margin: 8px 0; font-size: 30px; }
p { margin: 0; color: #617888; }
.owner-actions { display: flex; gap: 10px; flex-wrap: wrap; }
@media (max-width: 720px) { .owner-console { padding: 16px; } .owner-header { flex-direction: column; } }
</style>
