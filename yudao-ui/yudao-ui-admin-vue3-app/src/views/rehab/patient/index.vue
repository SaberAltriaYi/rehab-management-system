<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px" label-width="80px">
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名/手机号/患者编号"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="治疗师" prop="currentTherapistUserId">
        <el-select v-model="queryParams.currentTherapistUserId" clearable class="!w-180px" filterable>
          <el-option
            v-for="u in therapistOptions"
            :key="u.id"
            :label="`${u.nickname}(${u.id})`"
            :value="u.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="当前阶段" prop="currentStage">
        <el-select v-model="queryParams.currentStage" clearable class="!w-160px">
          <el-option v-for="stage in stageOptions" :key="stage" :label="stage" :value="stage" />
        </el-select>
      </el-form-item>
      <el-form-item label="CRM状态" prop="crmBindStatus">
        <el-select v-model="queryParams.crmBindStatus" clearable class="!w-160px">
          <el-option
            v-for="item in crmBindStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="性别" prop="gender">
        <el-select v-model="queryParams.gender" clearable class="!w-120px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.SYSTEM_USER_SEX)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源" prop="sourceChannel">
        <el-select v-model="queryParams.sourceChannel" clearable class="!w-120px">
          <el-option label="门诊" value="门诊" />
          <el-option label="转介绍" value="转介绍" />
          <el-option label="线上" value="线上" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="datetimerange"
          start-placeholder="开始"
          end-placeholder="结束"
          class="!w-260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['rehab:patient:create']" @click="openForm('create')">
          <Icon icon="ep:plus" /> 新建患者
        </el-button>
        <el-button type="warning" plain v-hasPermi="['rehab:patient:create']" @click="openImport">
          <Icon icon="ep:upload" /> 批量导入
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          v-hasPermi="['rehab:patient:export']"
          @click="handleExport"
        >
          <Icon icon="ep:download" /> 批量导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="患者编号" prop="patientNo" min-width="150" />
      <el-table-column label="姓名" prop="name" min-width="110" />
      <el-table-column label="性别" prop="gender" min-width="80">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.SYSTEM_USER_SEX" :value="scope.row.gender" />
        </template>
      </el-table-column>
      <el-table-column label="年龄" prop="age" min-width="70" />
      <el-table-column label="手机号" prop="phone" min-width="130" />
      <el-table-column label="当前阶段" prop="currentStage" min-width="120" />
      <el-table-column label="主责治疗师" prop="currentTherapistName" min-width="130" />
      <el-table-column label="CRM 绑定" min-width="110">
        <template #default="scope">
          {{ getCrmBindStatusLabel(scope.row.crmBindStatus) }}
        </template>
      </el-table-column>
      <el-table-column
        label="更新时间"
        prop="updateTime"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" fixed="right" min-width="380">
        <template #default="scope">
          <el-button type="primary" link v-hasPermi="['rehab:patient:detail']" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:episode:view']" @click="openDetail(scope.row.id)">
            查看 Episode
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:patient:update']" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:patient:assign']" @click="openAssign(scope.row)">
            分配
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:patient:transfer']" @click="openTransfer(scope.row)">
            转交
          </el-button>
          <el-button type="primary" link v-hasPermi="['rehab:patient:bind-crm']" @click="openBindCrm(scope.row)">
            CRM 绑定
          </el-button>
          <el-button type="danger" link v-hasPermi="['rehab:patient:delete']" @click="handleDelete(scope.row.id)">
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

  <PatientForm ref="patientFormRef" @success="getList" />
  <PatientImportDialog ref="patientImportDialogRef" @success="getList" />
  <AssignTherapistDialog ref="assignDialogRef" @success="getList" />
  <BindCrmDialog ref="bindCrmDialogRef" @success="getList" />
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import {
  deleteRehabPatient,
  exportRehabPatient,
  getRehabPatientPage,
  RehabPatientPageReqVO,
  RehabPatientVO
} from '@/api/rehab/patient'
import * as UserApi from '@/api/system/user'
import PatientForm from './PatientForm.vue'
import PatientImportDialog from './PatientImportDialog.vue'
import AssignTherapistDialog from './AssignTherapistDialog.vue'
import BindCrmDialog from './BindCrmDialog.vue'

defineOptions({ name: 'RehabPatient' })

const message = useMessage()
const { push } = useRouter()

const loading = ref(false)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<RehabPatientVO[]>([])
const therapistOptions = ref<UserApi.UserVO[]>([])
const stageOptions = [
  '初诊建档',
  '待评估',
  '评估中',
  '执行中',
  '复评中',
  '已结案',
  '已暂停',
  '已转诊'
]
const crmBindStatusOptions = [
  { label: '已绑定', value: 'bound' },
  { label: '未绑定', value: 'unbound' },
  { label: '信息冲突', value: 'conflict' },
  { label: '等待同步', value: 'pending_sync' }
]
const getCrmBindStatusLabel = (value?: string) =>
  crmBindStatusOptions.find((item) => item.value === value)?.label || value || '-'

const queryParams = reactive<RehabPatientPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  currentTherapistUserId: undefined,
  currentStage: undefined,
  crmBindStatus: undefined,
  gender: undefined,
  sourceChannel: undefined,
  createTime: []
})

const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await getRehabPatientPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const loadTherapistOptions = async () => {
  therapistOptions.value = await UserApi.getSimpleUserList()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const patientFormRef = ref()
const openForm = (type: 'create' | 'update', id?: number) => {
  patientFormRef.value.open(type, id)
}

const patientImportDialogRef = ref()
const openImport = () => patientImportDialogRef.value?.open()

const openDetail = (id: number) => {
  push(`/rehab/patient/detail/${id}`)
}

const assignDialogRef = ref()
const openAssign = (row: RehabPatientVO) => {
  assignDialogRef.value.open(
    {
      patientId: row.id,
      patientName: row.name,
      fromTherapistUserId: row.currentTherapistUserId,
      fromTherapistName: row.currentTherapistName
    },
    'assign'
  )
}

const openTransfer = (row: RehabPatientVO) => {
  assignDialogRef.value.open(
    {
      patientId: row.id,
      patientName: row.name,
      fromTherapistUserId: row.currentTherapistUserId,
      fromTherapistName: row.currentTherapistName
    },
    'transfer'
  )
}

const bindCrmDialogRef = ref()
const openBindCrm = (row: RehabPatientVO) => {
  bindCrmDialogRef.value.open({
    patientId: row.id,
    patientName: row.name
  })
}

const handleDelete = async (id: number) => {
  await message.confirm('确认删除该患者档案吗？')
  await deleteRehabPatient(id)
  message.success('删除成功')
  await getList()
}

const handleExport = async () => {
  try {
    await message.confirm('确认导出当前查询结果吗？')
    exportLoading.value = true
    const data = await exportRehabPatient(queryParams)
    download.excel(data, '康复患者.xlsx')
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTherapistOptions(), getList()])
})
</script>
