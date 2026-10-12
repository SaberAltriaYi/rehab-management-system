<template>
  <div class="platform" v-loading="busy">
    <header><div><small>REHAB SAAS · 平台管理</small><h1>机构与订阅</h1><p>平台权限由服务器核验，机构管理员不自动拥有平台权限。</p></div><el-button @click="load">刷新</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs v-if="ready">
      <el-tab-pane label="入驻申请">
        <el-table :data="registrations"><el-table-column prop="id" label="编号" width="80"/><el-table-column prop="organization_name" label="机构"/><el-table-column prop="contact_name" label="联系人"/><el-table-column prop="contact_email" label="邮箱"/><el-table-column prop="status" label="状态"/><el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.status === 'pending'" link type="primary" @click="select(row)">审核开通</el-button><el-button v-if="row.status === 'pending'" link type="danger" @click="rejectRequest(row.id)">拒绝</el-button></template></el-table-column></el-table>
        <el-button v-if="registrations.length === 100" @click="nextApplications">下一页</el-button>
      </el-tab-pane>
      <el-tab-pane label="机构与用量"><el-table :data="tenants"><el-table-column prop="name" label="机构"/><el-table-column prop="registration_status" label="机构状态"/><el-table-column prop="subscription_status" label="订阅状态"/><el-table-column prop="user_count" label="账号数"/><el-table-column prop="patient_count" label="患者数"/><el-table-column prop="motion_count" label="评估用量"/><el-table-column prop="storage_bytes" label="存储字节"/><el-table-column label="操作"><template #default="{ row }"><el-button link type="danger" :disabled="row.registration_status !== 'active'" @click="suspendTenant(row.id)">暂停机构</el-button></template></el-table-column></el-table><el-button v-if="tenants.length === 100" @click="nextTenants">下一页</el-button><el-alert title="用量计数尚需完成全部业务写入接入；当前显示值不能作为计费依据。" type="warning" :closable="false"/></el-tab-pane>
      <el-tab-pane label="套餐"><el-table :data="plans"><el-table-column prop="name" label="套餐"/><el-table-column prop="max_users" label="账号上限"/><el-table-column prop="max_patients" label="患者上限"/><el-table-column prop="motion_quota" label="评估上限"/><el-table-column prop="storage_quota" label="存储字节上限"/></el-table></el-tab-pane>
    </el-tabs>
    <el-dialog v-model="dialog" title="审核并创建机构管理员" width="min(520px, 95vw)" @closed="approval.password = ''">
      <el-form label-position="top" @submit.prevent="submitApproval">
        <el-form-item label="订阅套餐"><el-select v-model="approval.planId"><el-option v-for="plan in plans" :key="plan.id" :label="plan.name" :value="plan.id"/></el-select></el-form-item>
        <el-form-item label="权限套餐编号（不可使用系统套餐 0）"><el-input-number v-model="approval.packageId" :min="1"/></el-form-item>
        <el-form-item label="管理员账号（4–30 位字母或数字）"><el-input v-model="approval.username" autocomplete="off" maxlength="30"/></el-form-item>
        <el-form-item label="初始密码（12–16 位，通过安全渠道交付）"><el-input v-model="approval.password" type="password" autocomplete="new-password" maxlength="16" show-password/></el-form-item>
        <el-form-item label="订阅到期时间"><el-date-picker v-model="approval.expires" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy">确认开通</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/system/saas'
const registrations = ref<api.Registration[]>([]), tenants = ref<api.Tenant[]>([]), plans = ref<api.Plan[]>([])
const busy = ref(false), ready = ref(false), dialog = ref(false), error = ref('')
const approval = reactive<api.Approval>({ registrationId: 0, planId: 0, packageId: 1, username: '', password: '', expires: '' })
async function load() {
  busy.value = true; ready.value = false; error.value = ''
  registrations.value = []; tenants.value = []; plans.value = []
  try { [registrations.value, tenants.value, plans.value] = await Promise.all([api.getRegistrations(), api.getTenants(), api.getPlans()]); ready.value = true }
  catch { error.value = '无法加载：请确认平台管理员权限、SaaS 部署模式和网络连接。' }
  finally { busy.value = false }
}
function select(row: api.Registration) { Object.assign(approval, { registrationId: row.id, planId: plans.value[0]?.id ?? 0, username: '', password: '', expires: '' }); dialog.value = true }
async function submitApproval() {
  if (!approval.planId || !approval.expires || !/^[a-zA-Z0-9]{4,30}$/.test(approval.username) || approval.password.length < 12) { ElMessage.warning('请填写完整的套餐、账号、密码与到期时间'); return }
  busy.value = true
  try { const id = await api.approve({ ...approval }); dialog.value = false; ElMessage.success(`已创建机构 ${id}`); await load() }
  catch { ElMessage.error('开通未完成，请刷新确认申请状态后重试') }
  finally { approval.password = ''; busy.value = false }
}
async function rejectRequest(id: number) { try { await ElMessageBox.confirm('确认拒绝此入驻申请？', '审核'); await api.reject(id); await load() } catch { /* cancellation or API error is handled by the shared interceptor */ } }
async function suspendTenant(id: number) { try { await ElMessageBox.confirm('暂停后机构将无法继续访问业务数据，确认？', '暂停机构'); await api.suspend(id); await load() } catch { /* same as above */ } }
async function nextApplications() { try { registrations.value = await api.getRegistrations(registrations.value[registrations.value.length - 1].id) } catch { error.value = '申请列表加载失败' } }
async function nextTenants() { try { tenants.value = await api.getTenants(tenants.value[tenants.value.length - 1].id) } catch { error.value = '机构列表加载失败' } }
onMounted(load)
</script>
<style scoped>
.platform{padding:28px;background:#fff;border-radius:14px}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}small{color:#408679;letter-spacing:2px}h1{margin:10px 0;font-size:28px}p{color:#72857e}.el-alert{margin:18px 0}.el-select{width:100%}
</style>
