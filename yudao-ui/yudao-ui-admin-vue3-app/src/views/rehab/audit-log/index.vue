<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="模块">
        <el-input v-model="queryParams.moduleType" placeholder="report / plan / alert" class="!w-180px" />
      </el-form-item>
      <el-form-item label="操作">
        <el-input v-model="queryParams.operationType" placeholder="report_lock 等" class="!w-180px" />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="queryParams.resultStatus" clearable class="!w-120px">
          <el-option label="success" value="success" />
          <el-option label="failed" value="failed" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="模块" prop="moduleType" width="110" />
      <el-table-column label="模块ID" prop="moduleId" width="100" />
      <el-table-column label="操作类型" prop="operationType" min-width="180" />
      <el-table-column label="操作人" min-width="140">
        <template #default="scope">{{ scope.row.operatorName || scope.row.operatorUserId }}</template>
      </el-table-column>
      <el-table-column label="结果" prop="resultStatus" width="90" />
      <el-table-column label="备注" prop="remark" min-width="220" show-overflow-tooltip />
      <el-table-column label="时间" prop="createTime" min-width="170" :formatter="dateFormatter" />
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
import { getRehabAuditLogPage } from '@/api/rehab/audit-log'

defineOptions({ name: 'RehabAuditLog' })

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  moduleType: undefined as string | undefined,
  operationType: undefined as string | undefined,
  resultStatus: undefined as string | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabAuditLogPage(queryParams)
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

onMounted(() => {
  getList()
})
</script>
