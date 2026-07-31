<template>
  <ContentWrap class="board-head-wrap">
    <div class="board-head">
      <div>
        <div class="board-title">康复工作台</div>
        <div class="board-subtitle">覆盖患者、评估、报告、计划、打卡、风险、通知全链路数据</div>
      </div>
      <div class="board-actions">
        <el-button :loading="loading" type="primary" @click="loadAll">刷新数据</el-button>
        <span class="update-time">更新时间：{{ updateTime || '--' }}</span>
      </div>
    </div>
  </ContentWrap>

  <ContentWrap>
    <div class="section-title">我的工作台</div>
    <el-row :gutter="12">
      <el-col v-for="item in therapistCards" :key="item.label" :xs="12" :sm="8" :md="6" :lg="3">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap v-if="hasOpsData">
    <div class="section-title">机构全量总览（管理员）</div>
    <el-row :gutter="12">
      <el-col v-for="item in opsCards" :key="item.label" :xs="12" :sm="8" :md="6" :lg="4">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="12">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" header="最近待处理提醒">
          <el-table :data="recent.recentAlerts || []" size="small" height="300">
            <el-table-column label="患者" min-width="120">
              <template #default="scope">
                {{ scope.row.patientName || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="类型" min-width="120">
              <template #default="scope">
                {{ getAlertTypeLabel(scope.row.alertType) }}
              </template>
            </el-table-column>
            <el-table-column label="级别" width="80">
              <template #default="scope">
                {{ getSeverityLabel(scope.row.severity) }}
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="scope">
                {{ formatDateTime(scope.row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" header="风险类型分布">
          <el-table :data="riskDistribution" size="small" height="300">
            <el-table-column label="风险类型" min-width="180">
              <template #default="scope">
                {{ getAlertTypeLabel(scope.row.alertType) }}
              </template>
            </el-table-column>
            <el-table-column label="数量" prop="count" width="120" />
            <el-table-column label="占比" min-width="120">
              <template #default="scope">
                {{ getRiskPercent(scope.row.count) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="12">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" header="最近生成报告">
          <el-table :data="recent.recentReports || []" size="small" height="260">
            <el-table-column label="报告编号" prop="reportNo" min-width="150" />
            <el-table-column label="患者" prop="patientName" min-width="120" />
            <el-table-column label="状态" width="100">
              <template #default="scope">
                {{ getReportStatusLabel(scope.row.reportStatus) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" header="最近打卡异常">
          <el-table :data="recent.abnormalCheckins || []" size="small" height="260">
            <el-table-column label="患者" prop="patientName" min-width="120" />
            <el-table-column label="日期" min-width="120">
              <template #default="scope">
                {{ formatDate(scope.row.checkinDate) }}
              </template>
            </el-table-column>
            <el-table-column label="异常原因" prop="reason" min-width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" header="治疗师负载排行（管理员）">
          <el-table :data="workload || []" size="small" height="260">
            <el-table-column label="治疗师" prop="therapistName" min-width="120" />
            <el-table-column label="患者数" prop="patientCount" width="90" />
            <el-table-column label="执行中计划" prop="activePlanCount" width="120" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import {
  getRehabDashboardRecentItems,
  getRehabDashboardSummary,
  getRehabOpsDashboardRiskOverview,
  getRehabOpsDashboardSummary,
  getRehabOpsDashboardWorkload
} from '@/api/rehab/workspace'

defineOptions({ name: 'RehabDashboard' })

const loading = ref(false)
const updateTime = ref('')

const summary = ref<any>({})
const recent = ref<any>({})
const opsSummary = ref<any>({})
const workload = ref<any[]>([])
const opsRiskOverview = ref<any[]>([])
const hasOpsData = ref(false)

const ALERT_TYPE_LABELS: Record<string, string> = {
  reassessment_due: '待复评',
  low_adherence: '依从性偏低',
  pain_upgrade: '疼痛升高',
  plan_due: '计划到期',
  high_risk_unresolved: '高风险未处理',
  abnormal_checkin: '打卡异常'
}

const SEVERITY_LABELS: Record<string, string> = {
  info: '提示',
  warning: '警告',
  high: '高风险',
  critical: '紧急'
}

const REPORT_STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  reviewed: '已复核',
  approved: '已批准',
  locked: '已锁定'
}

const normalizeDateValue = (value: any) => {
  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value
    return dayjs(new Date(year, month - 1, day, hour, minute, second))
  }
  return dayjs(value)
}

const formatDateTime = (value: any) => {
  if (value == null || value === '') return '-'
  const date = normalizeDateValue(value)
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm') : '-'
}

const formatDate = (value: any) => {
  if (value == null || value === '') return '-'
  const date = normalizeDateValue(value)
  return date.isValid() ? date.format('YYYY-MM-DD') : '-'
}

const getAlertTypeLabel = (value?: string) => ALERT_TYPE_LABELS[value || ''] || value || '-'
const getSeverityLabel = (value?: string) => SEVERITY_LABELS[value || ''] || value || '-'
const getReportStatusLabel = (value?: string) =>
  REPORT_STATUS_LABELS[value || ''] || value || '-'

const therapistCards = computed(() => [
  { label: '我的患者', value: summary.value.myPatientCount ?? 0 },
  { label: '执行中计划', value: summary.value.activePlanCount ?? 0 },
  { label: '待复评', value: summary.value.pendingReassessmentCount ?? 0 },
  { label: '高风险', value: summary.value.highRiskPatientCount ?? 0 },
  { label: '低依从', value: summary.value.lowAdherencePatientCount ?? 0 },
  { label: '本周新增评估', value: summary.value.weeklyNewAssessmentCount ?? 0 },
  { label: '未读通知', value: summary.value.unreadNotificationCount ?? 0 }
])

const opsCards = computed(() => [
  { label: '患者总量', value: opsSummary.value.patientTotal ?? 0 },
  { label: '活跃患者', value: opsSummary.value.activePatientTotal ?? 0 },
  { label: '活跃计划', value: opsSummary.value.activePlanTotal ?? 0 },
  { label: '待复评', value: opsSummary.value.pendingReassessmentTotal ?? 0 },
  { label: '高风险患者', value: opsSummary.value.highRiskTotal ?? 0 },
  { label: '报告生成总数', value: opsSummary.value.reportGeneratedTotal ?? 0 },
  { label: '报告导出总数', value: opsSummary.value.reportExportedTotal ?? 0 },
  { label: '低依从患者', value: opsSummary.value.lowAdherenceTotal ?? 0 },
  {
    label: '平均打卡完成率',
    value: `${opsSummary.value.avgCheckinCompletionRate ?? 0}%`
  }
])

const riskDistribution = computed(() => {
  if (opsRiskOverview.value.length > 0) {
    return opsRiskOverview.value
  }
  const grouped: Record<string, number> = {}
  ;(recent.value.recentAlerts || []).forEach((item) => {
    const key = item.alertType || '未知'
    grouped[key] = (grouped[key] || 0) + 1
  })
  return Object.entries(grouped).map(([alertType, count]) => ({ alertType, count }))
})

const getRiskPercent = (count: number) => {
  const total = riskDistribution.value.reduce((sum, item) => sum + Number(item.count || 0), 0)
  if (!total) {
    return '0%'
  }
  return `${((Number(count || 0) / total) * 100).toFixed(1)}%`
}

const loadOps = async () => {
  try {
    const [summaryResp, workloadResp, riskResp] = await Promise.all([
      getRehabOpsDashboardSummary(),
      getRehabOpsDashboardWorkload(),
      getRehabOpsDashboardRiskOverview()
    ])
    opsSummary.value = summaryResp || {}
    workload.value = workloadResp || []
    opsRiskOverview.value = riskResp || []
    hasOpsData.value = true
  } catch (error) {
    hasOpsData.value = false
    opsSummary.value = {}
    workload.value = []
    opsRiskOverview.value = []
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    const [summaryResp, recentResp] = await Promise.all([
      getRehabDashboardSummary(),
      getRehabDashboardRecentItems()
    ])
    summary.value = summaryResp || {}
    recent.value = recentResp || {}
    await loadOps()
    updateTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  } catch (error) {
    ElMessage.error('加载康复工作台数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.board-head-wrap :deep(.el-card__body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

.board-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.board-title {
  font-size: 22px;
  font-weight: 700;
}

.board-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.board-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.update-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
}

.metric-card {
  margin-bottom: 12px;
}

.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

@media screen and (max-width: 768px) {
  .board-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
