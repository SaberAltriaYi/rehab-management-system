<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="90px">
      <el-form-item label="患者ID" prop="patientId">
        <el-input-number v-model="queryParams.patientId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="episode" prop="episodeId">
        <el-input-number v-model="queryParams.episodeId" :min="1" controls-position="right" class="!w-140px" />
      </el-form-item>
      <el-form-item label="治疗师" prop="primaryTherapistUserId">
        <el-select v-model="queryParams.primaryTherapistUserId" clearable filterable class="!w-180px">
          <el-option v-for="u in userOptions" :key="u.id" :label="`${u.nickname}(${u.id})`" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-130px">
          <el-option label="draft" value="draft" />
          <el-option label="active" value="active" />
          <el-option label="paused" value="paused" />
          <el-option label="completed" value="completed" />
          <el-option label="closed" value="closed" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="planType">
        <el-select v-model="queryParams.planType" clearable class="!w-150px">
          <el-option label="rehab" value="rehab" />
          <el-option label="maintenance" value="maintenance" />
          <el-option label="return_to_sport" value="return_to_sport" />
          <el-option label="home_program" value="home_program" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:plan:create']" @click="handleCreate">
          <Icon icon="ep:plus" /> 新建计划
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="计划编号" prop="planNo" min-width="150" />
      <el-table-column label="患者" min-width="170">
        <template #default="scope">{{ scope.row.patientName }} ({{ scope.row.patientNo }})</template>
      </el-table-column>
      <el-table-column label="疗程编号" prop="episodeNo" min-width="140" />
      <el-table-column label="计划名称" prop="planName" min-width="180" />
      <el-table-column label="类型" prop="planType" min-width="120" />
      <el-table-column label="状态" prop="status" min-width="100" />
      <el-table-column label="起止" min-width="220">
        <template #default="scope">
          {{ formatRehabDate(scope.row.startDate) }} ~ {{ formatRehabDate(scope.row.endDate) }}
        </template>
      </el-table-column>
      <el-table-column label="主责治疗师" prop="primaryTherapistName" min-width="140" />
      <el-table-column label="最近进度" prop="latestProgressSummary" min-width="260" show-overflow-tooltip />
      <el-table-column label="操作" min-width="320" fixed="right">
        <template #default="scope">
          <el-button type="primary" link v-hasPermi="['rehab:plan:detail']" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:plan:update']" @click="openEdit(scope.row.id)">
            编辑
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:plan:activate']" @click="changeStatus('activate', scope.row)">
            激活
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:plan:pause']" @click="changeStatus('pause', scope.row)">
            暂停
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:plan:complete']" @click="changeStatus('complete', scope.row)">
            完成
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:plan:copy']" @click="handleCopy(scope.row)">
            复制
          </el-button>
          <el-button type="danger" link v-hasPermi="['rehab:plan:update']" @click="handleDelete(scope.row)">
            删除
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
</template>

<script lang="ts" setup>
import * as UserApi from '@/api/system/user'
import { formatDate } from '@/utils/formatTime'
import {
  activateRehabPlan,
  completeRehabPlan,
  copyRehabPlan,
  deleteRehabPlan,
  getRehabPlanPage,
  pauseRehabPlan,
  RehabCarePlanPageReqVO
} from '@/api/rehab/plan'

defineOptions({ name: 'RehabPlan' })

const { push } = useRouter()
const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const userOptions = ref<UserApi.UserVO[]>([])

const queryParams = reactive<RehabCarePlanPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  episodeId: undefined,
  primaryTherapistUserId: undefined,
  status: undefined,
  planType: undefined
})

const queryFormRef = ref()

const formatRehabDate = (value: any) => (value ? formatDate(value, 'YYYY-MM-DD') : '-')

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabPlanPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  userOptions.value = await UserApi.getSimpleUserList()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handleCreate = () => {
  push('/rehab/plan/create')
}

const openEdit = (id: number) => {
  push(`/rehab/plan/edit/${id}`)
}

const openDetail = (id: number) => {
  push(`/rehab/plan/detail/${id}`)
}

const changeStatus = async (action: 'activate' | 'pause' | 'complete', row: any) => {
  await message.confirm(`确认${action}计划 ${row.planNo} 吗？`)
  if (action === 'activate') await activateRehabPlan({ id: row.id })
  if (action === 'pause') await pauseRehabPlan({ id: row.id })
  if (action === 'complete') await completeRehabPlan({ id: row.id })
  message.success('操作成功')
  await getList()
}

const handleCopy = async (row: any) => {
  await message.confirm(`确认复制计划 ${row.planNo} 吗？`)
  await copyRehabPlan({ id: row.id })
  message.success('复制成功')
  await getList()
}

const handleDelete = async (row: any) => {
  await message.confirm(`确认删除计划 ${row.planNo} 吗？`)
  await deleteRehabPlan(row.id)
  message.success('删除成功')
  await getList()
}

onMounted(async () => {
  await Promise.all([loadUsers(), getList()])
})
</script>
