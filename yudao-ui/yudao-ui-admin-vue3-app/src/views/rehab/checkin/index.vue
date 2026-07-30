<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="90px">
      <el-form-item label="患者ID" prop="patientId">
        <el-input-number v-model="queryParams.patientId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="计划ID" prop="planId">
        <el-input-number v-model="queryParams.planId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="角色" prop="submitRoleType">
        <el-select v-model="queryParams.submitRoleType" clearable class="!w-140px">
          <el-option label="patient" value="patient" />
          <el-option label="therapist" value="therapist" />
          <el-option label="clerk" value="clerk" />
        </el-select>
      </el-form-item>
      <el-form-item label="打卡日期" prop="checkinDate">
        <el-date-picker
          v-model="queryParams.checkinDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          class="!w-250px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:checkin:create-manual']" @click="openCreateDialog">
          <Icon icon="ep:plus" /> 代录打卡
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="ID" prop="id" min-width="80" />
      <el-table-column label="患者" min-width="160">
        <template #default="scope">{{ scope.row.patientName }} ({{ scope.row.patientNo }})</template>
      </el-table-column>
      <el-table-column label="plan_no" prop="planNo" min-width="150" />
      <el-table-column label="日期" prop="checkinDate" min-width="110" :formatter="dateFormatter2" />
      <el-table-column label="角色" prop="submitRoleType" min-width="100" />
      <el-table-column label="提交人" prop="submitterName" min-width="120" />
      <el-table-column label="完成率" prop="overallCompletionRate" min-width="90">
        <template #default="scope">{{ scope.row.overallCompletionRate ?? '-' }}%</template>
      </el-table-column>
      <el-table-column label="疼痛(前/后)" min-width="120">
        <template #default="scope">{{ scope.row.painScoreBefore ?? '-' }}/{{ scope.row.painScoreAfter ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="overallComment" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" min-width="120" fixed="right">
        <template #default="scope">
          <el-button type="primary" link v-hasPermi="['rehab:checkin:detail']" @click="openExecutionDialog(scope.row)">
            执行明细
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

  <Dialog v-model="executionDialog.visible" title="任务执行明细" width="900px">
    <el-table :data="executionDialog.list" stripe>
      <el-table-column label="任务" prop="taskName" min-width="160" />
      <el-table-column label="状态" prop="completionStatus" min-width="120" />
      <el-table-column label="完成组数" prop="completedSets" min-width="100" />
      <el-table-column label="完成次数" prop="completedReps" min-width="100" />
      <el-table-column label="疼痛" prop="painScore" min-width="90" />
      <el-table-column label="难度" prop="difficultyLevel" min-width="90" />
      <el-table-column label="症状" min-width="90">
        <template #default="scope">{{ scope.row.symptomFlag ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="taskComment" min-width="200" show-overflow-tooltip />
    </el-table>
  </Dialog>

  <Dialog v-model="createDialog.visible" title="代录打卡" width="980px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="患者ID" prop="patientId">
            <el-input-number v-model="createForm.patientId" :min="1" controls-position="right" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Episode ID" prop="episodeId">
            <el-input-number v-model="createForm.episodeId" :min="1" controls-position="right" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="计划ID" prop="planId">
            <el-input-number v-model="createForm.planId" :min="1" controls-position="right" class="!w-full" @change="loadTaskByPlan" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="打卡日期" prop="checkinDate">
            <el-date-picker v-model="createForm.checkinDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="提交角色" prop="submitRoleType">
            <el-select v-model="createForm.submitRoleType" class="!w-full">
              <el-option label="therapist" value="therapist" />
              <el-option label="clerk" value="clerk" />
              <el-option label="patient" value="patient" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="整体完成率" prop="overallCompletionRate">
            <el-input-number v-model="createForm.overallCompletionRate" :min="0" :max="100" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="疼痛(前)" prop="painScoreBefore">
            <el-input-number v-model="createForm.painScoreBefore" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="疼痛(后)" prop="painScoreAfter">
            <el-input-number v-model="createForm.painScoreAfter" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="疲劳等级" prop="fatigueLevel">
            <el-input-number v-model="createForm.fatigueLevel" :min="0" :max="10" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="信心等级" prop="confidenceLevel">
            <el-input-number v-model="createForm.confidenceLevel" :min="0" :max="10" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="备注" prop="overallComment">
        <el-input v-model="createForm.overallComment" type="textarea" :rows="2" />
      </el-form-item>

      <el-divider content-position="left">任务执行</el-divider>
      <el-alert type="info" :closable="false" class="mb-12px" title="请先填写计划ID以加载任务。" />
      <el-table :data="createForm.taskExecutions" stripe>
        <el-table-column label="任务" min-width="160">
          <template #default="scope">{{ scope.row.taskName }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="140">
          <template #default="scope">
            <el-select v-model="scope.row.completionStatus" class="!w-full">
              <el-option label="completed" value="completed" />
              <el-option label="partial" value="partial" />
              <el-option label="skipped" value="skipped" />
              <el-option label="pain_stop" value="pain_stop" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="组数" min-width="100">
          <template #default="scope">
            <el-input-number v-model="scope.row.completedSets" :min="0" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="次数" min-width="100">
          <template #default="scope">
            <el-input-number v-model="scope.row.completedReps" :min="0" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="疼痛" min-width="90">
          <template #default="scope">
            <el-input-number v-model="scope.row.painScore" :min="0" :max="10" :step="0.5" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="症状" min-width="90">
          <template #default="scope">
            <el-switch v-model="scope.row.symptomFlag" />
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <template #footer>
      <el-button @click="createDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="createDialog.loading" @click="submitCreate">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { dateFormatter2 } from '@/utils/formatTime'
import dayjs from 'dayjs'
import {
  createRehabCheckinManual,
  getRehabCheckinPage,
  getRehabCheckinTaskExecutions,
  RehabCheckinPageReqVO
} from '@/api/rehab/checkin'
import { getRehabTaskListByPlan } from '@/api/rehab/task'

defineOptions({ name: 'RehabCheckin' })

const route = useRoute()
const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryFormRef = ref()

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

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const executionDialog = reactive({
  visible: false,
  list: [] as any[]
})

const openExecutionDialog = async (row: any) => {
  executionDialog.list = await getRehabCheckinTaskExecutions(row.id)
  executionDialog.visible = true
}

const createDialog = reactive({ visible: false, loading: false })
const createFormRef = ref()
const createForm = reactive<Record<string, any>>({
  patientId: undefined,
  episodeId: undefined,
  planId: undefined,
  checkinDate: dayjs().format('YYYY-MM-DD'),
  submitRoleType: 'therapist',
  overallCompletionRate: undefined,
  painScoreBefore: undefined,
  painScoreAfter: undefined,
  fatigueLevel: undefined,
  confidenceLevel: undefined,
  overallComment: '',
  taskExecutions: [] as any[]
})
const createRules = reactive({
  patientId: [{ required: true, message: '患者ID不能为空', trigger: 'blur' }],
  episodeId: [{ required: true, message: 'Episode ID不能为空', trigger: 'blur' }],
  planId: [{ required: true, message: '计划ID不能为空', trigger: 'blur' }],
  checkinDate: [{ required: true, message: '打卡日期不能为空', trigger: 'change' }],
  submitRoleType: [{ required: true, message: '提交角色不能为空', trigger: 'change' }]
})

const openCreateDialog = async () => {
  createForm.patientId = Number(queryParams.patientId) || undefined
  createForm.episodeId = undefined
  createForm.planId = Number(queryParams.planId) || undefined
  createForm.checkinDate = dayjs().format('YYYY-MM-DD')
  createForm.submitRoleType = 'therapist'
  createForm.overallCompletionRate = undefined
  createForm.painScoreBefore = undefined
  createForm.painScoreAfter = undefined
  createForm.fatigueLevel = undefined
  createForm.confidenceLevel = undefined
  createForm.overallComment = ''
  createForm.taskExecutions = []
  createDialog.visible = true
  if (createForm.planId) {
    await loadTaskByPlan()
  }
}

const loadTaskByPlan = async () => {
  if (!createForm.planId) {
    createForm.taskExecutions = []
    return
  }
  const tasks = await getRehabTaskListByPlan(createForm.planId)
  createForm.taskExecutions = (tasks || [])
    .filter((item: any) => item.status !== 'disabled')
    .map((item: any) => ({
      taskId: item.id,
      taskName: item.taskName,
      completionStatus: 'completed',
      completedSets: item.sets || 0,
      completedReps: item.repetitions || 0,
      perceivedExertion: undefined,
      painScore: undefined,
      difficultyLevel: undefined,
      symptomFlag: false,
      symptomNote: '',
      taskComment: ''
    }))
}

const submitCreate = async () => {
  await createFormRef.value.validate()
  if (!createForm.taskExecutions.length) {
    message.warning('请先加载并填写任务执行信息')
    return
  }
  createDialog.loading = true
  try {
    await createRehabCheckinManual({
      patientId: createForm.patientId,
      episodeId: createForm.episodeId,
      planId: createForm.planId,
      checkinDate: createForm.checkinDate,
      submitRoleType: createForm.submitRoleType,
      overallCompletionRate: createForm.overallCompletionRate,
      painScoreBefore: createForm.painScoreBefore,
      painScoreAfter: createForm.painScoreAfter,
      fatigueLevel: createForm.fatigueLevel,
      confidenceLevel: createForm.confidenceLevel,
      overallComment: createForm.overallComment,
      taskExecutions: createForm.taskExecutions.map((item: any) => ({
        taskId: item.taskId,
        completionStatus: item.completionStatus,
        completedSets: item.completedSets,
        completedReps: item.completedReps,
        perceivedExertion: item.perceivedExertion,
        painScore: item.painScore,
        difficultyLevel: item.difficultyLevel,
        symptomFlag: item.symptomFlag,
        symptomNote: item.symptomNote,
        taskComment: item.taskComment
      }))
    })
    message.success('代录打卡成功')
    createDialog.visible = false
    await getList()
  } finally {
    createDialog.loading = false
  }
}

onMounted(() => {
  const planId = Number(route.query.planId)
  const patientId = Number(route.query.patientId)
  if (planId) queryParams.planId = planId
  if (patientId) queryParams.patientId = patientId
  getList()
})
</script>
