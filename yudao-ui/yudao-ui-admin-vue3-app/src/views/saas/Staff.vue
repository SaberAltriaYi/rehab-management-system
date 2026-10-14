<template>
  <section class="staff" v-loading="busy">
    <header><div><small>REHAB SAAS · 开发版</small><h1>治疗师邀请</h1><p>仅本机构管理员可操作。邀请有效期 72 小时，仅能使用一次。</p></div><el-button @click="load(0)">刷新</el-button></header>
    <el-alert title="首次使用前，请在角色管理中配置 rehab_therapist 角色及康复业务权限。邀请凭据不会发送邮件，请通过可信渠道交付本人。" type="info" :closable="false" />
    <el-form inline @submit.prevent="issue"><el-form-item label="治疗师邮箱"><el-input v-model="email" type="email" maxlength="50" placeholder="name@example.com" /></el-form-item><el-button native-type="submit" type="primary">创建邀请</el-button></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="rows"><el-table-column prop="recipient_email" label="接收人" /><el-table-column prop="status" label="状态" /><el-table-column prop="expires_at" label="到期时间" /><el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.status === 'pending'" link type="danger" @click="revoke(row.id)">撤销</el-button></template></el-table-column></el-table>
    <el-button v-if="rows.length === 100" @click="load(rows[rows.length - 1].id)">下一页</el-button>
    <el-dialog v-model="showToken" title="邀请凭据 · 仅显示一次" @closed="invitation = ''">
      <el-alert title="关闭后无法再次查看凭据。请勿转发给非接收人或放入公开群聊。" type="warning" :closable="false" />
      <el-input :model-value="invitation" type="textarea" :rows="5" readonly />
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/system/saas'
const rows = ref<api.StaffInvitation[]>([]), email = ref(''), busy = ref(false), error = ref(''), invitation = ref(''), showToken = ref(false)
async function load(afterId = 0) {
  busy.value = true; error.value = ''
  try { rows.value = await api.getStaffInvitations(afterId) }
  catch { rows.value = []; error.value = '无法读取邀请：请检查本机构管理员权限、订阅状态和网络。' }
  finally { busy.value = false }
}
async function issue() {
  busy.value = true
  try { const issued = await api.issueStaffInvitation(email.value); invitation.value = `接受邀请页面：${location.origin}/saas/join
机构编号：${issued.tenantId}
邀请凭据：${issued.token}
有效期：72 小时`; showToken.value = true; await load() }
  catch { ElMessage.error('邀请创建失败，请确认邮箱格式与治疗师角色配置') }
  finally { busy.value = false }
}
async function revoke(id: number) { try { await ElMessageBox.confirm('撤销后该凭据立即失效，确认？', '撤销邀请'); await api.revokeStaffInvitation(id); await load() } catch { /* cancellation or shared API error */ } }
onMounted(() => load())
onUnmounted(() => { invitation.value = '' })
</script>
<style scoped>.staff{padding:28px;background:white;border-radius:14px}header{display:flex;justify-content:space-between;align-items:center}.el-alert,.el-form{margin:20px 0}small{color:#408679}p{color:#667b75}</style>
