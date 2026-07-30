<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="90px">
      <el-form-item label="患者关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名/患者编号"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="患者ID" prop="patientId">
        <el-input-number v-model="queryParams.patientId" :min="1" controls-position="right" class="!w-150px" />
      </el-form-item>
      <el-form-item label="Episode" prop="episodeId">
        <el-input-number v-model="queryParams.episodeId" :min="1" controls-position="right" class="!w-150px" />
      </el-form-item>
      <el-form-item label="评估类型" prop="assessmentType">
        <el-select v-model="queryParams.assessmentType" clearable class="!w-150px">
          <el-option
            v-for="item in enabledAssessmentTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="评估日期" prop="assessmentDate">
        <el-date-picker
          v-model="queryParams.assessmentDate"
          value-format="YYYY-MM-DD"
          type="daterange"
          start-placeholder="开始"
          end-placeholder="结束"
          class="!w-250px"
        />
      </el-form-item>
      <el-form-item label="评估人" prop="assessorUserId">
        <el-select v-model="queryParams.assessorUserId" clearable filterable class="!w-170px">
          <el-option v-for="u in assessorOptions" :key="u.id" :label="`${u.nickname}(${u.id})`" :value="u.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-140px">
          <el-option label="draft" value="draft" />
          <el-option label="completed" value="completed" />
          <el-option label="reviewed" value="reviewed" />
          <el-option label="archived" value="archived" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:assessment:create']" @click="handleCreate">
          <Icon icon="ep:plus" /> 新建评估
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="assessment_no" prop="assessmentNo" min-width="160" />
      <el-table-column label="患者" min-width="150">
        <template #default="scope">{{ scope.row.patientName }} ({{ scope.row.patientNo }})</template>
      </el-table-column>
      <el-table-column label="episode_no" prop="episodeNo" min-width="150" />
      <el-table-column label="评估类型" min-width="140">
        <template #default="scope">
          {{ getAssessmentTypeLabel(scope.row.assessmentType) }}
        </template>
      </el-table-column>
      <el-table-column label="评估日期" prop="assessmentDate" min-width="120" :formatter="dateFormatter2" />
      <el-table-column label="评估人" prop="assessorName" min-width="120" />
      <el-table-column label="质量" prop="qualityGrade" min-width="80" />
      <el-table-column label="置信" prop="confidenceGrade" min-width="100" />
      <el-table-column label="状态" prop="status" min-width="100" />
      <el-table-column label="操作" fixed="right" min-width="280">
        <template #default="scope">
          <el-button type="primary" link v-hasPermi="['rehab:assessment:detail']" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:assessment:update']" @click="openEdit(scope.row.id)">
            编辑
          </el-button>
          <el-button
            type="primary"
            link
            v-hasPermi="['rehab:assessment:generate-report']"
            @click="handleGenerateReport(scope.row)"
          >
            生成报告
          </el-button>
          <el-button
            type="primary"
            link
            v-hasPermi="['rehab:assessment:archive']"
            @click="handleArchive(scope.row)"
          >
            归档
          </el-button>
          <el-button type="danger" link v-hasPermi="['rehab:assessment:delete']" @click="handleDelete(scope.row)">
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
import { dateFormatter2 } from '@/utils/formatTime'
import * as UserApi from '@/api/system/user'
import {
  archiveRehabAssessment,
  deleteRehabAssessment,
  getRehabAssessmentPage,
  RehabAssessmentPageReqVO
} from '@/api/rehab/assessment'
import { generateRehabReport } from '@/api/rehab/report'
import {
  ASSESSMENT_TYPE_OPTIONS,
  getAssessmentTypeLabel
} from '@/views/rehab/assessment/config/assessmentTypeOptions'

defineOptions({ name: 'RehabAssessment' })

const message = useMessage()
const { push } = useRouter()
const route = useRoute()

const loading = ref(false)
const total = ref(0)
const list = ref<any[]>([])
const assessorOptions = ref<UserApi.UserVO[]>([])
const enabledAssessmentTypeOptions = ASSESSMENT_TYPE_OPTIONS.filter((item) => item.enabled !== false)

const queryParams = reactive<RehabAssessmentPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  patientId: undefined,
  episodeId: undefined,
  keyword: undefined,
  assessmentType: undefined,
  assessmentDate: [],
  assessorUserId: undefined,
  status: undefined
})

const queryFormRef = ref()

const loadAssessorOptions = async () => {
  assessorOptions.value = await UserApi.getSimpleUserList()
}

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabAssessmentPage(queryParams)
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

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handleCreate = () => {
  const patientId = route.query.patientId as string | undefined
  push(patientId ? `/rehab/assessment/create?patientId=${patientId}` : '/rehab/assessment/create')
}

const openDetail = (id: number) => {
  push(`/rehab/assessment/detail/${id}`)
}

const openEdit = (id: number) => {
  push(`/rehab/assessment/edit/${id}`)
}

const handleGenerateReport = async (row: any) => {
  await message.confirm(`确认为评估 ${row.assessmentNo} 生成报告吗？`)
  await generateRehabReport({ assessmentId: row.id })
  message.success('已生成报告')
}

const handleArchive = async (row: any) => {
  await message.confirm(`确认归档评估 ${row.assessmentNo} 吗？`)
  await archiveRehabAssessment({ id: row.id })
  message.success('归档成功')
  await getList()
}

const handleDelete = async (row: any) => {
  await message.confirm(`确认删除评估 ${row.assessmentNo} 吗？`)
  await deleteRehabAssessment(row.id)
  message.success('删除成功')
  await getList()
}

onMounted(async () => {
  const patientId = Number(route.query.patientId)
  if (patientId) {
    queryParams.patientId = patientId
  }
  await Promise.all([loadAssessorOptions(), getList()])
})
</script>
