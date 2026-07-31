<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <div class="text-16px font-bold">计划详情</div>
      <div>
        <el-button @click="goBack">返回</el-button>
        <el-button
          v-if="AI_ENABLED"
          type="primary"
          v-hasPermi="['rehab:ai:generate']"
          @click="handleGenerateAiPlanDraft"
        >
          AI 计划草案
        </el-button>
        <el-button type="primary" v-hasPermi="['rehab:plan:update']" @click="openEdit">编辑</el-button>
        <el-button type="primary" v-hasPermi="['rehab:plan:activate']" @click="changePlanStatus('activate')">激活</el-button>
        <el-button type="warning" v-hasPermi="['rehab:plan:pause']" @click="changePlanStatus('pause')">暂停</el-button>
        <el-button type="success" v-hasPermi="['rehab:plan:complete']" @click="changePlanStatus('complete')">完成</el-button>
      </div>
    </div>

    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-row :gutter="12" class="mb-12px">
          <el-col :span="16">
            <el-card shadow="never">
              <template #header>计划基础信息</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="plan_no">{{ detail?.planNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail?.status || '-' }}</el-descriptions-item>
                <el-descriptions-item label="患者">{{ detail?.patientName || '-' }} ({{ detail?.patientNo || '-' }})</el-descriptions-item>
                <el-descriptions-item label="episode">{{ detail?.episodeNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="来源评估">{{ detail?.assessmentNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="治疗师">{{ detail?.primaryTherapistName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="计划类型">{{ detail?.planType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="强度">{{ detail?.intensityLevel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="起止日期">{{ detail?.startDate || '-' }} ~ {{ detail?.endDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="周期">{{ detail?.cycleDays || '-' }} 天</el-descriptions-item>
                <el-descriptions-item label="复评周期">{{ detail?.reviewCycleDays || '-' }} 天</el-descriptions-item>
                <el-descriptions-item label="最近进度">{{ detail?.latestProgressSummary || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="mb-12px">
              <template #header>目标</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="短期">{{ detail?.shortTermGoalsJson || '-' }}</el-descriptions-item>
                <el-descriptions-item label="中期">{{ detail?.midTermGoalsJson || '-' }}</el-descriptions-item>
                <el-descriptions-item label="长期">{{ detail?.longTermGoalsJson || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
            <el-card shadow="never">
              <template #header>注意事项</template>
              <div class="mb-8px"><b>禁忌：</b>{{ detail?.contraindications || '-' }}</div>
              <div class="mb-8px"><b>注意：</b>{{ detail?.precautions || '-' }}</div>
              <div><b>备注：</b>{{ detail?.note || '-' }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card v-if="AI_ENABLED" shadow="never" class="mb-12px">
          <template #header>
            <div class="flex items-center justify-between">
              <span>AI 计划草案</span>
              <div>
                <el-button type="primary" link v-hasPermi="['rehab:ai:generate']" @click="handleGenerateAiPlanDraft">
                  重生成
                </el-button>
                <el-button type="primary" link v-if="aiPlanDraft" @click="aiPlanDialog = true">查看详情</el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="2" border class="mb-10px">
            <el-descriptions-item label="输出ID">{{ aiPlanDraft?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核状态">{{ aiPlanDraft?.reviewStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安全状态">{{ aiPlanDraft?.safetyStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="输出时间">{{ aiPlanDraft?.createTime || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="ai-rendered">{{ aiPlanDraft?.renderedText || '暂无 AI 草案，请点击“AI 计划草案”生成。' }}</div>
        </el-card>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="任务列表" name="task">
            <div class="mb-12px flex items-center gap-8px">
              <el-button type="primary" v-hasPermi="['rehab:task:create']" @click="openTaskDialog('create')">新增任务</el-button>
              <el-button v-hasPermi="['rehab:task:sort']" @click="saveTaskSort">保存排序</el-button>
            </div>
            <el-table :data="taskList" stripe>
              <el-table-column label="排序" min-width="100">
                <template #default="scope">
                  <el-input-number
                    v-model="scope.row.sortOrder"
                    :min="1"
                    :max="999"
                    controls-position="right"
                    size="small"
                    class="!w-90px"
                  />
                </template>
              </el-table-column>
              <el-table-column label="task_no" prop="taskNo" min-width="150" />
              <el-table-column label="任务名称" prop="taskName" min-width="160" />
              <el-table-column label="模块" prop="moduleType" min-width="110" />
              <el-table-column label="执行类型" prop="executionType" min-width="100" />
              <el-table-column label="剂量" prop="dosageText" min-width="140" />
              <el-table-column label="周频次" prop="frequencyPerWeek" min-width="90" />
              <el-table-column label="状态" prop="status" min-width="90" />
              <el-table-column label="操作" min-width="220" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link v-hasPermi="['rehab:task:update']" @click="openTaskDialog('update', scope.row)">
                    编辑
                  </el-button>
                  <el-button
                    v-if="scope.row.status !== 'disabled'"
                    type="warning"
                    link
                    v-hasPermi="['rehab:task:disable']"
                    @click="toggleTask(scope.row, 'disable')"
                  >
                    停用
                  </el-button>
                  <el-button
                    v-else
                    type="success"
                    link
                    v-hasPermi="['rehab:task:update']"
                    @click="toggleTask(scope.row, 'enable')"
                  >
                    启用
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="打卡记录" name="checkin">
            <div class="mb-12px flex items-center gap-8px">
              <el-button type="primary" v-hasPermi="['rehab:checkin:create-manual']" @click="openCheckinDialog">新增代录打卡</el-button>
              <el-button @click="goCheckinCenter">前往打卡中心</el-button>
            </div>
            <el-table :data="checkinList" stripe>
              <el-table-column label="日期" prop="checkinDate" min-width="110" />
              <el-table-column label="角色" prop="submitRoleType" min-width="100" />
              <el-table-column label="提交人" prop="submitterName" min-width="120" />
              <el-table-column label="完成率" prop="overallCompletionRate" min-width="90">
                <template #default="scope">{{ scope.row.overallCompletionRate ?? '-' }}%</template>
              </el-table-column>
              <el-table-column label="疼痛(前/后)" min-width="110">
                <template #default="scope">{{ scope.row.painScoreBefore ?? '-' }}/{{ scope.row.painScoreAfter ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="备注" prop="overallComment" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" min-width="120" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link @click="showExecutions(scope.row)">执行明细</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="进度追踪" name="progress">
            <div class="mb-12px flex items-center gap-8px">
              <el-button type="primary" v-hasPermi="['rehab:progress:detail']" @click="recalculateProgress">重算进度</el-button>
            </div>
            <el-table :data="progressList" stripe>
              <el-table-column label="周期" min-width="180">
                <template #default="scope">{{ scope.row.periodStart }} ~ {{ scope.row.periodEnd }}</template>
              </el-table-column>
              <el-table-column label="计划任务数" prop="plannedTaskCount" min-width="100" />
              <el-table-column label="完成任务数" prop="completedTaskCount" min-width="100" />
              <el-table-column label="完成率" prop="completionRate" min-width="90">
                <template #default="scope">{{ scope.row.completionRate ?? '-' }}%</template>
              </el-table-column>
              <el-table-column label="依从性" prop="adherenceScore" min-width="90">
                <template #default="scope">{{ scope.row.adherenceScore ?? '-' }}%</template>
              </el-table-column>
              <el-table-column label="疼痛趋势" prop="painTrend" min-width="100" />
              <el-table-column label="状态" prop="progressStatus" min-width="100" />
              <el-table-column label="建议" prop="recommendedAction" min-width="240" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="复评触发" name="trigger">
            <div class="mb-12px flex items-center gap-8px">
              <el-button type="primary" v-hasPermi="['rehab:reassessment-trigger:create']" @click="openTriggerDialog">手动创建触发</el-button>
              <el-button @click="goTriggerCenter">前往触发中心</el-button>
            </div>
            <el-table :data="triggerList" stripe>
              <el-table-column label="类型" prop="triggerType" min-width="120" />
              <el-table-column label="等级" prop="triggerLevel" min-width="90" />
              <el-table-column label="状态" prop="triggerStatus" min-width="120" />
              <el-table-column label="触发说明" prop="triggerMessage" min-width="220" show-overflow-tooltip />
              <el-table-column label="建议动作" prop="suggestedAction" min-width="220" show-overflow-tooltip />
              <el-table-column label="到期" prop="dueDate" min-width="110" />
              <el-table-column label="操作" min-width="250" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'ack')">确认</el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'convert')">转复评</el-button>
                  <el-button type="danger" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'dismiss')">忽略</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="操作日志" name="log">
            <el-table :data="operationLogs" stripe>
              <el-table-column label="时间" prop="createTime" min-width="170" />
              <el-table-column label="操作类型" prop="operationType" min-width="130" />
              <el-table-column label="操作人" prop="operatorName" min-width="120" />
              <el-table-column label="备注" prop="remark" min-width="260" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-skeleton>
  </ContentWrap>

  <Dialog v-model="taskDialog.visible" :title="taskDialog.type === 'create' ? '新增任务' : '编辑任务'" width="800px">
    <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="120px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="taskForm.taskName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模块" prop="moduleType">
            <el-select v-model="taskForm.moduleType" class="!w-full" clearable>
              <el-option label="mobility" value="mobility" />
              <el-option label="stability" value="stability" />
              <el-option label="control" value="control" />
              <el-option label="integration" value="integration" />
              <el-option label="load" value="load" />
              <el-option label="breathing" value="breathing" />
              <el-option label="balance" value="balance" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="执行类型" prop="executionType">
            <el-select v-model="taskForm.executionType" class="!w-full">
              <el-option label="home" value="home" />
              <el-option label="clinic" value="clinic" />
              <el-option label="both" value="both" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="周频次" prop="frequencyPerWeek">
            <el-input-number v-model="taskForm.frequencyPerWeek" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="组数" prop="sets">
            <el-input-number v-model="taskForm.sets" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="次数" prop="repetitions">
            <el-input-number v-model="taskForm.repetitions" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="保持秒数" prop="holdSeconds">
            <el-input-number v-model="taskForm.holdSeconds" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="剂量文本" prop="dosageText">
        <el-input v-model="taskForm.dosageText" />
      </el-form-item>
      <el-form-item label="目标缺陷" prop="targetDeficit">
        <el-input v-model="taskForm.targetDeficit" />
      </el-form-item>
      <el-form-item label="疼痛限制规则" prop="painLimitRule">
        <el-input v-model="taskForm.painLimitRule" />
      </el-form-item>
      <el-form-item label="终止条件" prop="stopRule">
        <el-input v-model="taskForm.stopRule" />
      </el-form-item>
      <el-form-item label="进阶规则" prop="progressionRule">
        <el-input v-model="taskForm.progressionRule" />
      </el-form-item>
      <el-form-item label="退阶规则" prop="regressionRule">
        <el-input v-model="taskForm.regressionRule" />
      </el-form-item>
      <el-form-item label="动作说明" prop="instructionText">
        <el-input v-model="taskForm.instructionText" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="taskDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="taskDialog.loading" @click="submitTask">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="checkinDialog.visible" title="新增代录打卡" width="960px">
    <el-form ref="checkinFormRef" :model="checkinForm" :rules="checkinRules" label-width="130px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="打卡日期" prop="checkinDate">
            <el-date-picker v-model="checkinForm.checkinDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="提交角色" prop="submitRoleType">
            <el-select v-model="checkinForm.submitRoleType" class="!w-full">
              <el-option label="therapist" value="therapist" />
              <el-option label="patient" value="patient" />
              <el-option label="clerk" value="clerk" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="整体完成率" prop="overallCompletionRate">
            <el-input-number v-model="checkinForm.overallCompletionRate" :min="0" :max="100" :step="1" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="疼痛(前)" prop="painScoreBefore">
            <el-input-number v-model="checkinForm.painScoreBefore" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="疼痛(后)" prop="painScoreAfter">
            <el-input-number v-model="checkinForm.painScoreAfter" :min="0" :max="10" :step="0.5" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="疲劳等级" prop="fatigueLevel">
            <el-input-number v-model="checkinForm.fatigueLevel" :min="0" :max="10" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注" prop="overallComment">
        <el-input v-model="checkinForm.overallComment" type="textarea" :rows="2" />
      </el-form-item>

      <el-divider content-position="left">任务执行</el-divider>
      <el-table :data="checkinForm.taskExecutions" stripe>
        <el-table-column label="任务" min-width="180">
          <template #default="scope">{{ scope.row.taskName }}</template>
        </el-table-column>
        <el-table-column label="完成状态" min-width="160">
          <template #default="scope">
            <el-select v-model="scope.row.completionStatus" class="!w-full">
              <el-option label="completed" value="completed" />
              <el-option label="partial" value="partial" />
              <el-option label="skipped" value="skipped" />
              <el-option label="pain_stop" value="pain_stop" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="完成组数" min-width="110">
          <template #default="scope">
            <el-input-number v-model="scope.row.completedSets" :min="0" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="完成次数" min-width="110">
          <template #default="scope">
            <el-input-number v-model="scope.row.completedReps" :min="0" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="疼痛" min-width="100">
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
      <el-button @click="checkinDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="checkinDialog.loading" @click="submitCheckin">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="executionDialog.visible" title="任务执行明细" width="860px">
    <el-table :data="executionDialog.items" stripe>
      <el-table-column label="任务" prop="taskName" min-width="160" />
      <el-table-column label="状态" prop="completionStatus" min-width="120" />
      <el-table-column label="组数" prop="completedSets" min-width="90" />
      <el-table-column label="次数" prop="completedReps" min-width="90" />
      <el-table-column label="疼痛" prop="painScore" min-width="90" />
      <el-table-column label="难度" prop="difficultyLevel" min-width="90" />
      <el-table-column label="备注" prop="taskComment" min-width="240" show-overflow-tooltip />
    </el-table>
  </Dialog>

  <Dialog v-model="triggerDialog.visible" title="手动创建复评触发" width="650px">
    <el-form ref="triggerFormRef" :model="triggerForm" :rules="triggerRules" label-width="130px">
      <el-form-item label="触发类型" prop="triggerType">
        <el-select v-model="triggerForm.triggerType" class="!w-full">
          <el-option label="time_due" value="time_due" />
          <el-option label="pain_upgrade" value="pain_upgrade" />
          <el-option label="low_adherence" value="low_adherence" />
          <el-option label="stage_end" value="stage_end" />
          <el-option label="target_not_met" value="target_not_met" />
          <el-option label="target_met" value="target_met" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发等级" prop="triggerLevel">
        <el-select v-model="triggerForm.triggerLevel" class="!w-full">
          <el-option label="low" value="low" />
          <el-option label="medium" value="medium" />
          <el-option label="high" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="触发说明" prop="triggerMessage">
        <el-input v-model="triggerForm.triggerMessage" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="建议动作" prop="suggestedAction">
        <el-input v-model="triggerForm.suggestedAction" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="建议处理日期" prop="dueDate">
        <el-date-picker v-model="triggerForm.dueDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="triggerDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="triggerDialog.loading" @click="submitTrigger">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-if="AI_ENABLED" v-model="aiPlanDialog" title="AI 计划草案详情" width="70%">
    <div class="ai-label">渲染文本</div>
    <div class="ai-rendered">{{ aiPlanDraft?.renderedText || '-' }}</div>
    <el-divider />
    <div class="ai-label">结构化 JSON</div>
    <pre class="ai-json">{{ prettyJson(aiPlanDraft?.contentJson) }}</pre>
    <div class="ai-label">evidence_refs</div>
    <pre class="ai-json">{{ prettyJson(aiPlanDraft?.evidenceRefsJson) }}</pre>
  </Dialog>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { generatePlanDraft, getRehabAiOutputPage, RehabAiGenerateRespVO } from '@/api/rehab/ai'
import {
  activateRehabPlan,
  completeRehabPlan,
  getRehabPlan,
  getRehabPlanOperationLog,
  pauseRehabPlan
} from '@/api/rehab/plan'
import {
  createRehabTask,
  disableRehabTask,
  enableRehabTask,
  getRehabTaskListByPlan,
  sortRehabTasks,
  updateRehabTask
} from '@/api/rehab/task'
import {
  createRehabCheckinManual,
  getRehabCheckinPage,
  getRehabCheckinTaskExecutions
} from '@/api/rehab/checkin'
import { getRehabProgressPage, recalculateRehabProgress } from '@/api/rehab/progress'
import {
  acknowledgeRehabTrigger,
  convertRehabTrigger,
  createRehabTrigger,
  dismissRehabTrigger,
  getRehabTriggerPage
} from '@/api/rehab/reassessment-trigger'

defineOptions({ name: 'RehabPlanDetail' })

const AI_ENABLED = import.meta.env.VITE_REHAB_AI_ENABLED === 'true'
const route = useRoute()
const { push } = useRouter()
const message = useMessage()

const id = computed(() => Number(route.params.id))

const loading = ref(false)
const activeTab = ref('task')
const detail = ref<any>()
const taskList = ref<any[]>([])
const checkinList = ref<any[]>([])
const progressList = ref<any[]>([])
const triggerList = ref<any[]>([])
const operationLogs = ref<any[]>([])
const aiPlanDraft = ref<any>()
const aiPlanDialog = ref(false)

const loadPlan = async () => {
  detail.value = await getRehabPlan(id.value)
}

const loadTasks = async () => {
  taskList.value = await getRehabTaskListByPlan(id.value)
}

const loadCheckins = async () => {
  const data = await getRehabCheckinPage({ pageNo: 1, pageSize: 20, planId: id.value })
  checkinList.value = data.list || []
}

const loadProgress = async () => {
  const data = await getRehabProgressPage({ pageNo: 1, pageSize: 20, planId: id.value })
  progressList.value = data.list || []
}

const loadTriggers = async () => {
  const data = await getRehabTriggerPage({ pageNo: 1, pageSize: 20, planId: id.value })
  triggerList.value = data.list || []
}

const loadOperationLogs = async () => {
  operationLogs.value = await getRehabPlanOperationLog(id.value)
}

const loadAiPlanDraft = async () => {
  if (!AI_ENABLED) {
    aiPlanDraft.value = undefined
    return
  }
  if (!detail.value?.patientId) {
    aiPlanDraft.value = undefined
    return
  }
  const data = await getRehabAiOutputPage({
    pageNo: 1,
    pageSize: 50,
    patientId: detail.value.patientId,
    outputType: 'plan_draft',
    targetObjectType: 'plan'
  })
  const rows = data.list || []
  aiPlanDraft.value = rows.find((item: any) => Number(item.targetObjectId) === id.value) || undefined
}

const load = async () => {
  loading.value = true
  try {
    await Promise.all([loadPlan(), loadTasks(), loadCheckins(), loadProgress(), loadTriggers(), loadOperationLogs()])
    if (AI_ENABLED) {
      await loadAiPlanDraft()
    }
  } finally {
    loading.value = false
  }
}

const refreshExecutionChain = async () => {
  await Promise.all([loadCheckins(), loadProgress(), loadTriggers(), loadPlan()])
}

const goBack = () => {
  if (detail.value?.patientId) {
    push(`/rehab/patient/detail/${detail.value.patientId}`)
    return
  }
  push('/rehab/plan')
}

const openEdit = () => {
  push(`/rehab/plan/edit/${id.value}`)
}

const changePlanStatus = async (action: 'activate' | 'pause' | 'complete') => {
  await message.confirm(`确认${action}当前计划吗？`)
  if (action === 'activate') await activateRehabPlan({ id: id.value })
  if (action === 'pause') await pauseRehabPlan({ id: id.value })
  if (action === 'complete') await completeRehabPlan({ id: id.value })
  message.success('操作成功')
  await Promise.all([loadPlan(), loadOperationLogs()])
}

const taskDialog = reactive({
  visible: false,
  type: 'create' as 'create' | 'update',
  loading: false
})
const taskFormRef = ref()
const taskForm = reactive<Record<string, any>>({
  id: undefined,
  taskName: '',
  moduleType: 'control',
  executionType: 'both',
  targetDeficit: '',
  bodyRegion: '',
  dosageText: '',
  repetitions: 0,
  sets: 0,
  holdSeconds: 0,
  frequencyPerWeek: 3,
  tempo: '',
  painLimitRule: '',
  stopRule: '',
  progressionRule: '',
  regressionRule: '',
  replacementExercise: '',
  instructionText: '',
  mediaUrl: '',
  status: 'active',
  sortOrder: 1
})
const taskRules = reactive({
  taskName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }]
})

const resetTaskForm = () => {
  Object.assign(taskForm, {
    id: undefined,
    taskName: '',
    moduleType: 'control',
    executionType: 'both',
    targetDeficit: '',
    bodyRegion: '',
    dosageText: '',
    repetitions: 0,
    sets: 0,
    holdSeconds: 0,
    frequencyPerWeek: 3,
    tempo: '',
    painLimitRule: '',
    stopRule: '',
    progressionRule: '',
    regressionRule: '',
    replacementExercise: '',
    instructionText: '',
    mediaUrl: '',
    status: 'active',
    sortOrder: taskList.value.length + 1
  })
}

const openTaskDialog = (type: 'create' | 'update', row?: any) => {
  taskDialog.type = type
  taskDialog.visible = true
  if (type === 'update' && row) {
    Object.assign(taskForm, row)
    return
  }
  resetTaskForm()
}

const submitTask = async () => {
  await taskFormRef.value.validate()
  taskDialog.loading = true
  try {
    const payload = {
      ...taskForm,
      planId: detail.value.id,
      patientId: detail.value.patientId,
      episodeId: detail.value.episodeId
    }
    if (taskDialog.type === 'create') {
      await createRehabTask(payload)
    } else {
      await updateRehabTask(payload)
    }
    message.success('任务保存成功')
    taskDialog.visible = false
    await Promise.all([loadTasks(), loadOperationLogs()])
  } finally {
    taskDialog.loading = false
  }
}

const saveTaskSort = async () => {
  if (!taskList.value.length) {
    message.warning('暂无任务可排序')
    return
  }
  await sortRehabTasks({
    planId: id.value,
    items: taskList.value.map((item) => ({ id: item.id, sortOrder: item.sortOrder || 1 }))
  })
  message.success('排序已保存')
  await Promise.all([loadTasks(), loadOperationLogs()])
}

const toggleTask = async (row: any, action: 'enable' | 'disable') => {
  await message.confirm(`确认${action === 'enable' ? '启用' : '停用'}任务 ${row.taskName} 吗？`)
  if (action === 'enable') {
    await enableRehabTask({ id: row.id })
  } else {
    await disableRehabTask({ id: row.id })
  }
  message.success('操作成功')
  await Promise.all([loadTasks(), loadOperationLogs()])
}

const checkinDialog = reactive({ visible: false, loading: false })
const checkinFormRef = ref()
const checkinForm = reactive<Record<string, any>>({
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
const checkinRules = reactive({
  checkinDate: [{ required: true, message: '打卡日期不能为空', trigger: 'change' }],
  submitRoleType: [{ required: true, message: '提交角色不能为空', trigger: 'change' }]
})

const openCheckinDialog = () => {
  const enabledTasks = taskList.value.filter((item) => item.status !== 'disabled')
  if (!enabledTasks.length) {
    message.warning('当前计划无可执行任务')
    return
  }
  checkinForm.checkinDate = dayjs().format('YYYY-MM-DD')
  checkinForm.submitRoleType = 'therapist'
  checkinForm.overallCompletionRate = undefined
  checkinForm.painScoreBefore = undefined
  checkinForm.painScoreAfter = undefined
  checkinForm.fatigueLevel = undefined
  checkinForm.confidenceLevel = undefined
  checkinForm.overallComment = ''
  checkinForm.taskExecutions = enabledTasks.map((item) => ({
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
  checkinDialog.visible = true
}

const submitCheckin = async () => {
  await checkinFormRef.value.validate()
  checkinDialog.loading = true
  try {
    await createRehabCheckinManual({
      patientId: detail.value.patientId,
      episodeId: detail.value.episodeId,
      planId: detail.value.id,
      checkinDate: checkinForm.checkinDate,
      submitRoleType: checkinForm.submitRoleType,
      overallCompletionRate: checkinForm.overallCompletionRate,
      painScoreBefore: checkinForm.painScoreBefore,
      painScoreAfter: checkinForm.painScoreAfter,
      fatigueLevel: checkinForm.fatigueLevel,
      confidenceLevel: checkinForm.confidenceLevel,
      overallComment: checkinForm.overallComment,
      taskExecutions: checkinForm.taskExecutions.map((item: any) => ({
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
    checkinDialog.visible = false
    await Promise.all([refreshExecutionChain(), loadOperationLogs()])
  } finally {
    checkinDialog.loading = false
  }
}

const executionDialog = reactive({
  visible: false,
  items: [] as any[]
})

const showExecutions = async (row: any) => {
  executionDialog.items = await getRehabCheckinTaskExecutions(row.id)
  executionDialog.visible = true
}

const recalculateProgress = async () => {
  await recalculateRehabProgress({ planId: id.value })
  message.success('进度重算成功')
  await Promise.all([loadProgress(), loadTriggers(), loadPlan(), loadOperationLogs()])
}

const triggerDialog = reactive({ visible: false, loading: false })
const triggerFormRef = ref()
const triggerForm = reactive<Record<string, any>>({
  triggerType: 'time_due',
  triggerLevel: 'medium',
  triggerMessage: '',
  suggestedAction: '',
  dueDate: dayjs().add(2, 'day').format('YYYY-MM-DD')
})
const triggerRules = reactive({
  triggerType: [{ required: true, message: '触发类型不能为空', trigger: 'change' }]
})

const openTriggerDialog = () => {
  triggerForm.triggerType = 'time_due'
  triggerForm.triggerLevel = 'medium'
  triggerForm.triggerMessage = ''
  triggerForm.suggestedAction = ''
  triggerForm.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DD')
  triggerDialog.visible = true
}

const submitTrigger = async () => {
  await triggerFormRef.value.validate()
  triggerDialog.loading = true
  try {
    await createRehabTrigger({
      patientId: detail.value.patientId,
      episodeId: detail.value.episodeId,
      planId: detail.value.id,
      triggerType: triggerForm.triggerType,
      triggerLevel: triggerForm.triggerLevel,
      triggerMessage: triggerForm.triggerMessage,
      suggestedAction: triggerForm.suggestedAction,
      dueDate: triggerForm.dueDate
    })
    message.success('触发已创建')
    triggerDialog.visible = false
    await Promise.all([loadTriggers(), loadOperationLogs()])
  } finally {
    triggerDialog.loading = false
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
  await Promise.all([loadTriggers(), loadOperationLogs()])
}

const goCheckinCenter = () => {
  push(`/rehab/checkin?planId=${id.value}&patientId=${detail.value?.patientId || ''}`)
}

const goTriggerCenter = () => {
  push(`/rehab/reassessment-trigger?planId=${id.value}&patientId=${detail.value?.patientId || ''}`)
}

const handleGenerateAiPlanDraft = async () => {
  if (!AI_ENABLED) return
  if (!detail.value) return
  await message.confirm('确认生成该计划的 AI 草案吗？')
  const resp: RehabAiGenerateRespVO = await generatePlanDraft({
    patientId: detail.value.patientId,
    episodeId: detail.value.episodeId,
    assessmentId: detail.value.sourceAssessmentId || undefined,
    asyncMode: false
  })
  message.success(resp.fallbackUsed ? 'AI 草案已降级生成' : 'AI 草案生成成功')
  await loadAiPlanDraft()
}

const prettyJson = (value?: string) => {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(() => {
  load()
})
</script>

<style scoped>
.ai-rendered {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.ai-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.ai-json {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow: auto;
}
</style>
