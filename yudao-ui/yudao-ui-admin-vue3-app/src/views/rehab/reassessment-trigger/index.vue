<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="95px">
      <el-form-item label="患者ID" prop="patientId">
        <el-input-number v-model="queryParams.patientId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="计划ID" prop="planId">
        <el-input-number v-model="queryParams.planId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="触发类型" prop="triggerType">
        <el-select v-model="queryParams.triggerType" clearable class="!w-150px">
          <el-option label="time_due" value="time_due" />
          <el-option label="pain_upgrade" value="pain_upgrade" />
          <el-option label="low_adherence" value="low_adherence" />
          <el-option label="stage_end" value="stage_end" />
          <el-option label="target_not_met" value="target_not_met" />
          <el-option label="target_met" value="target_met" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发等级" prop="triggerLevel">
        <el-select v-model="queryParams.triggerLevel" clearable class="!w-130px">
          <el-option label="low" value="low" />
          <el-option label="medium" value="medium" />
          <el-option label="high" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发状态" prop="triggerStatus">
        <el-select v-model="queryParams.triggerStatus" clearable class="!w-190px">
          <el-option label="pending" value="pending" />
          <el-option label="acknowledged" value="acknowledged" />
          <el-option label="converted_to_reassessment" value="converted_to_reassessment" />
          <el-option label="dismissed" value="dismissed" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始"
          end-placeholder="结束"
          class="!w-270px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:reassessment-trigger:create']" @click="openCreateDialog">
          <Icon icon="ep:plus" /> 新增触发
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
      <el-table-column label="类型" prop="triggerType" min-width="120" />
      <el-table-column label="等级" prop="triggerLevel" min-width="90" />
      <el-table-column label="状态" prop="triggerStatus" min-width="180" />
      <el-table-column label="触发说明" prop="triggerMessage" min-width="220" show-overflow-tooltip />
      <el-table-column label="建议动作" prop="suggestedAction" min-width="220" show-overflow-tooltip />
      <el-table-column label="到期" prop="dueDate" min-width="110" :formatter="dateFormatter2" />
      <el-table-column label="确认人" prop="acknowledgedByName" min-width="120" />
      <el-table-column label="操作" min-width="260" fixed="right">
        <template #default="scope">
          <el-button type="primary" link @click="openPatient(scope.row)">患者</el-button>
          <el-button
            type="primary"
            link
            v-hasPermi="['rehab:reassessment-trigger:handle']"
            @click="handleTrigger(scope.row, 'ack')"
          >
            确认
          </el-button>
          <el-button
            type="primary"
            link
            v-hasPermi="['rehab:reassessment-trigger:handle']"
            @click="handleTrigger(scope.row, 'convert')"
          >
            转复评
          </el-button>
          <el-button
            type="danger"
            link
            v-hasPermi="['rehab:reassessment-trigger:handle']"
            @click="handleTrigger(scope.row, 'dismiss')"
          >
            忽略
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

  <Dialog v-model="createDialog.visible" title="创建复评触发" width="680px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="患者ID" prop="patientId">
            <el-input-number v-model="createForm.patientId" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Episode ID" prop="episodeId">
            <el-input-number v-model="createForm.episodeId" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="计划ID" prop="planId">
            <el-input-number v-model="createForm.planId" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="触发类型" prop="triggerType">
        <el-select v-model="createForm.triggerType" class="!w-full">
          <el-option label="time_due" value="time_due" />
          <el-option label="pain_upgrade" value="pain_upgrade" />
          <el-option label="low_adherence" value="low_adherence" />
          <el-option label="stage_end" value="stage_end" />
          <el-option label="target_not_met" value="target_not_met" />
          <el-option label="target_met" value="target_met" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发等级" prop="triggerLevel">
        <el-select v-model="createForm.triggerLevel" class="!w-full">
          <el-option label="low" value="low" />
          <el-option label="medium" value="medium" />
          <el-option label="high" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发说明" prop="triggerMessage">
        <el-input v-model="createForm.triggerMessage" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="建议动作" prop="suggestedAction">
        <el-input v-model="createForm.suggestedAction" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="建议处理日期" prop="dueDate">
        <el-date-picker v-model="createForm.dueDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
      </el-form-item>
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
  acknowledgeRehabTrigger,
  convertRehabTrigger,
  createRehabTrigger,
  dismissRehabTrigger,
  getRehabTriggerPage,
  RehabTriggerPageReqVO
} from '@/api/rehab/reassessment-trigger'

defineOptions({ name: 'RehabReassessmentTrigger' })

const route = useRoute()
const { push } = useRouter()
const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const queryFormRef = ref()

const queryParams = reactive<RehabTriggerPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  episodeId: undefined,
  planId: undefined,
  triggerType: undefined,
  triggerLevel: undefined,
  triggerStatus: undefined,
  createTime: []
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabTriggerPage(queryParams)
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

const openPatient = (row: any) => {
  if (row.patientId) {
    push(`/rehab/patient/detail/${row.patientId}`)
  }
}

const handleTrigger = async (row: any, action: 'ack' | 'convert' | 'dismiss') => {
  if (action === 'ack') {
    await message.confirm('确认该触发吗？')
    await acknowledgeRehabTrigger({ id: row.id })
    message.success('已确认')
  }
  if (action === 'dismiss') {
    await message.confirm('确认忽略该触发吗？')
    await dismissRehabTrigger({ id: row.id })
    message.success('已忽略')
  }
  if (action === 'convert') {
    await message.confirm('确认转为复评入口吗？')
    const data = await convertRehabTrigger({ id: row.id })
    message.success(data.message || '已转复评')
    if (data.reassessmentEntry) {
      push(data.reassessmentEntry)
    }
  }
  await getList()
}

const createDialog = reactive({ visible: false, loading: false })
const createFormRef = ref()
const createForm = reactive<Record<string, any>>({
  patientId: undefined,
  episodeId: undefined,
  planId: undefined,
  triggerType: 'time_due',
  triggerLevel: 'medium',
  triggerMessage: '',
  suggestedAction: '',
  dueDate: dayjs().add(2, 'day').format('YYYY-MM-DD')
})
const createRules = reactive({
  patientId: [{ required: true, message: '患者ID不能为空', trigger: 'blur' }],
  episodeId: [{ required: true, message: 'Episode ID不能为空', trigger: 'blur' }],
  planId: [{ required: true, message: '计划ID不能为空', trigger: 'blur' }],
  triggerType: [{ required: true, message: '触发类型不能为空', trigger: 'change' }]
})

const openCreateDialog = () => {
  createForm.patientId = Number(queryParams.patientId) || undefined
  createForm.episodeId = Number(queryParams.episodeId) || undefined
  createForm.planId = Number(queryParams.planId) || undefined
  createForm.triggerType = 'time_due'
  createForm.triggerLevel = 'medium'
  createForm.triggerMessage = ''
  createForm.suggestedAction = ''
  createForm.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DD')
  createDialog.visible = true
}

const submitCreate = async () => {
  await createFormRef.value.validate()
  createDialog.loading = true
  try {
    await createRehabTrigger({
      patientId: createForm.patientId,
      episodeId: createForm.episodeId,
      planId: createForm.planId,
      triggerType: createForm.triggerType,
      triggerLevel: createForm.triggerLevel,
      triggerMessage: createForm.triggerMessage,
      suggestedAction: createForm.suggestedAction,
      dueDate: createForm.dueDate
    })
    message.success('触发创建成功')
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
