<template>
  <ContentWrap>
    <div class="mb-12px text-16px font-bold">AI 配置中心</div>
    <el-form v-loading="loading" :model="formData" label-width="240px">
      <el-form-item label="AI 总开关">
        <el-switch v-model="formData.aiEnabled" />
      </el-form-item>
      <el-form-item label="启用评估解读">
        <el-switch v-model="formData.enableAssessmentInterpretation" />
      </el-form-item>
      <el-form-item label="启用报告摘要">
        <el-switch v-model="formData.enableReportSummary" />
      </el-form-item>
      <el-form-item label="启用患者摘要">
        <el-switch v-model="formData.enablePatientSummary" />
      </el-form-item>
      <el-form-item label="启用计划草案">
        <el-switch v-model="formData.enablePlanDraft" />
      </el-form-item>
      <el-form-item label="启用随访文案">
        <el-switch v-model="formData.enableFollowupWriter" />
      </el-form-item>
      <el-form-item label="人工审核后可见">
        <el-switch v-model="formData.requireHumanReviewBeforeVisible" />
      </el-form-item>
      <el-form-item label="患者端仅审核后可见">
        <el-switch v-model="formData.visibleToPatientAfterReviewOnly" />
      </el-form-item>
      <el-form-item label="首选模型">
        <el-input v-model="formData.preferredModelName" placeholder="例如 gpt-4.1" class="!w-360px" />
      </el-form-item>
      <el-form-item label="输出风格">
        <el-radio-group v-model="formData.promptStyle">
          <el-radio label="concise">concise</el-radio>
          <el-radio label="standard">standard</el-radio>
          <el-radio label="detailed">detailed</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="安全模式">
        <el-radio-group v-model="formData.safetyMode">
          <el-radio label="strict">strict</el-radio>
          <el-radio label="standard">standard</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="formData.note" type="textarea" :rows="3" class="!w-500px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitLoading" v-hasPermi="['rehab:ai:config:update']" @click="submitForm">
          保存配置
        </el-button>
        <el-button @click="loadData">刷新</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { getRehabAiConfig, updateRehabAiConfig } from '@/api/rehab/ai'

defineOptions({ name: 'RehabAiConfig' })

const message = useMessage()
const loading = ref(false)
const submitLoading = ref(false)

const formData = reactive<Record<string, any>>({
  aiEnabled: true,
  enableAssessmentInterpretation: true,
  enableReportSummary: true,
  enablePatientSummary: true,
  enablePlanDraft: true,
  enableFollowupWriter: true,
  requireHumanReviewBeforeVisible: true,
  visibleToPatientAfterReviewOnly: true,
  preferredModelName: 'gpt-4.1-mini',
  promptStyle: 'standard',
  safetyMode: 'strict',
  note: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const data = await getRehabAiConfig()
    Object.assign(formData, data || {})
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
  submitLoading.value = true
  try {
    await updateRehabAiConfig(formData)
    message.success('AI 配置已更新')
    await loadData()
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
