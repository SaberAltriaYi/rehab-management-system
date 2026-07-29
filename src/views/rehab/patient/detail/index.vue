<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <div class="text-16px font-bold">患者详情</div>
      <div>
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" v-hasPermi="['rehab:patient:assign']" @click="openAssign">分配</el-button>
        <el-button type="primary" v-hasPermi="['rehab:patient:transfer']" @click="openTransfer">转交</el-button>
        <el-button type="primary" v-hasPermi="['rehab:patient:bind-crm']" @click="openBindCrm">CRM 绑定</el-button>
      </div>
    </div>

    <el-skeleton :loading="loading" animated>
      <template #template>
        <el-skeleton-item variant="rect" style="height: 180px" />
      </template>
      <template #default>
        <el-row :gutter="12" class="mb-12px">
          <el-col :span="16">
            <el-card shadow="never">
              <template #header>基本信息</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="患者编号">{{ detail?.patient?.patientNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ detail?.patient?.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="性别">
                  <dict-tag :type="DICT_TYPE.SYSTEM_USER_SEX" :value="detail?.patient?.gender ?? ''" />
                </el-descriptions-item>
                <el-descriptions-item label="年龄">{{ detail?.patient?.age ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">{{ detail?.patient?.phone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前阶段">{{ detail?.patient?.currentStage || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前状态">{{ detail?.patient?.currentStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="主责治疗师">{{ detail?.patient?.currentTherapistName || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="mb-12px">
              <template #header>CRM 绑定</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="状态">{{ detail?.crmBinding?.bindStatus || 'unbound' }}</el-descriptions-item>
                <el-descriptions-item label="CRM 客户ID">{{ detail?.crmBinding?.crmCustomerId || '-' }}</el-descriptions-item>
                <el-descriptions-item label="CRM 客户名">{{ detail?.crmBinding?.crmCustomerName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="CRM 手机">{{ detail?.crmBinding?.crmCustomerMobile || '-' }}</el-descriptions-item>
                <el-descriptions-item label="同步状态">{{ detail?.crmBinding?.syncStatus || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
            <el-card shadow="never" class="mb-12px">
              <template #header>会员绑定</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="绑定状态">{{ detail?.memberBinding?.bindStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="会员ID">{{ detail?.memberBinding?.appUserId || '-' }}</el-descriptions-item>
                <el-descriptions-item label="会员昵称">{{ detail?.memberBinding?.memberNickname || detail?.memberBinding?.nickname || '-' }}</el-descriptions-item>
                <el-descriptions-item label="会员手机号">{{ detail?.memberBinding?.memberMobile || detail?.memberBinding?.phone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="最近登录">{{ detail?.memberBinding?.lastLoginTime || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
            <el-card shadow="never">
              <template #header>当前 Episode</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="Episode号">{{ detail?.currentEpisode?.episodeNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="类型">{{ detail?.currentEpisode?.episodeType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="阶段">{{ detail?.currentEpisode?.currentStage || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail?.currentEpisode?.status || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="Episode 列表" name="episode">
            <el-table :data="episodeList" stripe>
              <el-table-column label="episode_no" prop="episodeNo" min-width="150" />
              <el-table-column label="类型" prop="episodeType" min-width="120" />
              <el-table-column label="阶段" prop="currentStage" min-width="120" />
              <el-table-column label="状态" prop="status" min-width="100" />
              <el-table-column label="开始日期" prop="startDate" min-width="120" />
              <el-table-column label="结束日期" prop="endDate" min-width="120" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="评估记录" name="assessment">
            <el-form :inline="true" :model="assessmentQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="Episode">
                <el-select v-model="assessmentQuery.episodeId" clearable class="!w-160px" @change="loadAssessmentList">
                  <el-option v-for="ep in episodeList" :key="ep.id" :label="ep.episodeNo" :value="ep.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="assessmentQuery.assessmentType" clearable class="!w-140px" @change="loadAssessmentList">
                  <el-option
                    v-for="item in enabledAssessmentTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="assessmentQuery.status" clearable class="!w-140px" @change="loadAssessmentList">
                  <el-option label="draft" value="draft" />
                  <el-option label="completed" value="completed" />
                  <el-option label="reviewed" value="reviewed" />
                  <el-option label="archived" value="archived" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:assessment:create']" @click="openCreateAssessment">新建评估</el-button>
                <el-button @click="loadAssessmentList">刷新</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="assessmentList" stripe>
              <el-table-column label="assessment_no" prop="assessmentNo" min-width="160" />
              <el-table-column label="episode_no" prop="episodeNo" min-width="140" />
              <el-table-column label="类型" min-width="140">
                <template #default="scope">
                  {{ getAssessmentTypeLabel(scope.row.assessmentType) }}
                </template>
              </el-table-column>
              <el-table-column label="日期" prop="assessmentDate" min-width="110" />
              <el-table-column label="状态" prop="status" min-width="100" />
              <el-table-column label="质量" prop="qualityGrade" min-width="80" />
              <el-table-column label="置信" prop="confidenceGrade" min-width="90" />
              <el-table-column label="操作" min-width="280" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link @click="openAssessmentDetail(scope.row.id)">详情</el-button>
                  <el-button
                    type="primary"
                    link
                    v-hasPermi="['rehab:assessment:generate-report']"
                    @click="handleGenerateReport(scope.row)"
                  >
                    生成报告
                  </el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:plan:create']" @click="createPlanFromAssessment(scope.row)">
                    创建计划
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="报告记录" name="report">
            <el-form :inline="true" :model="reportQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="状态">
                <el-select v-model="reportQuery.reportStatus" clearable class="!w-140px" @change="loadReportList">
                  <el-option label="draft" value="draft" />
                  <el-option label="reviewed" value="reviewed" />
                  <el-option label="approved" value="approved" />
                  <el-option label="exported" value="exported" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button @click="loadReportList">刷新</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="reportList" stripe>
              <el-table-column label="report_no" prop="reportNo" min-width="160" />
              <el-table-column label="assessment_no" prop="assessmentNo" min-width="150" />
              <el-table-column label="版本" prop="reportVersion" min-width="80" />
              <el-table-column label="状态" prop="reportStatus" min-width="100" />
              <el-table-column label="模式" prop="generationMode" min-width="120" />
              <el-table-column label="更新时间" prop="updateTime" min-width="170" />
              <el-table-column label="操作" min-width="260" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link v-hasPermi="['rehab:report:preview']" @click="previewReport(scope.row)">
                    预览
                  </el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:report:export']" @click="exportDocx(scope.row)">
                    导出DOCX
                  </el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:report:export']" @click="exportPdf(scope.row)">
                    导出PDF
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="计划记录" name="plan">
            <el-form :inline="true" :model="planQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="状态">
                <el-select v-model="planQuery.status" clearable class="!w-140px" @change="loadPlanList">
                  <el-option label="draft" value="draft" />
                  <el-option label="active" value="active" />
                  <el-option label="paused" value="paused" />
                  <el-option label="completed" value="completed" />
                  <el-option label="closed" value="closed" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:plan:create']" @click="openCreatePlan">新建计划</el-button>
                <el-button @click="loadPlanList">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="planList" stripe>
              <el-table-column label="plan_no" prop="planNo" min-width="150" />
              <el-table-column label="episode_no" prop="episodeNo" min-width="140" />
              <el-table-column label="名称" prop="planName" min-width="180" />
              <el-table-column label="类型" prop="planType" min-width="120" />
              <el-table-column label="状态" prop="status" min-width="100" />
              <el-table-column label="起止" min-width="220">
                <template #default="scope">{{ scope.row.startDate || '-' }} ~ {{ scope.row.endDate || '-' }}</template>
              </el-table-column>
              <el-table-column label="最近进度" prop="latestProgressSummary" min-width="260" show-overflow-tooltip />
              <el-table-column label="操作" min-width="300" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link @click="openPlanDetail(scope.row)">详情</el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:plan:activate']" @click="changePlanStatus('activate', scope.row)">激活</el-button>
                  <el-button type="warning" link v-hasPermi="['rehab:plan:pause']" @click="changePlanStatus('pause', scope.row)">暂停</el-button>
                  <el-button type="success" link v-hasPermi="['rehab:plan:complete']" @click="changePlanStatus('complete', scope.row)">完成</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="任务清单" name="task">
            <el-form :inline="true" :model="taskQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="计划">
                <el-select v-model="taskQuery.planId" clearable filterable class="!w-260px" @change="loadTaskList">
                  <el-option v-for="item in planList" :key="item.id" :label="`${item.planNo} (${item.status})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:task:create']" @click="openTaskDialog('create')">新增任务</el-button>
                <el-button v-hasPermi="['rehab:task:sort']" @click="saveTaskSort">保存排序</el-button>
                <el-button @click="loadTaskList">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="taskList" stripe>
              <el-table-column label="排序" min-width="100">
                <template #default="scope">
                  <el-input-number v-model="scope.row.sortOrder" :min="1" :max="999" controls-position="right" size="small" class="!w-90px" />
                </template>
              </el-table-column>
              <el-table-column label="task_no" prop="taskNo" min-width="150" />
              <el-table-column label="任务" prop="taskName" min-width="160" />
              <el-table-column label="模块" prop="moduleType" min-width="110" />
              <el-table-column label="剂量" prop="dosageText" min-width="140" />
              <el-table-column label="频次" prop="frequencyPerWeek" min-width="80" />
              <el-table-column label="状态" prop="status" min-width="100" />
              <el-table-column label="操作" min-width="220" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link v-hasPermi="['rehab:task:update']" @click="openTaskDialog('update', scope.row)">编辑</el-button>
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
            <el-form :inline="true" :model="checkinQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="计划">
                <el-select v-model="checkinQuery.planId" clearable filterable class="!w-260px" @change="loadCheckinList">
                  <el-option v-for="item in planList" :key="item.id" :label="`${item.planNo} (${item.status})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="角色">
                <el-select v-model="checkinQuery.submitRoleType" clearable class="!w-140px" @change="loadCheckinList">
                  <el-option label="patient" value="patient" />
                  <el-option label="therapist" value="therapist" />
                  <el-option label="clerk" value="clerk" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:checkin:create-manual']" @click="openCheckinDialog">代录打卡</el-button>
                <el-button @click="loadCheckinList">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="checkinList" stripe>
              <el-table-column label="日期" prop="checkinDate" min-width="110" />
              <el-table-column label="计划" prop="planNo" min-width="150" />
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
            <el-form :inline="true" :model="progressQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="计划">
                <el-select v-model="progressQuery.planId" clearable filterable class="!w-260px" @change="loadProgressList">
                  <el-option v-for="item in planList" :key="item.id" :label="`${item.planNo} (${item.status})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:progress:detail']" @click="recalculateProgress">重算进度</el-button>
                <el-button @click="loadProgressList">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="progressList" stripe>
              <el-table-column label="周期" min-width="180">
                <template #default="scope">{{ scope.row.periodStart }} ~ {{ scope.row.periodEnd }}</template>
              </el-table-column>
              <el-table-column label="计划任务" prop="plannedTaskCount" min-width="90" />
              <el-table-column label="完成任务" prop="completedTaskCount" min-width="90" />
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
            <el-form :inline="true" :model="triggerQuery" class="-mb-15px" label-width="80px">
              <el-form-item label="计划">
                <el-select v-model="triggerQuery.planId" clearable filterable class="!w-260px" @change="loadTriggerList">
                  <el-option v-for="item in planList" :key="item.id" :label="`${item.planNo} (${item.status})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="triggerQuery.triggerStatus" clearable class="!w-180px" @change="loadTriggerList">
                  <el-option label="pending" value="pending" />
                  <el-option label="acknowledged" value="acknowledged" />
                  <el-option label="converted_to_reassessment" value="converted_to_reassessment" />
                  <el-option label="dismissed" value="dismissed" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" v-hasPermi="['rehab:reassessment-trigger:create']" @click="openTriggerDialog">手动触发</el-button>
                <el-button @click="loadTriggerList">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="triggerList" stripe>
              <el-table-column label="类型" prop="triggerType" min-width="120" />
              <el-table-column label="等级" prop="triggerLevel" min-width="90" />
              <el-table-column label="状态" prop="triggerStatus" min-width="180" />
              <el-table-column label="触发说明" prop="triggerMessage" min-width="220" show-overflow-tooltip />
              <el-table-column label="建议动作" prop="suggestedAction" min-width="220" show-overflow-tooltip />
              <el-table-column label="到期" prop="dueDate" min-width="110" />
              <el-table-column label="操作" min-width="260" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'ack')">确认</el-button>
                  <el-button type="primary" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'convert')">转复评</el-button>
                  <el-button type="danger" link v-hasPermi="['rehab:reassessment-trigger:handle']" @click="handleTrigger(scope.row, 'dismiss')">忽略</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane v-if="AI_ENABLED" label="AI 摘要" name="ai">
            <div class="mb-12px flex items-center gap-8px">
              <el-button type="primary" v-hasPermi="['rehab:ai:generate']" @click="handleGenerateAiRisk">生成风险解释</el-button>
              <el-button type="primary" v-hasPermi="['rehab:ai:generate']" @click="handleGenerateAiFollowup">
                生成随访文案
              </el-button>
              <el-button type="primary" v-hasPermi="['rehab:ai:generate']" @click="handleGenerateAiPlanDraft">
                生成计划草案
              </el-button>
              <el-button @click="loadAiOutputs">刷新</el-button>
            </div>
            <el-table :data="aiOutputList" stripe>
              <el-table-column label="输出类型" prop="outputType" min-width="160" />
              <el-table-column label="目标对象" min-width="160">
                <template #default="scope">{{ scope.row.targetObjectType }}#{{ scope.row.targetObjectId || '-' }}</template>
              </el-table-column>
              <el-table-column label="审核状态" prop="reviewStatus" min-width="120" />
              <el-table-column label="安全状态" prop="safetyStatus" min-width="120" />
              <el-table-column label="患者可见" min-width="100">
                <template #default="scope">{{ scope.row.patientVisible ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="输出文本" prop="renderedText" min-width="320" show-overflow-tooltip />
              <el-table-column label="时间" prop="createTime" min-width="170" />
              <el-table-column label="操作" min-width="120" fixed="right">
                <template #default="scope">
                  <el-button type="primary" link @click="openAiDetail(scope.row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="分配记录" name="assign">
            <el-table :data="detail?.assignmentHistory || []" stripe>
              <el-table-column label="治疗师" prop="therapistName" min-width="120" />
              <el-table-column label="角色" prop="roleType" min-width="100" />
              <el-table-column label="状态" prop="assignStatus" min-width="100" />
              <el-table-column label="开始时间" prop="startTime" min-width="170" />
              <el-table-column label="结束时间" prop="endTime" min-width="170" />
              <el-table-column label="原因" prop="assignReason" min-width="200" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="操作日志" name="log">
            <el-table :data="detail?.operationLogs || []" stripe>
              <el-table-column label="时间" prop="createTime" min-width="170" />
              <el-table-column label="操作类型" prop="operationType" min-width="120" />
              <el-table-column label="操作人" prop="operatorName" min-width="120" />
              <el-table-column label="备注" prop="remark" min-width="220" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-skeleton>
  </ContentWrap>

  <AssignTherapistDialog ref="assignDialogRef" @success="load" />
  <BindCrmDialog ref="bindCrmDialogRef" @success="load" />

  <Dialog v-model="previewVisible" title="报告预览" width="70%">
    <div class="preview-wrap" v-html="previewHtml"></div>
  </Dialog>

  <Dialog v-if="AI_ENABLED" v-model="aiDetailVisible" title="AI 输出详情" width="70%">
    <el-descriptions :column="2" border v-if="currentAiOutput" class="mb-10px">
      <el-descriptions-item label="输出类型">{{ currentAiOutput.outputType }}</el-descriptions-item>
      <el-descriptions-item label="目标对象">
        {{ currentAiOutput.targetObjectType }}#{{ currentAiOutput.targetObjectId }}
      </el-descriptions-item>
      <el-descriptions-item label="审核状态">{{ currentAiOutput.reviewStatus }}</el-descriptions-item>
      <el-descriptions-item label="安全状态">{{ currentAiOutput.safetyStatus }}</el-descriptions-item>
      <el-descriptions-item label="患者可见">{{ currentAiOutput.patientVisible ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="输出时间">{{ currentAiOutput.createTime }}</el-descriptions-item>
    </el-descriptions>
    <div class="detail-label">渲染文本</div>
    <div class="ai-rendered">{{ currentAiOutput?.renderedText || '-' }}</div>
    <el-divider />
    <div class="detail-label">evidence_refs</div>
    <pre class="ai-json">{{ prettyJson(currentAiOutput?.evidenceRefsJson) }}</pre>
    <div class="detail-label">content_json</div>
    <pre class="ai-json">{{ prettyJson(currentAiOutput?.contentJson) }}</pre>
  </Dialog>

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
          <el-form-item label="组数" prop="sets"><el-input-number v-model="taskForm.sets" :min="0" class="!w-full" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="次数" prop="repetitions"><el-input-number v-model="taskForm.repetitions" :min="0" class="!w-full" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="保持秒" prop="holdSeconds"><el-input-number v-model="taskForm.holdSeconds" :min="0" class="!w-full" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="剂量文本" prop="dosageText"><el-input v-model="taskForm.dosageText" /></el-form-item>
      <el-form-item label="目标缺陷" prop="targetDeficit"><el-input v-model="taskForm.targetDeficit" /></el-form-item>
      <el-form-item label="疼痛限制" prop="painLimitRule"><el-input v-model="taskForm.painLimitRule" /></el-form-item>
      <el-form-item label="终止条件" prop="stopRule"><el-input v-model="taskForm.stopRule" /></el-form-item>
      <el-form-item label="动作说明" prop="instructionText"><el-input v-model="taskForm.instructionText" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="taskDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="taskDialog.loading" @click="submitTask">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="checkinDialog.visible" title="代录打卡" width="960px">
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
            <el-input-number v-model="checkinForm.overallCompletionRate" :min="0" :max="100" class="!w-full" />
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
        <el-table-column label="状态" min-width="140">
          <template #default="scope">
            <el-select v-model="scope.row.completionStatus" class="!w-full">
              <el-option label="completed" value="completed" />
              <el-option label="partial" value="partial" />
              <el-option label="skipped" value="skipped" />
              <el-option label="pain_stop" value="pain_stop" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="组数" min-width="100">
          <template #default="scope"><el-input-number v-model="scope.row.completedSets" :min="0" class="!w-full" /></template>
        </el-table-column>
        <el-table-column label="次数" min-width="100">
          <template #default="scope"><el-input-number v-model="scope.row.completedReps" :min="0" class="!w-full" /></template>
        </el-table-column>
        <el-table-column label="疼痛" min-width="90">
          <template #default="scope"><el-input-number v-model="scope.row.painScore" :min="0" :max="10" :step="0.5" class="!w-full" /></template>
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
      <el-table-column label="备注" prop="taskComment" min-width="240" show-overflow-tooltip />
    </el-table>
  </Dialog>

  <Dialog v-model="triggerDialog.visible" title="手动创建复评触发" width="660px">
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
      <el-form-item label="触发说明" prop="triggerMessage"><el-input v-model="triggerForm.triggerMessage" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="建议动作" prop="suggestedAction"><el-input v-model="triggerForm.suggestedAction" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="建议日期" prop="dueDate">
        <el-date-picker v-model="triggerForm.dueDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="triggerDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="triggerDialog.loading" @click="submitTrigger">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { DICT_TYPE } from '@/utils/dict'
import download from '@/utils/download'
import { getRehabEpisodePage } from '@/api/rehab/episode'
import { getRehabAssessmentPage } from '@/api/rehab/assessment'
import {
  exportRehabReportDocx,
  exportRehabReportPdf,
  generateRehabReport,
  getRehabReportPage,
  previewRehabReport
} from '@/api/rehab/report'
import {
  activateRehabPlan,
  completeRehabPlan,
  getRehabPlanPage,
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
import {
  generateFollowupMessage,
  generatePlanDraft,
  generateRiskExplanation,
  getRehabAiOutputPage,
  RehabAiGenerateRespVO
} from '@/api/rehab/ai'
import { getRehabPatient, RehabPatientDetailVO } from '@/api/rehab/patient'
import {
  ASSESSMENT_TYPE_OPTIONS,
  getAssessmentTypeLabel
} from '@/views/rehab/assessment/config/assessmentTypeOptions'
import AssignTherapistDialog from '../AssignTherapistDialog.vue'
import BindCrmDialog from '../BindCrmDialog.vue'

defineOptions({ name: 'RehabPatientDetail' })

const AI_ENABLED = import.meta.env.VITE_REHAB_AI_ENABLED === 'true'
const route = useRoute()
const { push } = useRouter()
const message = useMessage()

const id = computed(() => Number(route.params.id))
const loading = ref(false)
const detail = ref<RehabPatientDetailVO>()
const activeTab = ref('episode')

const episodeList = ref<any[]>([])
const assessmentList = ref<any[]>([])
const reportList = ref<any[]>([])
const planList = ref<any[]>([])
const taskList = ref<any[]>([])
const checkinList = ref<any[]>([])
const progressList = ref<any[]>([])
const triggerList = ref<any[]>([])
const aiOutputList = ref<any[]>([])
const enabledAssessmentTypeOptions = ASSESSMENT_TYPE_OPTIONS.filter((item) => item.enabled !== false)

const assessmentQuery = reactive({
  episodeId: undefined as number | undefined,
  assessmentType: undefined as string | undefined,
  status: undefined as string | undefined
})

const reportQuery = reactive({
  reportStatus: undefined as string | undefined
})

const planQuery = reactive({
  status: undefined as string | undefined
})

const taskQuery = reactive({
  planId: undefined as number | undefined
})

const checkinQuery = reactive({
  planId: undefined as number | undefined,
  submitRoleType: undefined as string | undefined
})

const progressQuery = reactive({
  planId: undefined as number | undefined
})

const triggerQuery = reactive({
  planId: undefined as number | undefined,
  triggerStatus: undefined as string | undefined
})

const previewVisible = ref(false)
const previewHtml = ref('')
const aiDetailVisible = ref(false)
const currentAiOutput = ref<any>()

const getPlanById = (planId?: number) => planList.value.find((item) => item.id === planId)

const ensureDefaultPlanSelection = () => {
  const activePlan = planList.value.find((item) => item.status === 'active') || planList.value[0]
  if (!activePlan) return
  if (!taskQuery.planId) taskQuery.planId = activePlan.id
  if (!checkinQuery.planId) checkinQuery.planId = activePlan.id
  if (!progressQuery.planId) progressQuery.planId = activePlan.id
  if (!triggerQuery.planId) triggerQuery.planId = activePlan.id
}

const loadEpisodeList = async () => {
  if (!id.value) return
  const data = await getRehabEpisodePage({ pageNo: 1, pageSize: 50, patientId: id.value })
  episodeList.value = data.list || []
}

const loadAssessmentList = async () => {
  if (!id.value) return
  const data = await getRehabAssessmentPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    episodeId: assessmentQuery.episodeId,
    assessmentType: assessmentQuery.assessmentType,
    status: assessmentQuery.status
  })
  assessmentList.value = data.list || []
}

const loadReportList = async () => {
  if (!id.value) return
  const data = await getRehabReportPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    reportStatus: reportQuery.reportStatus
  })
  reportList.value = data.list || []
}

const loadPlanList = async () => {
  if (!id.value) return
  const data = await getRehabPlanPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    status: planQuery.status
  })
  planList.value = data.list || []
  ensureDefaultPlanSelection()
}

const loadTaskList = async () => {
  if (!taskQuery.planId) {
    taskList.value = []
    return
  }
  taskList.value = await getRehabTaskListByPlan(taskQuery.planId)
}

const loadCheckinList = async () => {
  if (!id.value) return
  const data = await getRehabCheckinPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    planId: checkinQuery.planId,
    submitRoleType: checkinQuery.submitRoleType
  })
  checkinList.value = data.list || []
}

const loadProgressList = async () => {
  if (!id.value) return
  const data = await getRehabProgressPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    planId: progressQuery.planId
  })
  progressList.value = data.list || []
}

const loadTriggerList = async () => {
  if (!id.value) return
  const data = await getRehabTriggerPage({
    pageNo: 1,
    pageSize: 50,
    patientId: id.value,
    planId: triggerQuery.planId,
    triggerStatus: triggerQuery.triggerStatus
  })
  triggerList.value = data.list || []
}

const loadAiOutputs = async () => {
  if (!AI_ENABLED) {
    aiOutputList.value = []
    return
  }
  if (!id.value) return
  const data = await getRehabAiOutputPage({
    pageNo: 1,
    pageSize: 100,
    patientId: id.value
  })
  aiOutputList.value = data.list || []
}

const load = async () => {
  if (!id.value) return
  loading.value = true
  try {
    detail.value = await getRehabPatient(id.value)
    await Promise.all([loadEpisodeList(), loadAssessmentList(), loadReportList(), loadPlanList()])
    const detailLoaders = [loadTaskList(), loadCheckinList(), loadProgressList(), loadTriggerList()]
    if (AI_ENABLED) {
      detailLoaders.push(loadAiOutputs())
    }
    await Promise.all(detailLoaders)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  push('/rehab/patient')
}

const assignDialogRef = ref()
const openAssign = () => {
  if (!detail.value?.patient) return
  assignDialogRef.value.open(
    {
      patientId: detail.value.patient.id,
      patientName: detail.value.patient.name,
      fromTherapistUserId: detail.value.patient.currentTherapistUserId,
      fromTherapistName: detail.value.patient.currentTherapistName
    },
    'assign'
  )
}

const openTransfer = () => {
  if (!detail.value?.patient) return
  assignDialogRef.value.open(
    {
      patientId: detail.value.patient.id,
      patientName: detail.value.patient.name,
      fromTherapistUserId: detail.value.patient.currentTherapistUserId,
      fromTherapistName: detail.value.patient.currentTherapistName
    },
    'transfer'
  )
}

const bindCrmDialogRef = ref()
const openBindCrm = () => {
  if (!detail.value?.patient) return
  bindCrmDialogRef.value.open({
    patientId: detail.value.patient.id,
    patientName: detail.value.patient.name
  })
}

const openCreateAssessment = () => {
  push(`/rehab/assessment/create?patientId=${id.value}`)
}

const openAssessmentDetail = (assessmentId: number) => {
  push(`/rehab/assessment/detail/${assessmentId}`)
}

const handleGenerateReport = async (row: any) => {
  await message.confirm(`确认基于评估 ${row.assessmentNo} 生成报告吗？`)
  await generateRehabReport({ assessmentId: row.id })
  message.success('报告生成成功')
  await loadReportList()
}

const createPlanFromAssessment = (row: any) => {
  const episodeId = row.episodeId || detail.value?.currentEpisode?.id
  push(`/rehab/plan/create?patientId=${id.value}&episodeId=${episodeId || ''}&sourceAssessmentId=${row.id}`)
}

const previewReport = async (row: any) => {
  const data = await previewRehabReport(row.id)
  previewHtml.value = data.html || '<p>暂无可预览内容</p>'
  previewVisible.value = true
}

const exportDocx = async (row: any) => {
  const blob = await exportRehabReportDocx(row.id)
  download.word(blob, `${row.reportNo || 'rehab-report'}.docx`)
}

const exportPdf = async (row: any) => {
  const blob = await exportRehabReportPdf(row.id)
  download.pdf(blob, `${row.reportNo || 'rehab-report'}.pdf`)
}

const openCreatePlan = () => {
  const episodeId = detail.value?.currentEpisode?.id
  push(`/rehab/plan/create?patientId=${id.value}&episodeId=${episodeId || ''}`)
}

const openPlanDetail = (row: any) => {
  push(`/rehab/plan/detail/${row.id}`)
}

const changePlanStatus = async (action: 'activate' | 'pause' | 'complete', row: any) => {
  await message.confirm(`确认${action}计划 ${row.planNo} 吗？`)
  if (action === 'activate') await activateRehabPlan({ id: row.id })
  if (action === 'pause') await pauseRehabPlan({ id: row.id })
  if (action === 'complete') await completeRehabPlan({ id: row.id })
  message.success('操作成功')
  await Promise.all([loadPlanList(), loadProgressList(), loadTriggerList()])
}

const taskDialog = reactive({ visible: false, type: 'create' as 'create' | 'update', loading: false })
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
  if (!taskQuery.planId) {
    message.warning('请先选择计划')
    return
  }
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
  const plan = getPlanById(taskQuery.planId)
  if (!plan) {
    message.warning('未找到计划信息')
    return
  }
  taskDialog.loading = true
  try {
    const payload = {
      ...taskForm,
      planId: plan.id,
      patientId: plan.patientId,
      episodeId: plan.episodeId
    }
    if (taskDialog.type === 'create') {
      await createRehabTask(payload)
    } else {
      await updateRehabTask(payload)
    }
    message.success('任务保存成功')
    taskDialog.visible = false
    await Promise.all([loadTaskList(), loadCheckinList()])
  } finally {
    taskDialog.loading = false
  }
}

const saveTaskSort = async () => {
  if (!taskQuery.planId || !taskList.value.length) {
    message.warning('暂无可排序任务')
    return
  }
  await sortRehabTasks({
    planId: taskQuery.planId,
    items: taskList.value.map((item) => ({ id: item.id, sortOrder: item.sortOrder || 1 }))
  })
  message.success('排序已保存')
  await loadTaskList()
}

const toggleTask = async (row: any, action: 'enable' | 'disable') => {
  await message.confirm(`确认${action === 'enable' ? '启用' : '停用'}任务 ${row.taskName} 吗？`)
  if (action === 'enable') {
    await enableRehabTask({ id: row.id })
  } else {
    await disableRehabTask({ id: row.id })
  }
  message.success('操作成功')
  await Promise.all([loadTaskList(), loadCheckinList(), loadProgressList(), loadTriggerList()])
}

const checkinDialog = reactive({ visible: false, loading: false })
const checkinFormRef = ref()
const checkinForm = reactive<Record<string, any>>({
  checkinDate: dayjs().format('YYYY-MM-DD'),
  submitRoleType: 'therapist',
  overallCompletionRate: undefined,
  overallComment: '',
  taskExecutions: [] as any[]
})

const checkinRules = reactive({
  checkinDate: [{ required: true, message: '打卡日期不能为空', trigger: 'change' }],
  submitRoleType: [{ required: true, message: '提交角色不能为空', trigger: 'change' }]
})

const openCheckinDialog = () => {
  if (!checkinQuery.planId) {
    message.warning('请先选择计划')
    return
  }
  const enabledTasks = taskList.value.filter((item) => item.status !== 'disabled')
  if (!enabledTasks.length) {
    message.warning('当前计划无可执行任务')
    return
  }
  checkinForm.checkinDate = dayjs().format('YYYY-MM-DD')
  checkinForm.submitRoleType = 'therapist'
  checkinForm.overallCompletionRate = undefined
  checkinForm.overallComment = ''
  checkinForm.taskExecutions = enabledTasks.map((item) => ({
    taskId: item.id,
    taskName: item.taskName,
    completionStatus: 'completed',
    completedSets: item.sets || 0,
    completedReps: item.repetitions || 0,
    painScore: undefined,
    taskComment: ''
  }))
  checkinDialog.visible = true
}

const submitCheckin = async () => {
  await checkinFormRef.value.validate()
  const plan = getPlanById(checkinQuery.planId)
  if (!plan) {
    message.warning('未找到计划信息')
    return
  }
  checkinDialog.loading = true
  try {
    await createRehabCheckinManual({
      patientId: plan.patientId,
      episodeId: plan.episodeId,
      planId: plan.id,
      checkinDate: checkinForm.checkinDate,
      submitRoleType: checkinForm.submitRoleType,
      overallCompletionRate: checkinForm.overallCompletionRate,
      overallComment: checkinForm.overallComment,
      taskExecutions: checkinForm.taskExecutions.map((item: any) => ({
        taskId: item.taskId,
        completionStatus: item.completionStatus,
        completedSets: item.completedSets,
        completedReps: item.completedReps,
        painScore: item.painScore,
        taskComment: item.taskComment
      }))
    })
    message.success('代录打卡成功')
    checkinDialog.visible = false
    await Promise.all([loadCheckinList(), loadProgressList(), loadTriggerList()])
  } finally {
    checkinDialog.loading = false
  }
}

const executionDialog = reactive({ visible: false, items: [] as any[] })

const showExecutions = async (row: any) => {
  executionDialog.items = await getRehabCheckinTaskExecutions(row.id)
  executionDialog.visible = true
}

const recalculateProgress = async () => {
  if (!progressQuery.planId) {
    message.warning('请先选择计划')
    return
  }
  await recalculateRehabProgress({ planId: progressQuery.planId })
  message.success('进度重算成功')
  await Promise.all([loadProgressList(), loadTriggerList(), loadPlanList()])
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
  if (!triggerQuery.planId) {
    message.warning('请先选择计划')
    return
  }
  triggerForm.triggerType = 'time_due'
  triggerForm.triggerLevel = 'medium'
  triggerForm.triggerMessage = ''
  triggerForm.suggestedAction = ''
  triggerForm.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DD')
  triggerDialog.visible = true
}

const submitTrigger = async () => {
  await triggerFormRef.value.validate()
  const plan = getPlanById(triggerQuery.planId)
  if (!plan) {
    message.warning('未找到计划信息')
    return
  }
  triggerDialog.loading = true
  try {
    await createRehabTrigger({
      patientId: plan.patientId,
      episodeId: plan.episodeId,
      planId: plan.id,
      triggerType: triggerForm.triggerType,
      triggerLevel: triggerForm.triggerLevel,
      triggerMessage: triggerForm.triggerMessage,
      suggestedAction: triggerForm.suggestedAction,
      dueDate: triggerForm.dueDate
    })
    message.success('触发已创建')
    triggerDialog.visible = false
    await loadTriggerList()
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
      return
    }
  }
  await loadTriggerList()
}

const openAiDetail = (row: any) => {
  if (!AI_ENABLED) return
  currentAiOutput.value = row
  aiDetailVisible.value = true
}

const handleGenerateAiRisk = async () => {
  if (!AI_ENABLED) return
  const resp: RehabAiGenerateRespVO = await generateRiskExplanation({ patientId: id.value, asyncMode: false })
  message.success(resp.fallbackUsed ? 'AI 风险解释已降级生成' : 'AI 风险解释生成成功')
  await loadAiOutputs()
}

const handleGenerateAiFollowup = async () => {
  if (!AI_ENABLED) return
  const latestProgress = progressList.value[0]
  const latestTrigger = triggerList.value[0]
  const resp: RehabAiGenerateRespVO = await generateFollowupMessage({
    patientId: id.value,
    progressId: latestProgress?.id,
    triggerId: latestTrigger?.id,
    asyncMode: false
  })
  message.success(resp.fallbackUsed ? 'AI 随访文案已降级生成' : 'AI 随访文案生成成功')
  await loadAiOutputs()
}

const handleGenerateAiPlanDraft = async () => {
  if (!AI_ENABLED) return
  const activePlan = planList.value.find((item) => item.status === 'active') || planList.value[0]
  const latestAssessment = assessmentList.value[0]
  const resp: RehabAiGenerateRespVO = await generatePlanDraft({
    patientId: id.value,
    episodeId: activePlan?.episodeId || detail.value?.currentEpisode?.id,
    assessmentId: latestAssessment?.id,
    asyncMode: false
  })
  message.success(resp.fallbackUsed ? 'AI 计划草案已降级生成' : 'AI 计划草案生成成功')
  await loadAiOutputs()
}

const prettyJson = (value?: string) => {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

watch(
  () => taskQuery.planId,
  () => {
    loadTaskList()
  }
)

watch(
  () => checkinQuery.planId,
  () => {
    loadCheckinList()
  }
)

watch(
  () => progressQuery.planId,
  () => {
    loadProgressList()
  }
)

watch(
  () => triggerQuery.planId,
  () => {
    loadTriggerList()
  }
)

onMounted(() => {
  load()
})
</script>

<style scoped>
.preview-wrap {
  max-height: 70vh;
  overflow: auto;
  border: 1px solid #ebeef5;
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.ai-rendered {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  line-height: 1.7;
  white-space: pre-wrap;
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
