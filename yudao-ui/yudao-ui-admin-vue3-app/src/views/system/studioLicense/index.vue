<template>
  <div class="studio-license-page">
    <ContentWrap>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-xl font-semibold">工作室员工与许可证</h2>
          <p class="text-sm text-gray-500">许可额度只读，最终账号权限由服务端 RBAC 与所有者签名共同决定。</p>
        </div>
        <el-button :loading="loading" @click="refresh"><Icon icon="ep:refresh" />刷新状态</el-button>
      </div>
      <el-alert
        v-if="loadError" type="error" :closable="false" show-icon class="mt-3"
        title="许可证状态不可用" :description="loadError" />
      <el-alert
        v-else-if="license?.status === 'NOT_CONFIGURED'" type="warning" :closable="false" show-icon
        class="mt-3" title="开发环境尚未启用签名授权"
        description="当前界面不代表已获正式许可；不能在此自行设置席位数或签发许可证。" />
      <el-alert
        v-else-if="license?.status === 'GRACE'" type="warning" :closable="false" show-icon
        class="mt-3" title="离线签名许可短期有效"
        description="签名许可最多 72 小时且不能自动延期；请在到期前联系软件所有者。" />
      <el-row v-if="license && license.licenseEnforced" :gutter="16" class="mt-4">
        <el-col :xs="24" :sm="12" :md="6">
          <el-statistic title="启用员工席位" :value="license.activeStaff ?? 0" />
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-statistic
            v-if="license.seatLimit !== null && license.seatLimit !== undefined"
            title="授权员工席位" :value="license.seatLimit" />
          <div v-else><div class="text-gray-500">授权员工席位</div><strong class="text-xl">明确签名：不限</strong></div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-statistic
            v-if="license.availableSeats !== null && license.availableSeats !== undefined"
            title="剩余员工席位" :value="license.availableSeats" />
          <div v-else><div class="text-gray-500">剩余员工席位</div><strong class="text-xl">不限</strong></div>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-statistic title="允许部署实例" :value="license.maxInstallations ?? 0" />
        </el-col>
      </el-row>
      <el-descriptions v-if="license && license.licenseEnforced" :column="2" :border="true" class="mt-4">
        <el-descriptions-item label="许可状态">
          <el-tag :type="license.status === 'ACTIVE' ? 'success' : 'warning'">{{ license.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="有效截止">{{ license.expiresAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="租户标识">{{ license.tenantId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="实例标识">{{ license.installationId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="授权模块">{{ license.modules?.join('、') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="合同到期">{{ license.entitlementExpiresAt || '—' }}</el-descriptions-item>
      </el-descriptions>
      <p class="text-sm text-gray-500 mt-3">
        许可暂停、撤销、到期或服务端无法确认签名时，员工业务请求会被 Java 层拒绝。
        这里不提供任何更改许可证、授权签名或绕过限制的功能。
      </p>
    </ContentWrap>

    <ContentWrap>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h3 class="text-lg font-semibold">本工作室员工账号</h3>
        <div class="flex flex-wrap items-center gap-2">
          <el-button
            v-if="checkPermi(['system:user:create'])" type="primary"
            :disabled="!licenseActive || seatsExhausted" @click="openForm('create')">
            <Icon icon="ep:plus" />新增员工
          </el-button>
          <el-button @click="loadStaff" :loading="staffLoading">刷新员工</el-button>
        </div>
      </div>
      <el-alert
        v-if="seatsExhausted" type="warning" :closable="false" show-icon
        class="my-3" title="已达到签名许可证员工席位上限"
        description="可停用离职员工释放席位，或联系软件所有者审批扩容；界面不能自行增加席位。" />
      <el-form :inline="true" class="mt-4" @submit.prevent="loadStaff">
        <el-form-item label="账号名"><el-input v-model="query.username" clearable placeholder="用户名" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option label="启用" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="staff" v-loading="staffLoading" row-key="id" stripe>
        <el-table-column prop="username" label="员工用户名" min-width="130" />
        <el-table-column prop="nickname" label="姓名/昵称" min-width="120" />
        <el-table-column prop="deptId" label="部门编号" width="120" />
        <el-table-column label="状态" width="95">
          <template #default="{ row }"><el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag></template>
        </el-table-column>
        <el-table-column label="账号管理" min-width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="checkPermi(['system:user:update'])" link type="primary"
              :disabled="!licenseActive" @click="openForm('update', row.id)">编辑</el-button>
            <el-button
              v-if="checkPermi(['system:permission:assign-user-role'])" link type="primary"
              :disabled="!licenseActive" @click="roleFormRef?.open(row)">角色</el-button>
            <el-button
              v-if="checkPermi(['system:user:update-password'])" link type="primary"
              :disabled="!licenseActive" @click="resetPassword(row)">重置密码</el-button>
            <el-button
              v-if="checkPermi(['system:user:update'])" link
              :type="row.status === 0 ? 'warning' : 'success'"
              :disabled="!licenseActive || (row.status !== 0 && seatsExhausted)"
              @click="toggleStatus(row)">
              {{ row.status === 0 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total" v-model:page="query.pageNo"
        v-model:limit="query.pageSize" @pagination="loadStaff" />
      <p class="text-xs text-gray-500 mt-2">账号与角色修改沿用原生 RBAC 接口；即使按钮显示可用，后端仍在事务中检查工作室席位与租户权限。</p>
    </ContentWrap>

    <UserForm ref="userFormRef" @success="refresh" />
    <UserAssignRoleForm ref="roleFormRef" @success="loadStaff" />
  </div>
</template>

<script lang="ts" setup>
import { checkPermi } from '@/utils/permission'
import * as UserApi from '@/api/system/user'
import { getStudioLicenseOverview, type StudioLicenseOverview } from '@/api/system/studioLicense'
import UserForm from '@/views/system/user/UserForm.vue'
import UserAssignRoleForm from '@/views/system/user/UserAssignRoleForm.vue'

defineOptions({ name: 'StudioLicenseAndStaff' })
const message = useMessage()
const loading = ref(false)
const staffLoading = ref(false)
const loadError = ref('')
const license = ref<StudioLicenseOverview | null>(null)
const staff = ref<UserApi.UserVO[]>([])
const total = ref(0)
const userFormRef = ref<InstanceType<typeof UserForm>>()
const roleFormRef = ref<InstanceType<typeof UserAssignRoleForm>>()
const query = reactive<{ pageNo: number; pageSize: number; username?: string; status?: number }>({
  pageNo: 1, pageSize: 10, username: undefined, status: undefined
})
const licenseActive = computed(() =>
  license.value?.licenseEnforced === true &&
  (license.value.status === 'ACTIVE' || license.value.status === 'GRACE'))
const seatsExhausted = computed(() => licenseActive.value &&
  license.value?.seatLimit !== null && license.value?.seatLimit !== undefined &&
  (license.value?.availableSeats ?? 0) <= 0)

const loadLicense = async () => {
  loading.value = true
  loadError.value = ''
  try {
    license.value = await getStudioLicenseOverview()
  } catch {
    license.value = null
    loadError.value = '服务端无法验证当前租户签名或您无权查看许可。请联系工作室管理员，不要绕过授权。'
  } finally {
    loading.value = false
  }
}
const loadStaff = async () => {
  if (!checkPermi(['system:user:query'])) return
  staffLoading.value = true
  try {
    const page = await UserApi.getUserPage(query)
    staff.value = page.list
    total.value = page.total
  } catch {
    staff.value = []
    total.value = 0
    message.error('员工列表查询被拒绝或暂时不可用')
  } finally {
    staffLoading.value = false
  }
}
const refresh = async () => {
  await loadLicense()
  await loadStaff()
}
const openForm = (type: string, id?: number) => {
  if (!licenseActive.value || (type === 'create' && seatsExhausted.value)) return
  userFormRef.value?.open(type, id)
}
const toggleStatus = async (row: UserApi.UserVO) => {
  if (!licenseActive.value || (row.status !== 0 && seatsExhausted.value)) return
  const desired = row.status === 0 ? 1 : 0
  try {
    await message.confirm(`确认${desired === 1 ? '停用' : '重新启用'}员工“${row.username}”？`)
    await UserApi.updateUserStatus(row.id, desired)
    await refresh()
  } catch { /* cancelled or rejected by server licensing/RBAC */ }
}
const resetPassword = async (row: UserApi.UserVO) => {
  if (!licenseActive.value || !checkPermi(['system:user:update-password'])) return
  try {
    const result = await message.prompt(`请为员工“${row.username}”输入强随机临时密码（不得在聊天中发送）`, '重置员工密码')
    const password = String(result.value)
    if (password.length < 12 || password.length > 64) {
      message.error('新密码须为 12–64 个字符')
      return
    }
    await UserApi.resetUserPassword(row.id, password)
    message.success('密码已更新，请通过安全渠道通知员工并要求重新登录')
  } catch { /* User cancelled, or server rejected by RBAC/license */ }
}
onMounted(refresh)
</script>
