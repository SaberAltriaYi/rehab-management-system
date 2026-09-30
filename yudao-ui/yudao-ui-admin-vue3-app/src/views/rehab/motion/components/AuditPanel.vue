<template>
  <div>
    <h4>人工修改留痕（{{ edits.length }}）</h4>
    <el-table :data="edits" size="small" max-height="360">
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ row.createTime ? formatDate(row.createTime) : '' }}</template>
      </el-table-column>
      <el-table-column label="对象" width="160">
        <template #default="{ row }">{{ TARGET[row.targetType] || row.targetType }} {{ row.targetKey || '#' + row.targetId }}</template>
      </el-table-column>
      <el-table-column label="字段" prop="fieldName" width="130" />
      <el-table-column label="原值" prop="oldValue" min-width="140" show-overflow-tooltip />
      <el-table-column label="新值" prop="newValue" min-width="140" show-overflow-tooltip />
      <el-table-column label="原因" prop="reason" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作人" prop="operatorUserId" width="80" />
    </el-table>

    <div v-hasPermi="['rehab:motion:audit']">
      <h4>审计日志（{{ logs.length }}）</h4>
      <el-table :data="logs" size="small" max-height="420">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ row.createTime ? formatDate(row.createTime) : '' }}</template>
        </el-table-column>
        <el-table-column label="操作" prop="operationType" width="170" />
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.operatorUserId }} <span class="muted">{{ row.operatorRole }}</span></template>
        </el-table-column>
        <el-table-column label="结果" prop="resultStatus" width="80" />
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="变更后" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.afterDataJson || '' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { MotionApi, MotionAssessmentVO } from '@/api/rehab/motion'
import { checkPermi } from '@/utils/permission'
import { formatDate } from '@/utils/formatTime'

const props = defineProps<{ assessment: MotionAssessmentVO }>()
const TARGET: Record<string, string> = { trial: 'Trial', score: '评分', assessment: '评估', manual_inputs: '人工录入', ai_draft: 'AI 草稿' }
const edits = ref<any[]>([])
const logs = ref<any[]>([])

onMounted(async () => {
  edits.value = (await MotionApi.manualEdits(props.assessment.id)) || []
  if (checkPermi(['rehab:motion:audit'])) logs.value = ((await MotionApi.auditLogs(props.assessment.id)) as any[]) || []
})
</script>

<style scoped>
h4 {
  margin: 16px 0 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
