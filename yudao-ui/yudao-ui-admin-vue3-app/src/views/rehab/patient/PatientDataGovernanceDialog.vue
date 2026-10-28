<template>
  <el-dialog
    v-model="visible"
    title="模拟资料治理"
    width="520px"
    style="max-width: 95vw"
    :close-on-click-modal="false"
  >
    <el-alert
      type="warning"
      :closable="false"
      title="仅用于已核实完全虚构的测试资料；不删除记录，也不改治疗阶段。标记模拟后不可改回业务数据。"
      show-icon
    />
    <p
      >患者编号：<strong>{{ patient?.patientNo }}</strong></p
    >
    <p
      >{{
        operationLabel
      }}；已有正式报告原件不会被覆盖。若来源变化，旧报告需创建带模拟标识的新修订才能继续导出。</p
    >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="逐字输入患者编号以确认">
        <el-input v-model="confirmation" maxlength="64" autocomplete="off" />
      </el-form-item>
      <el-form-item label="操作依据（5–256 字，请勿填写病情或联系方式）">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          minlength="5"
          maxlength="256"
          show-word-limit
        />
      </el-form-item>
      <el-checkbox v-model="acknowledged"
        >我已核对对象及虚构资料来源，并了解此次操作影响统计范围。</el-checkbox
      >
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">取消</el-button>
      <el-button type="warning" :disabled="!canSubmit" :loading="busy" @click="submit">{{
        operationLabel
      }}</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
import { computed, ref } from 'vue'
import {
  changeRehabPatientDataOrigin,
  type RehabPatientVO,
  type RehabPatientDataGovernanceRequest
} from '@/api/rehab/patient'

const emit = defineEmits<{ (event: 'success'): void }>()
const message = useMessage()
const visible = ref(false)
const busy = ref(false)
const patient = ref<RehabPatientVO>()
const confirmation = ref('')
const reason = ref('')
const acknowledged = ref(false)
const operation = ref<RehabPatientDataGovernanceRequest['operation']>('MARK_SYNTHETIC')
const operationLabel = computed(
  () =>
    ({
      MARK_SYNTHETIC: '确认标记模拟',
      ARCHIVE_SYNTHETIC: '归档模拟资料',
      RESTORE_SYNTHETIC: '恢复模拟资料'
    })[operation.value]
)
const canSubmit = computed(
  () =>
    !busy.value &&
    acknowledged.value &&
    !!patient.value?.patientNo &&
    confirmation.value.trim() === patient.value.patientNo &&
    reason.value.trim().length >= 5
)

function open(row: RehabPatientVO) {
  if (!row.dataOrigin) {
    message.warning('当前后端尚未提供资料来源，请先完成受控升级，不能推断或修改。')
    return
  }
  patient.value = { ...row }
  operation.value =
    row.dataOrigin === 'REAL'
      ? 'MARK_SYNTHETIC'
      : row.dataOrigin === 'SYNTHETIC'
        ? 'ARCHIVE_SYNTHETIC'
        : 'RESTORE_SYNTHETIC'
  confirmation.value = ''
  reason.value = ''
  acknowledged.value = false
  visible.value = true
}

async function submit() {
  if (!canSubmit.value || !patient.value?.dataOrigin) return
  await message.confirm(
    `确认对编号 ${patient.value.patientNo} 执行“${operationLabel.value}”？不会删除数据或修改临床内容。`
  )
  busy.value = true
  try {
    await changeRehabPatientDataOrigin({
      patientId: patient.value.id,
      confirmPatientNo: confirmation.value.trim(),
      expectedDataOrigin: patient.value.dataOrigin,
      operation: operation.value,
      reason: reason.value.trim()
    })
    visible.value = false
    message.success('资料来源已更新并记录审计')
    emit('success')
  } finally {
    busy.value = false
  }
}
defineExpose({ open })
</script>
