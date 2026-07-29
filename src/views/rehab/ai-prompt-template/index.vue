<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="模板编码">
        <el-input v-model="queryParams.templateCode" placeholder="assessment_interpretation" class="!w-220px" />
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="queryParams.moduleScope" clearable class="!w-180px">
          <el-option label="assessment" value="assessment" />
          <el-option label="report" value="report" />
          <el-option label="plan" value="plan" />
          <el-option label="followup" value="followup" />
          <el-option label="patient_summary" value="patient_summary" />
          <el-option label="risk" value="risk" />
          <el-option label="progress" value="progress" />
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="queryParams.roleScope" clearable class="!w-140px">
          <el-option label="therapist" value="therapist" />
          <el-option label="patient" value="patient" />
          <el-option label="admin" value="admin" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用">
        <el-select v-model="queryParams.enabled" clearable class="!w-120px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" v-hasPermi="['rehab:ai:prompt-template:create']" @click="openDialog('create')">
          新建模板
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="ID" prop="id" width="90" />
      <el-table-column label="模板编码" prop="templateCode" min-width="190" />
      <el-table-column label="名称" prop="templateName" min-width="180" />
      <el-table-column label="模块" prop="moduleScope" min-width="140" />
      <el-table-column label="角色" prop="roleScope" min-width="110" />
      <el-table-column label="语言" prop="language" min-width="90" />
      <el-table-column label="版本" prop="versionNo" min-width="80" />
      <el-table-column label="默认" min-width="70">
        <template #default="scope">{{ scope.row.isDefault ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="启用" min-width="70">
        <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="时间" prop="createTime" min-width="170" />
      <el-table-column label="操作" min-width="300" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openDetail(scope.row)">查看</el-button>
          <el-button type="primary" link v-hasPermi="['rehab:ai:prompt-template:update']" @click="openDialog('edit', scope.row)">
            编辑
          </el-button>
          <el-button
            type="warning"
            link
            v-hasPermi="['rehab:ai:prompt-template:enable']"
            @click="toggleEnable(scope.row)"
          >
            {{ scope.row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            type="success"
            link
            v-hasPermi="['rehab:ai:prompt-template:enable']"
            :disabled="scope.row.isDefault"
            @click="setDefault(scope.row)"
          >
            设默认
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="detailVisible" title="模板详情" width="70%">
    <el-descriptions :column="2" border v-if="current">
      <el-descriptions-item label="模板编码">{{ current.templateCode }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.templateName }}</el-descriptions-item>
      <el-descriptions-item label="模块">{{ current.moduleScope }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ current.roleScope }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ current.versionNo }}</el-descriptions-item>
      <el-descriptions-item label="默认">{{ current.isDefault ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="启用">{{ current.enabled ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="Schema">{{ current.outputSchemaName }}</el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <div class="detail-label">System Prompt</div>
    <pre class="prompt-pre">{{ current?.systemPrompt || '-' }}</pre>
    <div class="detail-label">User Prompt Template</div>
    <pre class="prompt-pre">{{ current?.userPromptTemplate || '-' }}</pre>
  </Dialog>

  <Dialog v-model="dialog.visible" :title="dialog.type === 'create' ? '新建模板' : '编辑模板'" width="780px">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="模板编码" prop="templateCode">
            <el-input v-model="formData.templateCode" :disabled="dialog.type === 'edit'" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="formData.templateName" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="模块" prop="moduleScope">
            <el-select v-model="formData.moduleScope" class="!w-full">
              <el-option label="assessment" value="assessment" />
              <el-option label="report" value="report" />
              <el-option label="plan" value="plan" />
              <el-option label="followup" value="followup" />
              <el-option label="patient_summary" value="patient_summary" />
              <el-option label="risk" value="risk" />
              <el-option label="progress" value="progress" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="角色" prop="roleScope">
            <el-select v-model="formData.roleScope" class="!w-full">
              <el-option label="therapist" value="therapist" />
              <el-option label="patient" value="patient" />
              <el-option label="admin" value="admin" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="版本" prop="versionNo">
            <el-input-number v-model="formData.versionNo" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="语言" prop="language">
            <el-input v-model="formData.language" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Schema" prop="outputSchemaName">
            <el-input v-model="formData.outputSchemaName" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="默认模板" prop="isDefault">
            <el-switch v-model="formData.isDefault" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="启用状态" prop="enabled">
        <el-switch v-model="formData.enabled" />
      </el-form-item>
      <el-form-item label="System Prompt" prop="systemPrompt">
        <el-input v-model="formData.systemPrompt" type="textarea" :rows="5" />
      </el-form-item>
      <el-form-item label="User Template" prop="userPromptTemplate">
        <el-input v-model="formData.userPromptTemplate" type="textarea" :rows="6" />
      </el-form-item>
      <el-form-item label="备注" prop="note">
        <el-input v-model="formData.note" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="dialog.loading" @click="submitForm">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import {
  createRehabAiPromptTemplate,
  enableRehabAiPromptTemplate,
  getRehabAiPromptTemplate,
  getRehabAiPromptTemplatePage,
  setDefaultRehabAiPromptTemplate,
  updateRehabAiPromptTemplate
} from '@/api/rehab/ai'

defineOptions({ name: 'RehabAiPromptTemplate' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const current = ref<any>()
const detailVisible = ref(false)
const formRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  templateCode: undefined as string | undefined,
  moduleScope: undefined as string | undefined,
  roleScope: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})

const dialog = reactive({
  visible: false,
  type: 'create' as 'create' | 'edit',
  loading: false
})

const formData = reactive<Record<string, any>>({
  id: undefined,
  templateCode: '',
  templateName: '',
  moduleScope: 'assessment',
  roleScope: 'therapist',
  language: 'zh-CN',
  versionNo: 1,
  outputSchemaName: 'TherapistSummarySchema',
  systemPrompt: '',
  userPromptTemplate: '',
  enabled: true,
  isDefault: false,
  note: ''
})

const rules = reactive({
  templateCode: [{ required: true, message: '模板编码不能为空', trigger: 'blur' }],
  templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }],
  moduleScope: [{ required: true, message: '模块不能为空', trigger: 'change' }],
  roleScope: [{ required: true, message: '角色不能为空', trigger: 'change' }],
  versionNo: [{ required: true, message: '版本不能为空', trigger: 'change' }],
  outputSchemaName: [{ required: true, message: 'Schema 不能为空', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: 'System Prompt 不能为空', trigger: 'blur' }],
  userPromptTemplate: [{ required: true, message: 'User Prompt Template 不能为空', trigger: 'blur' }]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabAiPromptTemplatePage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryParams.pageNo = 1
  queryParams.pageSize = 10
  queryParams.templateCode = undefined
  queryParams.moduleScope = undefined
  queryParams.roleScope = undefined
  queryParams.enabled = undefined
  getList()
}

const openDetail = async (row: any) => {
  current.value = await getRehabAiPromptTemplate(row.id)
  detailVisible.value = true
}

const resetForm = () => {
  Object.assign(formData, {
    id: undefined,
    templateCode: '',
    templateName: '',
    moduleScope: 'assessment',
    roleScope: 'therapist',
    language: 'zh-CN',
    versionNo: 1,
    outputSchemaName: 'TherapistSummarySchema',
    systemPrompt: '',
    userPromptTemplate: '',
    enabled: true,
    isDefault: false,
    note: ''
  })
}

const openDialog = async (type: 'create' | 'edit', row?: any) => {
  dialog.type = type
  dialog.visible = true
  if (type === 'create') {
    resetForm()
    return
  }
  const data = await getRehabAiPromptTemplate(row.id)
  Object.assign(formData, data)
}

const submitForm = async () => {
  await formRef.value.validate()
  dialog.loading = true
  try {
    if (dialog.type === 'create') {
      await createRehabAiPromptTemplate(formData)
    } else {
      await updateRehabAiPromptTemplate(formData)
    }
    message.success('保存成功')
    dialog.visible = false
    await getList()
  } finally {
    dialog.loading = false
  }
}

const toggleEnable = async (row: any) => {
  await enableRehabAiPromptTemplate({ id: row.id, enabled: !row.enabled })
  message.success(row.enabled ? '已停用' : '已启用')
  await getList()
}

const setDefault = async (row: any) => {
  await setDefaultRehabAiPromptTemplate({ id: row.id })
  message.success('已设置为默认模板')
  await getList()
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.prompt-pre {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #334155;
}
</style>
