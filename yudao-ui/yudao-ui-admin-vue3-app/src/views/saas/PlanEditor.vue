<template>
  <el-button type="primary" @click="open = true">创建订阅套餐</el-button>
  <el-dialog v-model="open" title="创建订阅套餐" width="min(520px, 95vw)">
    <el-alert title="阶段一仅管理订阅状态，不收款。配额业务接入尚未全部验收。" type="info" :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="套餐名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
      <el-form-item label="账号上限"><el-input-number v-model="form.maxUsers" :min="1" :max="1000000" :precision="0" /></el-form-item>
      <el-form-item label="患者上限"><el-input-number v-model="form.maxPatients" :min="0" :max="10000000" :precision="0" /></el-form-item>
      <el-form-item label="评估次数上限"><el-input-number v-model="form.motionQuota" :min="0" :max="1000000000" :precision="0" /></el-form-item>
      <el-form-item label="存储字节上限"><el-input-number v-model="form.storageQuota" :min="0" :max="Number.MAX_SAFE_INTEGER" :precision="0" /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy">保存套餐</el-button>
    </el-form>
  </el-dialog>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPlan } from '@/api/system/saas'
const emit = defineEmits(['changed'])
const open = ref(false), busy = ref(false)
const form = reactive({ name: '', maxUsers: 5, maxPatients: 100, motionQuota: 1000, storageQuota: 1073741824 })
async function submit() {
  if (!form.name.trim()) { ElMessage.warning('请填写套餐名称'); return }
  busy.value = true
  try { await createPlan({ ...form }); open.value = false; emit('changed'); ElMessage.success('套餐已创建') }
  catch { ElMessage.error('套餐创建失败，请刷新后确认是否已保存，避免重复创建') }
  finally { busy.value = false }
}
</script>
