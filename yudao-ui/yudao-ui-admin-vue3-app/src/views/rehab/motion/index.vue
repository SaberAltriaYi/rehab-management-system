<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="智能动作评估：OpenCap 负责运动学，规则引擎负责质控与评分，AI 只做解释与草稿，最终分数与结论由治疗师确认并签署。"
      class="mb-12px"
    />
    <el-form :model="query" inline>
      <el-form-item label="患者 ID">
        <el-input-number v-model="query.patientId" :min="1" controls-position="right" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable style="width: 160px">
          <el-option v-for="(v, k) in STATE_LABELS" :key="k" :label="v" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.visitType" clearable style="width: 120px">
          <el-option v-for="v in VISIT_OPTIONS" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:motion:create']" @click="formRef.open({ patientId: query.patientId })">
          新建动作评估
        </el-button>
        <el-button v-if="query.patientId" @click="trendVisible = true">患者趋势</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="编号" prop="id" width="80" />
      <el-table-column label="患者" min-width="140">
        <template #default="{ row }">{{ row.patientName || '-' }}<span class="muted"> {{ row.patientNo }}</span></template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ visitLabel(row.visitType) }}</template>
      </el-table-column>
      <el-table-column label="评估体系" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="f in row.protocolFamilies || []" :key="f" size="small" class="mr-4px">{{ f }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采集时间" width="160">
        <template #default="{ row }">{{ row.captureTime ? formatDate(row.captureTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="(STATE_TAG[row.status] as any) || 'info'">{{ row.statusLabel || row.status }}</el-tag>
          <el-tag v-if="row.stale" type="warning" size="small" class="ml-4px">需重算</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质控" width="90">
        <template #default="{ row }">{{ QC_LABEL[row.sessionQualityStatus] || '-' }}</template>
      </el-table-column>
      <el-table-column label="FMS 总分" width="100">
        <template #default="{ row }">
          <span v-if="row.fmsTotal !== null && row.fmsTotal !== undefined">{{ row.fmsTotal }} / 21</span>
          <el-tooltip v-else content="7 项均经治疗师审核后才显示总分"><span class="muted">未完成</span></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="签署" width="160">
        <template #default="{ row }">{{ row.signedTime ? formatDate(row.signedTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            link
            type="primary"
            v-hasPermi="['rehab:motion:create']"
            :disabled="row.status === 'COMPLETED'"
            @click="formRef.open({ id: row.id })"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            v-hasPermi="['rehab:motion:create']"
            :disabled="row.status === 'COMPLETED'"
            @click="remove(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="load" />
  </ContentWrap>

  <MotionForm ref="formRef" @success="onCreated" />
  <el-drawer v-model="trendVisible" title="患者动作评估趋势" size="60%">
    <TrendPanel v-if="trendVisible && query.patientId" :patient-id="query.patientId" />
  </el-drawer>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import MotionForm from './MotionForm.vue'
import TrendPanel from './components/TrendPanel.vue'
import { QC_LABEL, STATE_TAG, VISIT_OPTIONS, visitLabel } from './constants'

defineOptions({ name: 'RehabMotion' })

const STATE_LABELS: Record<string, string> = {
  WAITING_UPLOAD: '待上传',
  UPLOADING: '上传中',
  OPENCAP_PROCESSING: 'OpenCap处理中',
  DOWNLOADING: '下载结果',
  PARSING: '数据解析',
  RULES: '规则计算',
  AI_GENERATING: 'AI报告生成',
  PENDING_REVIEW: '待治疗师审核',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

const message = useMessage()
const route = useRoute()
const { push } = useRouter()
const formRef = ref()
const loading = ref(false)
const list = ref<MotionAssessmentVO[]>([])
const total = ref(0)
const trendVisible = ref(false)
const query = reactive<{ pageNo: number; pageSize: number; patientId?: number; status?: string; visitType?: string }>({
  pageNo: 1,
  pageSize: 10,
  patientId: route.query.patientId ? Number(route.query.patientId) : undefined
})

const load = async () => {
  loading.value = true
  try {
    const page = await MotionApi.page({ ...query })
    list.value = page?.list || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  query.patientId = undefined
  query.status = undefined
  query.visitType = undefined
  search()
}
const openDetail = (id: number) => push(`/rehab/motion/detail/${id}`)
const onCreated = (id: number) => {
  load()
  if (id) openDetail(id)
}
const remove = async (id: number) => {
  await message.delConfirm('删除后已上传的数据与计算结果将一并删除，确认删除？')
  await MotionApi.remove(id)
  message.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
