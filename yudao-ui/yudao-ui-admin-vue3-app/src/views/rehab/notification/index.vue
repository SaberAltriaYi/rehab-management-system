<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="-mb-15px">
      <el-form-item label="类型">
        <el-select v-model="queryParams.notificationType" clearable class="!w-160px">
          <el-option label="task_reminder" value="task_reminder" />
          <el-option label="reassessment_due" value="reassessment_due" />
          <el-option label="low_adherence" value="low_adherence" />
          <el-option label="pain_alert" value="pain_alert" />
          <el-option label="report_ready" value="report_ready" />
          <el-option label="plan_updated" value="plan_updated" />
          <el-option label="trigger_created" value="trigger_created" />
          <el-option label="system_notice" value="system_notice" />
        </el-select>
      </el-form-item>
      <el-form-item label="已读">
        <el-select v-model="queryParams.readStatus" clearable class="!w-120px">
          <el-option label="unread" value="unread" />
          <el-option label="read" value="read" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="handleReadAll" v-hasPermi="['rehab:notification:read']">全部已读</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="标题" prop="title" min-width="180" />
      <el-table-column label="内容" prop="content" min-width="280" show-overflow-tooltip />
      <el-table-column label="患者" min-width="160">
        <template #default="scope">{{ scope.row.patientName || '-' }}</template>
      </el-table-column>
      <el-table-column label="类型" prop="notificationType" min-width="140" />
      <el-table-column label="级别" prop="severity" width="90" />
      <el-table-column label="状态" prop="readStatus" width="90" />
      <el-table-column label="时间" prop="createTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="scope">
          <el-button
            type="primary"
            link
            v-if="scope.row.readStatus !== 'read'"
            v-hasPermi="['rehab:notification:read']"
            @click="handleRead(scope.row)"
          >
            已读
          </el-button>
          <el-button
            type="danger"
            link
            v-hasPermi="['rehab:notification:send']"
            @click="handleDelete(scope.row)"
          >
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
import { dateFormatter } from '@/utils/formatTime'
import {
  deleteRehabNotification,
  getRehabNotificationPage,
  readAllRehabNotification,
  readRehabNotification
} from '@/api/rehab/notification'

defineOptions({ name: 'RehabNotification' })

const message = useMessage()
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  notificationType: undefined as string | undefined,
  readStatus: undefined as string | undefined,
  onlyMine: true
})

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabNotificationPage(queryParams)
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

const handleRead = async (row: any) => {
  await readRehabNotification(row.id)
  await getList()
}

const handleReadAll = async () => {
  await readAllRehabNotification()
  message.success('已全部标记已读')
  await getList()
}

const handleDelete = async (row: any) => {
  await message.delConfirm('确认删除该通知？')
  await deleteRehabNotification(row.id)
  message.success('删除成功')
  await getList()
}

onMounted(() => {
  getList()
})
</script>
