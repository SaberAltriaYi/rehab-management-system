<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/config/axios'

type EventRow = {
  event_id: string
  event_type: string
  episode_id: number
  session_id: string
  review_status: string
  created_at: string
}
const hepAdminUrl = 'http://127.0.0.1:5188/'
const hepTherapistUrl = 'http://127.0.0.1:5192/'
const pending = ref(false)
const patientId = ref('')
const events = ref<EventRow[]>([])
const error = ref('')
const route = useRoute()

async function openHep(role: 'admin' | 'therapist') {
  error.value = ''
  const hepUrl = role === 'admin' ? hepAdminUrl : hepTherapistUrl
  const hepOrigin = new URL(hepUrl).origin
  const popup = window.open(hepUrl, '_blank')
  if (!popup) {
    error.value = '浏览器阻止打开 HEP，请允许弹出窗口。'
    return
  }
  let ready = false
  let token = ''
  let sent = false
  let cleanup = () => {}
  const deliver = () => {
    if (sent || !ready || !token || popup.closed) return
    sent = true
    popup.postMessage({ kind: 'HEP_CORE_TOKEN', token }, hepOrigin)
    cleanup()
  }
  const listener = (event: MessageEvent) => {
    if (event.source !== popup || event.origin !== hepOrigin || event.data?.kind !== 'HEP_READY') return
    ready = true
    deliver()
  }
  window.addEventListener('message', listener)
  const timeout = window.setTimeout(() => {
    cleanup()
    if (!sent) error.value = 'HEP 页面未完成安全握手，请检查服务端口。'
  }, 20000)
  cleanup = () => {
    window.removeEventListener('message', listener)
    window.clearTimeout(timeout)
  }
  pending.value = true
  try {
    const response = await request.post({ url: role === 'admin'
      ? '/rehab/hep-auth/admin-token' : '/rehab/hep-auth/therapist-token' }) as { accessToken?: string }
    if (!response?.accessToken) throw Error('Core 尚未授予当前账号对应的 HEP 权限')
    token = response.accessToken
    deliver()
  } catch (cause) {
    cleanup()
    popup.close()
    error.value = cause instanceof Error ? cause.message : '当前账号没有所需 HEP 权限'
  } finally {
    pending.value = false
  }
}

async function loadEvents() {
  error.value = ''
  const id = patientId.value.trim()
  if (!/^[1-9]\d{0,15}$/.test(id)) {
    error.value = '请输入合法的 Core 患者数字 ID'
    return
  }
  try {
    events.value = await request.get({ url: '/rehab/hep-bridge/patients/' + id + '/events' }) as EventRow[]
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '没有查看该患者 HEP 事件的权限'
  }
}
async function review(row: EventRow) {
  if (row.review_status !== 'PENDING') return
  try {
    await request.post({ url: '/rehab/hep-bridge/patients/' + patientId.value.trim() + '/events/' +
      encodeURIComponent(row.event_id) + '/review' })
    await loadEvents()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '人工复核失败'
  }
}
onMounted(() => {
  const id = route.query.patientId
  if (typeof id === 'string' && /^[1-9]\d{0,15}$/.test(id)) {
    patientId.value = id
    void loadEvents()
  }
})
</script>

<template>
  <ContentWrap>
    <h2>HEP 家庭康复联动</h2>
    <p>Rehab Core 管理患者、疗程和治疗师权限；HEP 独立保存训练处方、执行和患者反馈。</p>
    <el-button type="primary" :loading="pending" @click="openHep('therapist')">进入治疗师 HEP 工作台</el-button>
    <el-button :loading="pending" @click="openHep('admin')">进入 HEP 管理台</el-button>
    <p class="hint">治疗师需具备 rehab:hep:prescription:create，管理员需具备 rehab:hep:integration:admin。短期访问令牌经浏览器来源校验传递，不出现在 URL 中。</p>
    <el-divider />
    <h3>患者训练与风险回传</h3>
    <el-input v-model="patientId" style="max-width: 320px" placeholder="Core 患者数字 ID" />
    <el-button class="ml-2" @click="loadEvents">查看已回传事件</el-button>
    <el-table :data="events" style="width: 100%; margin-top: 16px">
      <el-table-column prop="event_type" label="事件类型" min-width="165" />
      <el-table-column prop="episode_id" label="疗程" width="95" />
      <el-table-column prop="session_id" label="训练会话" min-width="190" />
      <el-table-column prop="review_status" label="人工复核" width="120" />
      <el-table-column label="操作" width="125">
        <template #default="{ row }">
          <el-button v-if="row.review_status === 'PENDING'" type="primary" link @click="review(row)">
            标记已复核
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <p class="hint">这些是训练执行和风险事件，不等于医学诊断；复评需由治疗师通过主系统现有流程创建。</p>
    <el-alert v-if="error" type="warning" :title="error" show-icon :closable="false" />
  </ContentWrap>
</template>

<style scoped>
.hint { color: #687787; margin: 14px 0; font-size: 13px; }
</style>
