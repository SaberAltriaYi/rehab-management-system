<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="提醒类型">
        <el-select v-model="queryParams.alertType" clearable class="!w-160px">
          <el-option label="reassessment_due" value="reassessment_due" />
          <el-option label="low_adherence" value="low_adherence" />
          <el-option label="pain_upgrade" value="pain_upgrade" />
          <el-option label="plan_due" value="plan_due" />
          <el-option label="report_ready" value="report_ready" />
          <el-option label="high_risk_unresolved" value="high_risk_unresolved" />
        </el-select>
      </el-form-item>
      <el-form-item label="级别">
        <el-select v-model="queryParams.severity" clearable class="!w-120px">
          <el-option label="info" value="info" />
          <el-option label="warning" value="warning" />
          <el-option label="high" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable class="!w-140px">
          <el-option label="active" value="active" />
          <el-option label="acknowledged" value="acknowledged" />
          <el-option label="resolved" value="resolved" />
          <el-option label="ignored" value="ignored" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="handleRefresh">刷新提醒</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="患者" min-width="160">
        <template #default="scope">{{ scope.row.patientName || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" prop="alertType" min-width="150" />
      <el-table-column label="级别" prop="severity" width="90" />
      <el-table-column label="状态" prop="status" width="120" />
      <el-table-column label="触发说明" prop="triggerMessage" min-width="260" show-overflow-tooltip />
      <el-table-column label="时间" prop="createTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button
            type="primary"
            link
            v-if="scope.row.status === 'active'"
            v-hasPermi="['rehab:alert:acknowledge']"
            @click="handleAck(scope.row)"
          >
            确认
          </el-button>
          <el-button
            type="success"
            link
            v-if="scope.row.status === 'active' || scope.row.status === 'acknowledged'"
            v-hasPermi="['rehab:alert:resolve']"
            @click="handleResolve(scope.row)"
          >
            解决
          </el-button>
          <el-button
            type="warning"
            link
            v-if="scope.row.status === 'active' || scope.row.status === 'acknowledged'"
            v-hasPermi="['rehab:alert:ignore']"
            @click="handleIgnore(scope.row)"
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
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import {
  acknowledgeRehabAlert,
  getRehabAlertPage,
  ignoreRehabAlert,
  refreshRehabAlert,
  resolveRehabAlert
} from '@/api/rehab/alert'

defineOptions({ name: 'RehabAlert' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  alertType: undefined as string | undefined,
  severity: undefined as string | undefined,
  status: 'active'
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabAlertPage(queryParams)
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

const handleRefresh = async () => {
  await refreshRehabAlert({})
  message.success('提醒已刷新')
  await getList()
}

const handleAck = async (row: any) => {
  await acknowledgeRehabAlert({ id: row.id })
  await getList()
}

const handleResolve = async (row: any) => {
  await resolveRehabAlert({ id: row.id })
  await getList()
}

const handleIgnore = async (row: any) => {
  await ignoreRehabAlert({ id: row.id })
  await getList()
}

onMounted(() => {
  getList()
})
</script>
