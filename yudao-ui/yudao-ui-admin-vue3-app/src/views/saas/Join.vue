<template>
  <main class="join"><div class="card"><small>REHAB SAAS · 开发版</small><h1>加入康复团队</h1><p>请输入机构管理员通过可信渠道发送的邀请信息。</p>
    <el-alert v-if="done" title="账号创建成功，请使用机构名称与新账号登录。" type="success" :closable="false" />
    <el-form v-else label-position="top" @submit.prevent="submit">
      <el-form-item label="机构编号"><el-input-number v-model="tenant" :min="1" :precision="0" /></el-form-item>
      <el-form-item label="一次性邀请凭据"><el-input v-model="form.token" type="password" autocomplete="off" maxlength="43" show-password /></el-form-item>
      <el-form-item label="登录账号（4–30 位字母或数字）"><el-input v-model="form.username" autocomplete="username" maxlength="30" /></el-form-item>
      <el-form-item label="姓名"><el-input v-model="form.nickname" maxlength="30" /></el-form-item>
      <el-form-item label="密码（12–16 位）"><el-input v-model="form.password" type="password" autocomplete="new-password" maxlength="16" show-password /></el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button native-type="submit" type="primary" :loading="busy">接受邀请并创建账号</el-button>
    </el-form><router-link to="/login">前往登录</router-link>
  </div></main>
</template>
<script setup lang="ts">
import { reactive, ref, onUnmounted } from 'vue'
import { acceptStaffInvitation } from '@/api/system/saas'
const tenant = ref(1), busy = ref(false), done = ref(false), error = ref('')
const form = reactive({ token: '', username: '', nickname: '', password: '' })
async function submit() {
  error.value = ''
  if (!/^[A-Za-z0-9_-]{43}$/.test(form.token) || !/^[a-zA-Z0-9]{4,30}$/.test(form.username) || form.password.length < 12 || !form.nickname.trim()) { error.value = '请检查邀请凭据、账号、姓名和密码长度'; return }
  busy.value = true
  try { await acceptStaffInvitation(tenant.value, { ...form }); done.value = true; form.token = '' }
  catch { error.value = '无法接受邀请：请核实机构编号、凭据有效期、账号是否占用及机构配额；如结果不确定，请先尝试登录。' }
  finally { form.password = ''; busy.value = false }
}
onUnmounted(() => { form.token = ''; form.password = '' })
</script>
<style scoped>.join{min-height:100vh;background:#eef4f1;display:grid;place-items:center;padding:24px}.card{width:min(440px,100%);background:#fff;padding:32px;border-radius:18px}small{color:#408679}p{color:#72857e}.el-button,.el-alert{margin:14px 0}a{display:block;margin-top:20px;color:#287767}</style>
