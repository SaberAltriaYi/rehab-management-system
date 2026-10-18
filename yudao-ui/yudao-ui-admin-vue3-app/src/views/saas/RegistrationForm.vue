<template>
  <section class="registration-form">
    <div class="eyebrow">REHAB · 机构入驻</div>
    <h2>申请机构账号</h2>
    <p class="intro">提交机构信息，平台审核通过后为您开通机构账号。</p>
    <el-alert v-if="receipt" title="申请已提交，等待平台审核" type="success" :closable="false">
      申请编号：{{ receipt }}。请保存此编号，重复提交不会加快审核。
    </el-alert>
    <el-form v-else label-position="top" @submit.prevent="submit">
      <el-form-item label="机构名称">
        <el-input v-model="form.organization" maxlength="80" placeholder="请输入机构全称" />
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="form.contact" maxlength="50" placeholder="请输入联系人姓名" />
      </el-form-item>
      <el-form-item label="联系邮箱">
        <el-input v-model="form.email" type="email" maxlength="120" placeholder="用于接收审核结果" />
      </el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-button type="primary" native-type="submit" :loading="busy" class="submit-button">提交入驻申请</el-button>
    </el-form>
    <div class="form-actions"><el-button link type="primary" @click="emit('back-login')">返回登录</el-button></div>
    <small>请勿在此表单填写患者资料。审核不会自动产生费用。</small>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { registerOrganization } from '@/api/system/saas'

const emit = defineEmits<{ (event: 'back-login'): void }>()
const form = reactive({ organization: '', contact: '', email: '' })
const busy = ref(false)
const receipt = ref('')
const error = ref('')

async function submit() {
  if (busy.value) return
  if (!form.organization.trim() || !form.contact.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    error.value = '请完整填写机构名称、联系人与有效邮箱'
    return
  }
  busy.value = true
  error.value = ''
  try {
    receipt.value = await registerOrganization({ organization: form.organization.trim(), contact: form.contact.trim(), email: form.email.trim() })
  } catch {
    error.value = '提交未成功，请检查连接或稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.registration-form { width: 100%; }
.eyebrow { color: #397b70; font-size: 12px; letter-spacing: 3px; }
h2 { margin: 12px 0 8px; font-size: 28px; color: #234743; }
.intro { margin: 0 0 22px; color: #667b77; line-height: 1.7; }
.el-form { margin-top: 22px; }
.submit-button { width: 100%; margin-top: 10px; }
.form-actions { margin-top: 14px; text-align: center; }
small { display: block; margin-top: 20px; color: #7f918b; line-height: 1.6; }
</style>
