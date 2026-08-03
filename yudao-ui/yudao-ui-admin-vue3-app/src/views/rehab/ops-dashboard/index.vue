<template>
  <ContentWrap>
    <el-row :gutter="12">
      <el-col v-for="item in cards" :key="item.label" :xs="12" :sm="8" :md="6">
        <el-card shadow="hover" class="mb-12px">
          <div class="card-content">
            <div class="card-icon" :style="{ backgroundColor: item.background, color: item.color }">
              <Icon :icon="item.icon" />
            </div>
            <div>
              <div class="card-label">{{ item.label }}</div>
              <div class="card-value">{{ item.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="12">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" header="治疗师负载排行">
          <el-table :data="workload" size="small">
            <el-table-column label="治疗师" prop="therapistName" min-width="140" />
            <el-table-column label="患者数" prop="patientCount" width="100" />
            <el-table-column label="执行中计划" prop="activePlanCount" width="120" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" header="风险类型分布">
          <el-table :data="riskOverview" size="small">
            <el-table-column label="提醒类型" prop="alertType" min-width="200" />
            <el-table-column label="数量" prop="count" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>
</template>

<script lang="ts" setup>
import {
  getRehabOpsDashboardRiskOverview,
  getRehabOpsDashboardSummary,
  getRehabOpsDashboardWorkload
} from '@/api/rehab/workspace'

defineOptions({ name: 'RehabOpsDashboard' })

const summary = ref<any>({})
const workload = ref<any[]>([])
const riskOverview = ref<any[]>([])

const cards = computed(() => [
  { label: '患者总量', value: summary.value.patientTotal ?? 0, icon: 'ep:user-filled', color: '#409eff', background: '#ecf5ff' },
  { label: '活跃患者', value: summary.value.activePatientTotal ?? 0, icon: 'ep:circle-check-filled', color: '#67c23a', background: '#f0f9eb' },
  { label: '活跃计划', value: summary.value.activePlanTotal ?? 0, icon: 'ep:list', color: '#16a085', background: '#eafaf6' },
  { label: '本周新增评估', value: summary.value.weeklyNewAssessmentTotal ?? 0, icon: 'ep:document-add', color: '#626aef', background: '#f0f1ff' },
  { label: '待复评', value: summary.value.pendingReassessmentTotal ?? 0, icon: 'ep:refresh', color: '#e6a23c', background: '#fdf6ec' },
  { label: '高风险', value: summary.value.highRiskTotal ?? 0, icon: 'ep:warning-filled', color: '#f56c6c', background: '#fef0f0' },
  { label: '报告生成总数', value: summary.value.reportGeneratedTotal ?? 0, icon: 'ep:document', color: '#626aef', background: '#f0f1ff' },
  { label: '报告导出总数', value: summary.value.reportExportedTotal ?? 0, icon: 'ep:download', color: '#9b59b6', background: '#f8f0fb' },
  { label: '低依从', value: summary.value.lowAdherenceTotal ?? 0, icon: 'ep:trend-charts', color: '#909399', background: '#f4f4f5' },
  { label: '平均打卡完成率', value: `${summary.value.avgCheckinCompletionRate ?? 0}%`, icon: 'ep:data-line', color: '#00a8a8', background: '#e9fafa' }
])

const load = async () => {
  summary.value = await getRehabOpsDashboardSummary()
  workload.value = await getRehabOpsDashboardWorkload()
  riskOverview.value = await getRehabOpsDashboardRiskOverview()
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.card-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.card-icon {
  display: flex;
  flex: 0 0 46px;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  font-size: 24px;
}

.card-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
}
</style>
