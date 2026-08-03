<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="80px">
      <el-form-item label="患者" prop="patientId">
        <el-select
          v-model="queryParams.patientId"
          clearable
          filterable
          remote
          reserve-keyword
          :remote-method="loadPatientOptions"
          :loading="patientLoading"
          placeholder="姓名 / 患者编号 / 手机号"
          class="!w-240px"
        >
          <el-option
            v-for="patient in patientOptions"
            :key="patient.id"
            :label="`${patient.name}（${patient.patientNo}）`"
            :value="patient.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="计划ID" prop="planId">
        <el-input-number v-model="queryParams.planId" :min="1" controls-position="right" class="!w-150px" />
      </el-form-item>
      <el-form-item label="训练日期" prop="checkinDate">
        <el-date-picker
          v-model="queryParams.checkinDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-250px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" v-hasPermi="['rehab:checkin:create-manual']" @click="openAttendanceDialog">
          <Icon icon="ep:calendar-checked" /> 签到上课
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-alert
      title="课程签到只记录患者的实际上课日期，不会自动完成训练任务或改变计划进度；同一天多节课可分别签到。"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="患者" min-width="180">
        <template #default="scope">{{ scope.row.patientName }}（{{ scope.row.patientNo }}）</template>
      </el-table-column>
      <el-table-column label="训练计划" min-width="160">
        <template #default="scope">{{ scope.row.planNo || scope.row.planId }}</template>
      </el-table-column>
      <el-table-column label="训练日期" prop="checkinDate" min-width="120" :formatter="dateFormatter2" />
      <el-table-column label="记录类型" min-width="110">
        <template #default="scope">
          <el-tag v-if="isAttendance(scope.row)" type="success">课程签到</el-tag>
          <el-tag v-else type="info">详细打卡</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到人" prop="submitterName" min-width="120" />
      <el-table-column label="备注" prop="overallComment" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.overallComment || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button
            v-if="!isAttendance(scope.row)"
            type="primary"
            link
            v-hasPermi="['rehab:checkin:detail']"
            @click="openExecutionDialog(scope.row)"
          >
            任务明细
          </el-button>
          <span v-else class="text-[var(--el-text-color-secondary)]">—</span>
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

  <Dialog v-model="executionDialog.visible" title="历史详细打卡任务" width="900px">
    <el-table :data="executionDialog.list" stripe>
      <el-table-column label="任务" prop="taskName" min-width="160" />
      <el-table-column label="状态" prop="completionStatus" min-width="120" />
      <el-table-column label="完成组数" prop="completedSets" min-width="100" />
      <el-table-column label="完成次数" prop="completedReps" min-width="100" />
      <el-table-column label="疼痛" prop="painScore" min-width="90" />
      <el-table-column label="备注" prop="taskComment" min-width="200" show-overflow-tooltip />
    </el-table>
  </Dialog>

  <Dialog v-model="attendanceDialog.visible" title="患者课程签到" width="620px">
    <el-form ref="attendanceFormRef" :model="attendanceForm" :rules="attendanceRules" label-width="100px">
      <el-form-item label="患者" prop="patientId">
        <el-select
          v-model="attendanceForm.patientId"
          filterable
          remote
          reserve-keyword
          :remote-method="loadPatientOptions"
          :loading="patientLoading"
          placeholder="搜索并选择患者"
          class="!w-full"
          @change="handleAttendancePatientChange"
        >
          <el-option
            v-for="patient in patientOptions"
            :key="patient.id"
            :label="`${patient.name}（${patient.patientNo}）`"
            :value="patient.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="训练计划" prop="planId">
        <el-select
          v-model="attendanceForm.planId"
          :loading="planLoading"
          :disabled="!attendanceForm.patientId"
          placeholder="选择患者的执行中计划"
          class="!w-full"
        >
          <el-option
            v-for="plan in planOptions"
            :key="plan.id"
            :label="`${plan.planName || '未命名计划'}（${plan.planNo}）`"
            :value="plan.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="训练日期" prop="trainingDate">
        <el-date-picker
          v-model="attendanceForm.trainingDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择实际上课日期"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="备注" prop="note">
        <el-input
          v-model="attendanceForm.note"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="可选，例如：第 2 节课、迟到 10 分钟"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="attendanceDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="attendanceDialog.loading" @click="submitAttendance">
        确认签到
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { dateFormatter2 } from '@/utils/formatTime'
import {
  createRehabTrainingAttendance,
  getRehabCheckinPage,
  getRehabCheckinTaskExecutions,
  RehabCheckinPageReqVO
} from '@/api/rehab/checkin'
import { getRehabPatientPage, RehabPatientVO } from '@/api/rehab/patient'
import { getRehabPlan, getRehabPlanPage } from '@/api/rehab/plan'

defineOptions({ name: 'RehabCheckin' })

const route = useRoute()
const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryFormRef = ref()
const patientLoading = ref(false)
const patientOptions = ref<RehabPatientVO[]>([])
const planLoading = ref(false)
const planOptions = ref<any[]>([])

const queryParams = reactive<RehabCheckinPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  episodeId: undefined,
  planId: undefined,
  submitRoleType: undefined,
  checkinDate: []
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabCheckinPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const loadPatientOptions = async (keyword = '') => {
  patientLoading.value = true
  try {
    const data = await getRehabPatientPage({ pageNo: 1, pageSize: 100, keyword: keyword || undefined })
    patientOptions.value = data.list || []
  } finally {
    patientLoading.value = false
  }
}

const ensurePatientOption = (patient: any) => {
  if (patient?.id && !patientOptions.value.some((item) => item.id === patient.id)) {
    patientOptions.value.unshift({
      id: patient.id,
      patientNo: patient.patientNo,
      name: patient.patientName
    })
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const isAttendance = (row: any) => row.courseAttendance === true

const executionDialog = reactive({ visible: false, list: [] as any[] })
const openExecutionDialog = async (row: any) => {
  executionDialog.list = await getRehabCheckinTaskExecutions(row.id)
  executionDialog.visible = true
}

const attendanceDialog = reactive({ visible: false, loading: false })
const attendanceFormRef = ref()
const attendanceForm = reactive({
  patientId: undefined as number | undefined,
  planId: undefined as number | undefined,
  trainingDate: dayjs().format('YYYY-MM-DD'),
  note: ''
})
const attendanceRules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'change' }],
  planId: [{ required: true, message: '请选择训练计划', trigger: 'change' }],
  trainingDate: [{ required: true, message: '请选择实际上课日期', trigger: 'change' }]
}

const loadActivePlans = async (patientId?: number) => {
  planOptions.value = []
  if (!patientId) return
  planLoading.value = true
  try {
    const data = await getRehabPlanPage({ pageNo: 1, pageSize: 100, patientId, status: 'active' })
    planOptions.value = data.list || []
  } finally {
    planLoading.value = false
  }
}

const handleAttendancePatientChange = async (patientId?: number) => {
  attendanceForm.planId = undefined
  await loadActivePlans(patientId)
}

const openAttendanceDialog = async () => {
  attendanceForm.patientId = queryParams.patientId
  attendanceForm.planId = undefined
  attendanceForm.trainingDate = dayjs().format('YYYY-MM-DD')
  attendanceForm.note = ''
  attendanceDialog.visible = true
  await loadActivePlans(attendanceForm.patientId)
  if (queryParams.planId && planOptions.value.some((plan) => plan.id === queryParams.planId)) {
    attendanceForm.planId = queryParams.planId
  }
}

const submitAttendance = async () => {
  await attendanceFormRef.value.validate()
  attendanceDialog.loading = true
  try {
    await createRehabTrainingAttendance({
      patientId: attendanceForm.patientId!,
      planId: attendanceForm.planId!,
      trainingDate: attendanceForm.trainingDate,
      note: attendanceForm.note.trim() || undefined
    })
    message.success('课程签到成功')
    attendanceDialog.visible = false
    await getList()
  } finally {
    attendanceDialog.loading = false
  }
}

onMounted(async () => {
  await loadPatientOptions()
  const planId = Number(route.query.planId)
  const patientId = Number(route.query.patientId)
  if (planId) {
    const plan = await getRehabPlan(planId)
    queryParams.planId = planId
    queryParams.patientId = plan.patientId
    ensurePatientOption(plan)
  } else if (patientId) {
    queryParams.patientId = patientId
  }
  await getList()
})
</script>
