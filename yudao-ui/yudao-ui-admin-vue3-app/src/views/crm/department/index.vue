<template>
  <ContentWrap>
    <h2>部门客户工作台</h2>
    <el-alert
      title="共享目录只显示客户名称和认领状态；部门备注只在本部门可见，转接后不自动共享。"
      type="info"
      :closable="false"
      class="mb-4"
    />
    <el-tabs v-model="activeTab" @tab-change="loadTab">
      <el-tab-pane label="共享客户目录" name="directory" />
      <el-tab-pane label="本部门客户" name="cases" />
      <el-tab-pane label="待接收转接" name="incoming" />
      <el-tab-pane label="部门工作台设置" name="settings" />
    </el-tabs>

    <template v-if="activeTab === 'directory'">
      <el-form inline @submit.prevent="searchDirectory">
        <el-form-item label="客户名称">
          <el-input v-model="directoryQuery.name" clearable placeholder="搜索共享客户" />
        </el-form-item>
        <el-button type="primary" @click="searchDirectory">查询</el-button>
      </el-form>
      <el-table v-loading="loading" :data="directoryRows" stripe>
        <el-table-column prop="name" label="客户名称" min-width="180" />
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.claimedDeptId ? 'warning' : 'success'">
              {{ row.claimedDeptId ? '已认领' : '待认领' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前部门" min-width="180">
          <template #default="{ row }">{{ deptName(row.claimedDeptId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button
              v-if="!row.claimedDeptId"
              v-hasPermi="['crm:customer:create']"
              link
              type="primary"
              @click="claimCustomer(row)"
            >认领至本部门</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="directoryQuery.pageNo"
        v-model:limit="directoryQuery.pageSize"
        :total="directoryTotal"
        @pagination="loadDirectory"
      />
    </template>

    <template v-if="activeTab === 'cases'">
      <el-table v-loading="loading" :data="caseRows" stripe>
        <el-table-column prop="customerName" label="客户" min-width="160" />
        <el-table-column prop="claimTime" label="认领时间" min-width="170" />
        <el-table-column label="部门记录" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.privateNote || '暂无' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCustomer(row)">客户详情</el-button>
            <el-button v-hasPermi="['crm:customer:update']" link @click="editNote(row)">部门记录</el-button>
            <el-button v-hasPermi="['crm:customer:update']" link type="warning" @click="transfer(row)">转接</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="caseQuery.pageNo"
        v-model:limit="caseQuery.pageSize"
        :total="caseTotal"
        @pagination="loadCases"
      />
    </template>

    <el-table v-if="activeTab === 'incoming'" v-loading="loading" :data="incomingRows" stripe>
      <el-table-column prop="customerName" label="客户" min-width="160" />
      <el-table-column label="来源部门" min-width="160">
        <template #default="{ row }">{{ deptName(row.fromDeptId) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="转接说明" min-width="200" />
      <el-table-column prop="createTime" label="申请时间" min-width="170" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-hasPermi="['crm:customer:update']" link type="primary" @click="resolve(row, true)">接收</el-button>
          <el-button v-hasPermi="['crm:customer:update']" link type="danger" @click="resolve(row, false)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="activeTab === 'settings'">
      <el-alert title="先在系统管理中建立部门，再由有部门管理权限的账号启用相应工作台。" type="info" :closable="false" />
      <el-table :data="allDepts" class="mt-4" stripe>
        <el-table-column prop="name" label="部门" min-width="180" />
        <el-table-column label="工作台名称" min-width="180">
          <template #default="{ row }">{{ workspaceFor(row.id)?.displayName || '未启用' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ workspaceFor(row.id)?.enabled ? '已启用' : '未启用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-hasPermi="['system:dept:update']" link type="primary" @click="configure(row)">配置</el-button>
            <el-button
              v-if="workspaceFor(row.id)?.enabled"
              v-hasPermi="['system:dept:update']"
              link type="danger"
              @click="disableWorkspace(row)"
            >停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
    <el-dialog v-model="transferVisible" title="转接部门客户" width="480px">
      <el-form label-width="100px">
        <el-form-item label="客户">{{ transferCase?.customerName }}</el-form-item>
        <el-form-item label="目标部门" required>
          <el-select v-model="transferTargetDeptId" placeholder="选择已启用的部门" class="w-full">
            <el-option
              v-for="workspace in availableTransferWorkspaces"
              :key="workspace.deptId"
              :label="workspace.displayName"
              :value="workspace.deptId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转接说明">
          <el-input
            v-model="transferReason"
            type="textarea"
            :maxlength="200"
            show-word-limit
            placeholder="简要说明业务交接；勿填写患者敏感记录"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!transferTargetDeptId" @click="submitTransfer">提交转接</el-button>
      </template>
    </el-dialog>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import * as DepartmentApi from '@/api/crm/department'
import { getSimpleDeptList, type DeptVO } from '@/api/system/dept'

defineOptions({ name: 'CrmDepartment' })
const message = useMessage()
const { push } = useRouter()
const activeTab = ref('directory')
const loading = ref(false)
const workspaces = ref<DepartmentApi.Workspace[]>([])
const allDepts = ref<DeptVO[]>([])
const directoryQuery = reactive({ name: '', pageNo: 1, pageSize: 20 })
const directoryRows = ref<DepartmentApi.DirectoryItem[]>([])
const directoryTotal = ref(0)
const caseQuery = reactive({ pageNo: 1, pageSize: 20 })
const caseRows = ref<DepartmentApi.DepartmentCase[]>([])
const caseTotal = ref(0)
const incomingRows = ref<DepartmentApi.TransferItem[]>([])
const transferVisible = ref(false)
const transferCase = ref<DepartmentApi.DepartmentCase | null>(null)
const transferTargetDeptId = ref<number>()
const transferReason = ref('')
const availableTransferWorkspaces = computed(() => workspaces.value.filter((item) =>
  item.enabled && item.deptId !== transferCase.value?.deptId))

const workspaceFor = (id: number) => workspaces.value.find((item) => item.deptId === id)
const deptName = (id: number | null) => {
  if (!id) return '—'
  return workspaces.value.find((item) => item.deptId === id)?.displayName ||
    allDepts.value.find((item) => item.id === id)?.name || `部门 ${id}`
}
const loadDirectory = async () => {
  loading.value = true
  try {
    const data = await DepartmentApi.getDirectory(directoryQuery)
    directoryRows.value = data.list
    directoryTotal.value = data.total
  } finally { loading.value = false }
}
const loadCases = async () => {
  loading.value = true
  try {
    const data = await DepartmentApi.getCases(caseQuery)
    caseRows.value = data.list
    caseTotal.value = data.total
  } finally { loading.value = false }
}
const loadIncoming = async () => {
  loading.value = true
  try { incomingRows.value = await DepartmentApi.getIncoming() }
  finally { loading.value = false }
}
const loadTab = async () => {
  if (activeTab.value === 'directory') await loadDirectory()
  if (activeTab.value === 'cases') await loadCases()
  if (activeTab.value === 'incoming') await loadIncoming()
  if (activeTab.value === 'settings') {
    workspaces.value = await DepartmentApi.getWorkspaces()
    allDepts.value = await getSimpleDeptList()
  }
}
const searchDirectory = () => { directoryQuery.pageNo = 1; loadDirectory() }
const claimCustomer = async (row: DepartmentApi.DirectoryItem) => {
  await ElMessageBox.confirm(`将「${row.name}」认领至当前部门？`, '确认认领', { type: 'warning' })
  await DepartmentApi.claim(row.customerId)
  message.success('认领成功')
  await loadDirectory()
}
const openCustomer = (row: DepartmentApi.DepartmentCase) => {
  push({ name: 'CrmCustomerDetail', params: { id: row.customerId } })
}
const editNote = async (row: DepartmentApi.DepartmentCase) => {
  const result = await ElMessageBox.prompt('部门私有记录不会随转接自动提供给其他部门。', '编辑部门记录', {
    inputType: 'textarea', inputValue: row.privateNote || '', inputValidator: (value) =>
      value.length <= 4000 || '最多 4000 字'
  })
  await DepartmentApi.saveNote(row.id, result.value)
  message.success('已保存')
  await loadCases()
}
const transfer = async (row: DepartmentApi.DepartmentCase) => {
  workspaces.value = await DepartmentApi.getWorkspaces()
  transferCase.value = row
  transferTargetDeptId.value = undefined
  transferReason.value = ''
  transferVisible.value = true
}
const submitTransfer = async () => {
  if (!transferCase.value || !transferTargetDeptId.value) return
  await DepartmentApi.requestTransfer(transferCase.value.id, transferTargetDeptId.value, transferReason.value)
  transferVisible.value = false
  message.success('转接申请已发送，等待目标部门接收')
}
const resolve = async (row: DepartmentApi.TransferItem, accept: boolean) => {
  await ElMessageBox.confirm(`${accept ? '接收' : '拒绝'}「${row.customerName}」的转接？`, '确认操作', { type: 'warning' })
  await DepartmentApi.resolveTransfer(row.id, accept)
  message.success('已处理')
  await loadIncoming()
}
const configure = async (row: DeptVO) => {
  const current = workspaceFor(row.id)
  const result = await ElMessageBox.prompt('设置工作台名称；确定后将启用，取消后保持原配置。', '配置部门工作台', {
    inputValue: current?.displayName || `${row.name}客户工作台`,
    inputValidator: (value) => (!!value.trim() && value.length <= 100) || '请输入 1-100 字'
  })
  await DepartmentApi.configureWorkspace({ deptId: row.id, displayName: result.value.trim(), enabled: true })
  workspaces.value = await DepartmentApi.getWorkspaces()
  message.success('已启用部门工作台')
}
const disableWorkspace = async (row: DeptVO) => {
  const current = workspaceFor(row.id)
  if (!current) return
  await ElMessageBox.confirm(`停用「${current.displayName}」？现有客户和记录会保留，但暂停认领与接收转接。`, '确认停用', { type: 'warning' })
  await DepartmentApi.configureWorkspace({ deptId: row.id, displayName: current.displayName, enabled: false })
  workspaces.value = await DepartmentApi.getWorkspaces()
  message.success('工作台已停用，数据仍保留')
}
onMounted(async () => {
  workspaces.value = await DepartmentApi.getWorkspaces()
  allDepts.value = await getSimpleDeptList()
  await loadDirectory()
})
</script>
