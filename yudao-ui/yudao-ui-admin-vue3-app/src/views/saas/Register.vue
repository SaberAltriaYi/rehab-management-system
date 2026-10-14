<template>
  <main class="registration">
    <section>
      <p class="eyebrow">REHAB · 机构入驻</p>
      <h1>让康复服务，连接每一个家庭</h1>
      <p>提交机构信息，平台审核通过后为您开通机构账号。无需授权文件或本地激活。</p>
      <el-alert v-if="receipt" title="申请已提交，等待平台审核" type="success" :closable="false">
        申请编号：{{ receipt }}。请保存此编号，重复提交不会加快审核。
      </el-alert>
      <el-form v-else label-position="top" @submit.prevent="submit">
        <el-form-item label="机构名称"><el-input v-model="form.organization" maxlength="80" required /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" maxlength="50" required /></el-form-item>
        <el-form-item label="联系邮箱"><el-input v-model="form.email" type="email" maxlength="120" required /></el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-button type="primary" native-type="submit" :loading="busy">提交入驻申请</el-button>
      </el-form>
      <router-link to="/login">已有机构账号？前往登录</router-link>
      <small>请勿在此表单填写患者资料。审核不会自动产生费用。</small>
    </section>
  </main>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { registerOrganization } from '@/api/system/saas'
const form = reactive({ organization: '', contact: '', email: '' })
const busy = ref(false), receipt = ref(''), error = ref('')
async function submit() {
  if (busy.value) return
  if (!form.organization.trim() || !form.contact.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { error.value = '请完整填写机构名称、联系人与有效邮箱'; return }
  busy.value = true; error.value = ''
  try { receipt.value = await registerOrganization({ organization: form.organization.trim(), contact: form.contact.trim(), email: form.email.trim() }) }
  catch { error.value = '提交未成功，请检查连接或稍后重试。' }
  finally { busy.value = false }
}
</script>
<style scoped>
.registration{min-height:100vh;background:#eef5f3;display:grid;place-items:center;padding:32px 18px;color:#234743}.registration section{max-width:540px;width:100%;background:white;padding:40px;border-radius:18px;box-shadow:0 16px 60px #244b4210}.eyebrow{letter-spacing:3px;font-size:12px;color:#397b70}h1{font-size:28px;line-height:1.5}p{line-height:1.8;color:#667b77}.el-form{margin:28px 0}.el-button{margin-top:18px;width:100%}small{display:block;margin-top:24px;color:#7f918b}
</style>
