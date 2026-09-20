<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <div class="text-16px font-bold">{{ pageTitle }}</div>
      <el-button @click="goBack">返回</el-button>
    </div>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :disabled="submitLoading"
      label-width="130px"
      v-loading="loading || submitLoading"
    >
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="患者" prop="patientId">
            <el-select
              v-model="formData.patientId"
              class="!w-full"
              placeholder="请选择患者"
              filterable
              remote
              :remote-method="handlePatientSearch"
              :loading="patientLoading"
              :disabled="isEditMode"
              clearable
            >
              <el-option
                v-for="item in patientOptions"
                :key="item.id"
                :label="`${item.name}（${item.patientNo || '无编号'}）`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Episode" prop="episodeId">
            <el-select v-model="formData.episodeId" filterable clearable class="!w-full" placeholder="请选择 episode">
              <el-option
                v-for="ep in episodeOptions"
                :key="ep.id"
                :label="`${ep.episodeNo} (${ep.currentStage || ep.status || '-'})`"
                :value="ep.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="评估类型" prop="assessmentType">
            <el-select v-model="formData.assessmentType" class="!w-full" placeholder="请选择评估类型" @change="handleAssessmentTypeChange">
              <el-option
                v-for="item in enabledAssessmentTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="评估日期" prop="assessmentDate">
            <el-date-picker
              v-model="formData.assessmentDate"
              value-format="YYYY-MM-DD"
              type="date"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="本次重点" prop="chiefFocus">
            <el-input v-model="formData.chiefFocus" placeholder="例如：体态与下肢稳定" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="疼痛评分" prop="painScore">
            <el-input-number v-model="formData.painScore" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="红旗备注" prop="redFlagNotes">
        <el-input v-model="formData.redFlagNotes" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="备注" prop="note">
        <el-input v-model="formData.note" type="textarea" :rows="2" />
      </el-form-item>

      <el-divider content-position="left">评估表单</el-divider>

      <el-empty
        v-if="!formData.assessmentType"
        description="请选择评估类型以加载对应评估表单"
      />

      <template v-else>
        <el-alert
          type="info"
          :closable="false"
          class="mb-12px"
          :title="`当前类型：${selectedTypeLabel}`"
          :description="selectedTypeDescription"
        />

        <component
          v-if="selectedRegistryItem?.formComponent"
          :is="selectedRegistryItem.formComponent"
          ref="dynamicFormRef"
          v-model="currentFormData"
          :assessment-base-info="staticAssessmentBaseInfo"
        />

        <el-card v-else shadow="never">
          <el-empty description="该评估表单待配置，当前可保存空结构数据 {}" />
        </el-card>
      </template>

      <el-form-item class="mt-16px">
        <el-button type="primary" :loading="submitLoading" @click="submitForm('draft')">保存草稿</el-button>
        <el-button type="primary" plain :loading="submitLoading" @click="submitForm('continue')">
          保存并继续编辑
        </el-button>
        <el-button type="success" :loading="submitLoading" @click="submitForm('back')">保存并返回</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import {
  createRehabAssessment,
  getRehabAssessment,
  RehabAssessmentCreateReqVO,
  RehabAssessmentModuleDataItemVO,
  updateRehabAssessment
} from '@/api/rehab/assessment'
import { getRehabEpisodePage } from '@/api/rehab/episode'
import { getRehabPatient, getRehabPatientPage, RehabPatientVO } from '@/api/rehab/patient'
import {
  ASSESSMENT_TYPE_OPTIONS,
  getAssessmentTypeLabel
} from '@/views/rehab/assessment/config/assessmentTypeOptions'
import {
  AssessmentFormExpose,
  cloneDefaultAssessmentFormData,
  getAssessmentFormRegistryItem
} from '@/views/rehab/assessment/config/assessmentFormRegistry'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'RehabAssessmentCreateOrEdit' })

type SaveAction = 'draft' | 'continue' | 'back'
type FormStateSnapshot = {
  base: string
  module: string
}

const message = useMessage()
const route = useRoute()
const { push, replace } = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const patientLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const dynamicFormRef = ref<AssessmentFormExpose>()

const patientOptions = ref<RehabPatientVO[]>([])
const patientSnapshotMap = reactive<Record<number, any>>({})
const episodeOptions = ref<any[]>([])
const suppressPatientEpisodeReset = ref(false)
const episodeOptionsRequestId = ref(0)

const formData = reactive<any>({
  patientId: undefined,
  episodeId: undefined,
  assessmentType: undefined,
  assessmentDate: dayjs().format('YYYY-MM-DD'),
  chiefFocus: '',
  painScore: undefined,
  redFlagNotes: '',
  note: ''
})

const moduleFormDataMap = reactive<Record<string, Record<string, any>>>({})
const savedBaseState = ref('')
const savedModuleState = ref('')
const typeSwitchRollback = ref(false)

const rules = reactive({
  patientId: [{ required: true, message: '患者不能为空', trigger: 'change' }],
  episodeId: [{ required: true, message: 'episode 不能为空', trigger: 'change' }],
  assessmentType: [{ required: true, message: '评估类型不能为空', trigger: 'change' }],
  assessmentDate: [{ required: true, message: '评估日期不能为空', trigger: 'change' }]
})

const assessmentId = computed(() => Number(route.params.id || 0))
const isEditMode = computed(() => !!assessmentId.value)
const pageTitle = computed(() => (isEditMode.value ? '编辑评估' : '新建评估'))

const enabledAssessmentTypeOptions = computed(() => {
  return ASSESSMENT_TYPE_OPTIONS.filter((item) => item.enabled !== false)
})

const selectedRegistryItem = computed(() => getAssessmentFormRegistryItem(formData.assessmentType))
const selectedTypeLabel = computed(() => getAssessmentTypeLabel(formData.assessmentType))
const selectedTypeDescription = computed(
  () => selectedRegistryItem.value?.description || '该评估类型已选择，请完成对应评估内容。'
)
const selectedPatientSnapshot = computed(() => {
  const patientId = Number(formData.patientId || 0)
  if (!patientId) {
    return undefined
  }
  return patientSnapshotMap[patientId] || patientOptions.value.find((item) => item.id === patientId)
})
const staticAssessmentBaseInfo = computed(() => {
  const patient = selectedPatientSnapshot.value as any
  const gender = patient?.gender === 1 ? '男' : patient?.gender === 2 ? '女' : ''
  return {
    name: patient?.name || '',
    age: patient?.age ?? null,
    gender,
    height_cm: patient?.heightCm ?? null,
    weight_kg: patient?.weightKg ?? null,
    pain_score: formData.painScore ?? null,
    assessment_date: formData.assessmentDate || '',
    assessor: userStore.getUser?.nickname || ''
  }
})

const currentFormData = computed<Record<string, any>>({
  get() {
    const typeCode = formData.assessmentType
    if (!typeCode) {
      return {}
    }
    ensureTypeFormData(typeCode)
    return moduleFormDataMap[typeCode]
  },
  set(value) {
    const typeCode = formData.assessmentType
    if (!typeCode) {
      return
    }
    moduleFormDataMap[typeCode] = cloneData(value || {})
  }
})

const hasUnsavedChanges = computed(() => {
  return buildBaseState() !== savedBaseState.value || buildModuleState() !== savedModuleState.value
})

watch(
  () => formData.patientId,
  async (patientId) => {
    if (suppressPatientEpisodeReset.value) {
      return
    }
    formData.episodeId = undefined
    await loadEpisodeOptions(patientId)
  }
)

const handlePatientSearch = async (keyword?: string) => {
  await loadPatientOptions(keyword)
}

const loadPatientOptions = async (keyword?: string) => {
  patientLoading.value = true
  try {
    const data = await getRehabPatientPage({
      pageNo: 1,
      pageSize: 50,
      keyword: keyword || undefined
    })
    patientOptions.value = data.list || []
    patientOptions.value.forEach((item) => {
      if (item?.id) {
        patientSnapshotMap[item.id] = item
      }
    })
  } finally {
    patientLoading.value = false
  }
}

const appendPatientOption = (patient?: RehabPatientVO) => {
  if (!patient?.id) {
    return
  }
  patientSnapshotMap[patient.id] = patient
  const exists = patientOptions.value.some((item) => item.id === patient.id)
  if (!exists) {
    patientOptions.value.unshift(patient)
  }
}

const ensureCurrentPatientOption = async (patientId?: number) => {
  if (!patientId) {
    return
  }
  const exists = patientOptions.value.some((item) => item.id === patientId)
  if (exists) {
    return
  }
  const detail = await getRehabPatient(patientId)
  appendPatientOption(detail?.patient as any)
}

const loadEpisodeOptions = async (patientId = formData.patientId) => {
  const targetPatientId = Number(patientId || 0)
  const requestId = ++episodeOptionsRequestId.value
  if (!targetPatientId) {
    if (requestId === episodeOptionsRequestId.value) {
      episodeOptions.value = []
    }
    return
  }
  const data = await getRehabEpisodePage({ pageNo: 1, pageSize: 50, patientId: targetPatientId })
  // A remote patient search or an edit-detail load can overtake an older
  // episode request. Never let an old response clear or replace the current
  // patient's historical Episode selection.
  if (requestId !== episodeOptionsRequestId.value || Number(formData.patientId || 0) !== targetPatientId) {
    return
  }
  episodeOptions.value = data.list || []
  const active = episodeOptions.value.find((item: any) => item.status === 'active')
  if (!formData.episodeId && active) {
    formData.episodeId = active.id
  }
}

const ensureTypeFormData = (typeCode?: string) => {
  if (!typeCode) {
    return
  }
  if (!moduleFormDataMap[typeCode]) {
    moduleFormDataMap[typeCode] = cloneDefaultAssessmentFormData(typeCode)
  }
}

const buildBaseState = () => {
  return JSON.stringify({
    patientId: formData.patientId || null,
    episodeId: formData.episodeId || null,
    assessmentType: formData.assessmentType || null,
    assessmentDate: formData.assessmentDate || null,
    chiefFocus: formData.chiefFocus || '',
    painScore: formData.painScore ?? null,
    redFlagNotes: formData.redFlagNotes || '',
    note: formData.note || ''
  })
}

const buildModuleState = () => {
  return JSON.stringify(cloneData(moduleFormDataMap))
}

const buildFormStateSnapshot = (): FormStateSnapshot => {
  return {
    base: buildBaseState(),
    module: buildModuleState()
  }
}

const markSavedState = (snapshot: FormStateSnapshot = buildFormStateSnapshot()) => {
  savedBaseState.value = snapshot.base
  savedModuleState.value = snapshot.module
}

const cloneData = (value: any) => {
  return JSON.parse(JSON.stringify(value || {}))
}

const parseModuleDataJson = (value: any) => {
  if (value == null || value === '') {
    return {}
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return typeof parsed === 'object' && parsed !== null ? parsed : { value: parsed }
    } catch {
      return { raw: value }
    }
  }
  if (typeof value === 'object') {
    return cloneData(value)
  }
  return { value }
}

const getModuleDataForAssessmentType = (assessmentType: string, moduleList: any[]) => {
  if (!assessmentType || !Array.isArray(moduleList) || moduleList.length === 0) {
    return {}
  }
  const registryItem = getAssessmentFormRegistryItem(assessmentType)
  const targetModuleType = registryItem?.moduleType
  const matchedModule = targetModuleType
    ? moduleList.find((item: any) => item.moduleType === targetModuleType)
    : moduleList[0]
  return parseModuleDataJson(matchedModule?.dataJson)
}

const settleRenderedModuleFormData = async (assessmentType?: string) => {
  // Some structured forms normalize their v-model payload one or two render
  // ticks after receiving persisted data. Capture that normalized value before
  // recording the clean-state snapshot, otherwise Reset can immediately look
  // like an unsaved user edit.
  await nextTick()
  await nextTick()
  const renderedData = dynamicFormRef.value?.getFormData?.()
  if (assessmentType && renderedData) {
    moduleFormDataMap[assessmentType] = cloneData(renderedData)
    await nextTick()
  }
}

const handleAssessmentTypeChange = async (newType: string) => {
  if (typeSwitchRollback.value) {
    return
  }
  ensureTypeFormData(newType)
}

watch(
  () => formData.assessmentType,
  async (newType, oldType) => {
    if (typeSwitchRollback.value) {
      return
    }
    if (!newType || !oldType || newType === oldType) {
      ensureTypeFormData(newType)
      return
    }
    if (!hasUnsavedChanges.value) {
      ensureTypeFormData(newType)
      return
    }
    try {
      await message.confirm('当前有未保存内容，切换评估类型前请确认是否继续。')
      ensureTypeFormData(newType)
    } catch {
      typeSwitchRollback.value = true
      formData.assessmentType = oldType
      await nextTick()
      typeSwitchRollback.value = false
    }
  }
)

const resetCreateState = async () => {
  episodeOptionsRequestId.value += 1
  Object.assign(formData, {
    patientId: undefined,
    episodeId: undefined,
    assessmentType: undefined,
    assessmentDate: dayjs().format('YYYY-MM-DD'),
    chiefFocus: '',
    painScore: undefined,
    redFlagNotes: '',
    note: ''
  })
  Object.keys(moduleFormDataMap).forEach((key) => delete moduleFormDataMap[key])
  episodeOptions.value = []
  await loadPatientOptions()
  markSavedState()
}

const buildModuleDataList = async (action: SaveAction): Promise<RehabAssessmentModuleDataItemVO[]> => {
  const assessmentType = formData.assessmentType
  if (!assessmentType) {
    return []
  }
  const registryItem = selectedRegistryItem.value
  if (!registryItem) {
    return []
  }

  // A draft is a recoverable work-in-progress. Only a completion action asks
  // the dynamic form to enforce its clinical completeness rules.
  if (action !== 'draft') {
    const valid = await dynamicFormRef.value?.validate?.()
    if (valid === false) {
      throw new Error('当前评估表单校验未通过，请检查输入后重试')
    }
  }

  const rawData = dynamicFormRef.value?.getFormData?.() || currentFormData.value || {}
  const dataJson = cloneData(rawData)
  return [
    {
      moduleType: registryItem.moduleType,
      moduleStatus: action === 'draft' ? 'partial' : 'completed',
      dataJson,
      sourceType: 'manual',
      version: 'v1'
    }
  ]
}

const submitForm = async (action: SaveAction) => {
  if (submitLoading.value) {
    return
  }
  submitLoading.value = true
  try {
    await formRef.value.validate()
    const moduleDataList = await buildModuleDataList(action)
    const savingRoute = route.fullPath
    const payload: RehabAssessmentCreateReqVO = {
      ...cloneData(formData),
      status: action === 'draft' ? 'draft' : undefined,
      moduleDataList: cloneData(moduleDataList)
    }

    if (isEditMode.value) {
      const id = assessmentId.value
      await updateRehabAssessment({ id, ...payload })
      if (route.fullPath !== savingRoute) {
        return
      }
      // The form is disabled and covered by the loading mask while the
      // request is in flight, so this current snapshot cannot absorb a new
      // user edit. It does include any form-local normalization that happened
      // while the request was being built.
      markSavedState()
      const hasNewerEdits = hasUnsavedChanges.value
      message.success(hasNewerEdits ? '评估保存成功；保存期间有新的修改，请再次保存' : '评估保存成功')
      if (action === 'back' && !hasNewerEdits) {
        push(`/rehab/assessment/detail/${id}`)
      }
      return
    }

    const resp = await createRehabAssessment(payload)
    if (route.fullPath !== savingRoute) {
      return
    }
    markSavedState()
    const hasNewerEdits = hasUnsavedChanges.value
    message.success(hasNewerEdits ? '评估创建成功；保存期间有新的修改，请再次保存' : '评估创建成功')

    if (action === 'back' && !hasNewerEdits) {
      push(`/rehab/assessment/detail/${resp.id}`)
      return
    }
    if (!hasNewerEdits) {
      replace(`/rehab/assessment/edit/${resp.id}`)
    }
  } catch (error: any) {
    if (error?.message) {
      message.error(error.message)
    }
  } finally {
    submitLoading.value = false
  }
}

const loadEditDetail = async (id: number) => {
  const detail = await getRehabAssessment(id)
  const assessment = detail?.assessment || {}

  suppressPatientEpisodeReset.value = true
  episodeOptionsRequestId.value += 1
  try {
    Object.assign(formData, {
      patientId: assessment.patientId,
      episodeId: assessment.episodeId,
      assessmentType: assessment.assessmentType,
      assessmentDate: Array.isArray(assessment.assessmentDate)
        ? assessment.assessmentDate
            .slice(0, 3)
            .map((part: number, index: number) => (index === 0 ? String(part) : String(part).padStart(2, '0')))
            .join('-')
        : assessment.assessmentDate || dayjs().format('YYYY-MM-DD'),
      chiefFocus: assessment.chiefFocus || '',
      painScore: assessment.painScore,
      redFlagNotes: assessment.redFlagNotes || '',
      note: assessment.note || ''
    })

    appendPatientOption(detail?.patient)
    await ensureCurrentPatientOption(formData.patientId)
    await loadEpisodeOptions(assessment.patientId)

    const moduleData = getModuleDataForAssessmentType(assessment.assessmentType, detail?.moduleDataList || [])
    ensureTypeFormData(assessment.assessmentType)
    if (assessment.assessmentType) {
      moduleFormDataMap[assessment.assessmentType] = moduleData
    }
    await settleRenderedModuleFormData(assessment.assessmentType)
    markSavedState()
  } finally {
    // The patient watcher is queued by the assignment above. Keep the guard
    // through its flush so it cannot erase the historic Episode afterwards.
    await nextTick()
    suppressPatientEpisodeReset.value = false
  }
}

const handleReset = async () => {
  if (isEditMode.value) {
    await loadEditDetail(assessmentId.value)
    message.success('已恢复为最近一次保存内容')
    return
  }
  await resetCreateState()
}

const goBack = () => {
  if (formData.patientId) {
    push(`/rehab/patient/detail/${formData.patientId}`)
    return
  }
  push('/rehab/assessment')
}

onBeforeRouteLeave(async () => {
  if (submitLoading.value) {
    return false
  }
  if (!hasUnsavedChanges.value) {
    return true
  }
  try {
    await message.confirm('当前评估存在未保存修改，确认离开将丢失这些修改。')
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  loading.value = true
  try {
    await loadPatientOptions()
    if (isEditMode.value) {
      await loadEditDetail(assessmentId.value)
      return
    }
    const queryPatientId = Number(route.query.patientId)
    if (queryPatientId) {
      formData.patientId = queryPatientId
      await ensureCurrentPatientOption(queryPatientId)
      await loadEpisodeOptions()
    }
    markSavedState()
  } finally {
    loading.value = false
  }
})
</script>
