<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="900px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="formData.name" placeholder="请输入姓名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="formData.phone" placeholder="请输入手机号" maxlength="20" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="性别" prop="gender">
            <el-select v-model="formData.gender" placeholder="请选择性别" class="!w-full">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.SYSTEM_USER_SEX)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="年龄" prop="age">
            <el-input-number v-model="formData.age" :min="0" :max="120" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="当前阶段" prop="currentStage">
            <el-select v-model="formData.currentStage" placeholder="请选择阶段" class="!w-full" clearable>
              <el-option v-for="stage in stageOptions" :key="stage" :label="stage" :value="stage" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源渠道" prop="sourceChannel">
            <el-select v-model="formData.sourceChannel" placeholder="请选择来源" class="!w-full" clearable>
              <el-option label="门诊" value="门诊" />
              <el-option label="转介绍" value="转介绍" />
              <el-option label="线上" value="线上" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="24">
          <el-form-item label="主诉" prop="chiefComplaint">
            <el-input v-model="formData.chiefComplaint" type="textarea" :rows="2" placeholder="请输入主诉" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="疼痛部位" prop="painArea">
            <el-input v-model="formData.painArea" placeholder="请输入疼痛部位" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="疼痛评分" prop="painScore">
            <el-input-number v-model="formData.painScore" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="formType === 'create'" :gutter="12">
        <el-col :span="12">
          <el-form-item label="初始化 Episode">
            <el-switch v-model="formData.initEpisode" />
          </el-form-item>
        </el-col>
        <el-col :span="12" v-if="formData.initEpisode">
          <el-form-item label="Episode 类型">
            <el-select v-model="formData.episodeType" class="!w-full">
              <el-option label="initial" value="initial" />
              <el-option label="followup" value="followup" />
              <el-option label="maintenance" value="maintenance" />
              <el-option label="return_to_sport" value="return_to_sport" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="formType === 'create' && formData.initEpisode" :gutter="12">
        <el-col :span="24">
          <el-form-item label="Episode 目标">
            <el-input v-model="formData.episodePrimaryGoal" placeholder="请输入本阶段主要目标" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="请输入备注" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import {
  createRehabPatient,
  getRehabPatient,
  RehabPatientCreateReqVO,
  updateRehabPatient
} from '@/api/rehab/patient'
import { FormRules } from 'element-plus'

defineOptions({ name: 'RehabPatientForm' })

const message = useMessage()
const { t } = useI18n()

const stageOptions = [
  '初诊建档',
  '待评估',
  '评估中',
  '执行中',
  '复评中',
  '已结案',
  '已暂停',
  '已转诊'
]

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formType = ref<'create' | 'update'>('create')
const formLoading = ref(false)
const formRef = ref()
const formData = ref<RehabPatientCreateReqVO & { id?: number }>({
  name: '',
  phone: '',
  gender: undefined,
  age: undefined,
  chiefComplaint: '',
  painArea: '',
  painScore: undefined,
  sourceChannel: '',
  currentStage: '初诊建档',
  initEpisode: true,
  episodeType: 'initial',
  episodePrimaryGoal: '',
  remark: ''
})

const formRules = reactive<FormRules>({
  name: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: 'blur'
    }
  ]
})

const resetForm = () => {
  formData.value = {
    name: '',
    phone: '',
    gender: undefined,
    age: undefined,
    chiefComplaint: '',
    painArea: '',
    painScore: undefined,
    sourceChannel: '',
    currentStage: '初诊建档',
    initEpisode: true,
    episodeType: 'initial',
    episodePrimaryGoal: '',
    remark: ''
  }
  formRef.value?.resetFields()
}

const open = async (type: 'create' | 'update', id?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建患者' : '编辑患者'
  resetForm()
  if (type === 'update' && id) {
    formLoading.value = true
    try {
      const data = await getRehabPatient(id)
      formData.value = {
        ...formData.value,
        ...(data.patient || {}),
        id
      }
      formData.value.initEpisode = false
    } finally {
      formLoading.value = false
    }
  }
}

defineExpose({ open })

const emit = defineEmits(['success'])

const submitForm = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return

  formLoading.value = true
  try {
    if (formType.value === 'create') {
      const result = await createRehabPatient(formData.value)
      message.success(t('common.createSuccess'))
      if (result?.suspectedDuplicate) {
        message.warning('检测到疑似重复建档，请在详情页复核')
      }
    } else {
      await updateRehabPatient(formData.value as any)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
