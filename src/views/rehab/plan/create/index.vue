<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <div class="text-16px font-bold">新建计划</div>
      <el-button @click="goBack">返回</el-button>
    </div>

    <el-form ref="formRef" v-loading="loading" :model="formData" :rules="rules" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="患者ID" prop="patientId">
            <el-input-number v-model="formData.patientId" :min="1" controls-position="right" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Episode" prop="episodeId">
            <el-select v-model="formData.episodeId" filterable clearable class="!w-full" placeholder="选择 episode">
              <el-option
                v-for="item in episodeOptions"
                :key="item.id"
                :label="`${item.episodeNo} (${item.currentStage || item.status})`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源评估" prop="sourceAssessmentId">
            <el-select v-model="formData.sourceAssessmentId" filterable clearable class="!w-full" placeholder="可选">
              <el-option
                v-for="item in assessmentOptions"
                :key="item.id"
                :label="`${item.assessmentNo} (${item.assessmentType})`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="计划名称" prop="planName">
            <el-input v-model="formData.planName" placeholder="例如：左膝稳定性四周计划" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="计划类型" prop="planType">
            <el-select v-model="formData.planType" class="!w-full">
              <el-option label="rehab" value="rehab" />
              <el-option label="maintenance" value="maintenance" />
              <el-option label="return_to_sport" value="return_to_sport" />
              <el-option label="home_program" value="home_program" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="主责治疗师" prop="primaryTherapistUserId">
            <el-select v-model="formData.primaryTherapistUserId" clearable filterable class="!w-full">
              <el-option v-for="u in therapistOptions" :key="u.id" :label="`${u.nickname}(${u.id})`" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="formData.startDate" value-format="YYYY-MM-DD" type="date" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="周期(天)" prop="cycleDays">
            <el-input-number v-model="formData.cycleDays" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="复评周期(天)" prop="reviewCycleDays">
            <el-input-number v-model="formData.reviewCycleDays" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="强度等级" prop="intensityLevel">
            <el-select v-model="formData.intensityLevel" class="!w-full">
              <el-option label="low" value="low" />
              <el-option label="medium" value="medium" />
              <el-option label="high" value="high" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="短期目标(JSON)" prop="shortTermGoalsJson">
            <el-input
              v-model="formData.shortTermGoalsJson"
              type="textarea"
              :rows="3"
              placeholder='例如：[{"goal":"疼痛控制至NRS<=2"}]'
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="中期目标(JSON)" prop="midTermGoalsJson">
            <el-input v-model="formData.midTermGoalsJson" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="长期目标(JSON)" prop="longTermGoalsJson">
            <el-input v-model="formData.longTermGoalsJson" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="禁忌" prop="contraindications">
            <el-input v-model="formData.contraindications" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="注意事项" prop="precautions">
        <el-input v-model="formData.precautions" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="备注" prop="note">
        <el-input v-model="formData.note" type="textarea" :rows="2" />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="启用家庭训练" prop="homeProgramEnabled">
            <el-switch v-model="formData.homeProgramEnabled" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="启用院内训练" prop="clinicProgramEnabled">
            <el-switch v-model="formData.clinicProgramEnabled" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="创建后状态" prop="status">
            <el-radio-group v-model="formData.status">
              <el-radio label="draft">draft</el-radio>
              <el-radio label="active">active</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-alert type="info" :closable="false" class="mb-12px" title="任务可在保存后于“计划详情”页面维护。" />

      <el-form-item>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
        <el-button @click="goBack">取消</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import * as UserApi from '@/api/system/user'
import { createRehabPlan } from '@/api/rehab/plan'
import { getRehabEpisodePage } from '@/api/rehab/episode'
import { getRehabAssessmentPage } from '@/api/rehab/assessment'

defineOptions({ name: 'RehabPlanCreate' })

const message = useMessage()
const route = useRoute()
const { push } = useRouter()

const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const therapistOptions = ref<UserApi.UserVO[]>([])
const episodeOptions = ref<any[]>([])
const assessmentOptions = ref<any[]>([])

const formData = reactive<Record<string, any>>({
  patientId: undefined,
  episodeId: undefined,
  sourceAssessmentId: undefined,
  primaryTherapistUserId: undefined,
  planName: '',
  planType: 'rehab',
  status: 'draft',
  startDate: dayjs().format('YYYY-MM-DD'),
  cycleDays: 28,
  shortTermGoalsJson: '',
  midTermGoalsJson: '',
  longTermGoalsJson: '',
  contraindications: '',
  precautions: '',
  homeProgramEnabled: true,
  clinicProgramEnabled: true,
  intensityLevel: 'medium',
  reviewCycleDays: 14,
  note: ''
})

const rules = reactive({
  patientId: [{ required: true, message: '患者ID不能为空', trigger: 'blur' }],
  episodeId: [{ required: true, message: 'episode 不能为空', trigger: 'change' }],
  planName: [{ required: true, message: '计划名称不能为空', trigger: 'blur' }],
  planType: [{ required: true, message: '计划类型不能为空', trigger: 'change' }],
  startDate: [{ required: true, message: '开始日期不能为空', trigger: 'change' }]
})

const loadTherapists = async () => {
  therapistOptions.value = await UserApi.getSimpleUserList()
}

const loadEpisodes = async () => {
  if (!formData.patientId) {
    episodeOptions.value = []
    return
  }
  const data = await getRehabEpisodePage({ pageNo: 1, pageSize: 50, patientId: formData.patientId })
  episodeOptions.value = data.list || []
  const active = episodeOptions.value.find((item: any) => item.status === 'active')
  if (!formData.episodeId && active) {
    formData.episodeId = active.id
  }
}

const loadAssessments = async () => {
  if (!formData.patientId) {
    assessmentOptions.value = []
    return
  }
  const data = await getRehabAssessmentPage({
    pageNo: 1,
    pageSize: 50,
    patientId: formData.patientId,
    episodeId: formData.episodeId,
    status: undefined
  })
  assessmentOptions.value = data.list || []
}

watch(
  () => formData.patientId,
  async () => {
    formData.episodeId = undefined
    formData.sourceAssessmentId = undefined
    await Promise.all([loadEpisodes(), loadAssessments()])
  }
)

watch(
  () => formData.episodeId,
  () => {
    formData.sourceAssessmentId = undefined
    loadAssessments()
  }
)

const submitForm = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const resp = await createRehabPlan({ ...formData })
    message.success('计划创建成功')
    push(`/rehab/plan/detail/${resp.id}`)
  } finally {
    submitLoading.value = false
  }
}

const goBack = () => {
  if (formData.patientId) {
    push(`/rehab/patient/detail/${formData.patientId}`)
    return
  }
  push('/rehab/plan')
}

onMounted(async () => {
  loading.value = true
  try {
    const queryPatientId = Number(route.query.patientId)
    const queryEpisodeId = Number(route.query.episodeId)
    const queryAssessmentId = Number(route.query.sourceAssessmentId)
    if (queryPatientId) formData.patientId = queryPatientId
    if (queryEpisodeId) formData.episodeId = queryEpisodeId
    if (queryAssessmentId) formData.sourceAssessmentId = queryAssessmentId

    await Promise.all([loadTherapists(), loadEpisodes(), loadAssessments()])
  } finally {
    loading.value = false
  }
})
</script>
