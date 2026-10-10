<template>
  <Dialog v-model="visible" :title="form.id ? '编辑动作评估' : '新建动作评估'" width="720px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" v-loading="loading">
      <el-form-item label="患者" prop="patientId">
        <el-select
          v-model="form.patientId"
          filterable
          remote
          :remote-method="searchPatients"
          :disabled="!!form.id || patientLocked"
          placeholder="输入姓名或编号搜索（仅显示有权限的患者）"
          style="width: 100%"
        >
          <el-option v-for="p in patientOptions" :key="p.id" :label="`${p.name}（${p.patientNo || p.id}）`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="评估类型" prop="visitType">
        <el-radio-group v-model="form.visitType">
          <el-radio v-for="v in VISIT_OPTIONS" :key="v.value" :value="v.value">{{ v.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.visitType !== 'initial'" label="对比基线">
        <el-select v-model="form.baselineId" clearable placeholder="选择同一患者的既往评估" style="width: 100%">
          <el-option
            v-for="b in baselines"
            :key="b.id"
            :label="`#${b.id} ${visitLabel(b.visitType)} ${b.captureTime ? formatDate(b.captureTime as any, 'YYYY-MM-DD') : ''} ${b.statusLabel || ''}`"
            :value="b.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="评估体系" prop="protocolFamilies">
        <el-checkbox-group v-model="form.protocolFamilies">
          <el-checkbox v-for="f in FAMILY_OPTIONS" :key="f.value" :value="f.value">{{ f.label }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="form.title" maxlength="128" placeholder="可选，例如：术后 6 周复评" />
      </el-form-item>
      <el-form-item label="采集时间">
        <el-date-picker v-model="captureTime" type="datetime" placeholder="采集时间" />
      </el-form-item>
      <el-form-item label="数据来源" prop="dataSource">
        <el-radio-group v-model="form.dataSource">
          <el-radio value="upload">上传 OpenCap 导出数据</el-radio>
          <el-radio value="opencap">从 OpenCap 会话拉取</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.dataSource === 'opencap'" label="OpenCap 会话 ID" prop="opencapSessionId">
        <el-input v-model="form.opencapSessionId" placeholder="UUID，例如 abe79267-...." />
      </el-form-item>
      <el-form-item label="相机数量" prop="cameraCount">
        <el-input-number v-model="form.cameraCount" :min="2" :max="8" />
        <span class="hint">OpenCap 至少 2 台相机</span>
      </el-form-item>
      <el-form-item label="视频授权">
        <el-switch v-model="form.videoConsent" />
        <span class="hint">未授权时视频不上传、不保存、不发送给 AI；仅使用运动学数据</span>
      </el-form-item>
      <el-form-item label="允许 AI 解释">
        <el-switch v-model="form.aiAllowed" />
        <span class="hint">仅发送去标识化指标与评分；AI 不改分、不诊断，关闭时使用系统模板</span>
      </el-form-item>
      <el-form-item label="性别分组">
        <el-select v-model="form.sexGroup" clearable style="width: 160px">
          <el-option label="男" value="male" />
          <el-option label="女" value="female" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.protocolFamilies.includes('YBT_LQ')" label="腿长（cm）">
        左 <el-input-number v-model="form.limbLengthLeftCm" :min="40" :max="140" :precision="1" />
        右 <el-input-number v-model="form.limbLengthRightCm" :min="40" :max="140" :precision="1" />
        <span class="hint">ASIS 至内踝；YBT 归一化必需</span>
      </el-form-item>
      <el-form-item v-if="form.protocolFamilies.includes('TUCK_JUMP')" label="TJA 版本">
        <el-select v-model="form.tjaVariant" style="width: 100%">
          <el-option v-for="v in TJA_VARIANTS" :key="v.value" :label="v.label" :value="v.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" maxlength="500" :rows="2" placeholder="请勿填写与评估无关的身份信息" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { MotionApi, MotionAssessmentVO, MotionSaveReqVO } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import { getRehabPatient, getRehabPatientPage } from '@/api/rehab/patient'
import { FAMILY_OPTIONS, TJA_VARIANTS, VISIT_OPTIONS, visitLabel } from './constants'

defineOptions({ name: 'RehabMotionForm' })
const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const patientLocked = ref(false)
const formRef = ref()
const baselines = ref<MotionAssessmentVO[]>([])
const captureTime = ref<Date | null>(new Date())
const patientOptions = ref<Array<{ id: number; name: string; patientNo?: string }>>([])

const searchPatients = async (keyword: string) => {
  const page = await getRehabPatientPage({ pageNo: 1, pageSize: 20, keyword } as any)
  patientOptions.value = (page?.list || []).map((p: any) => ({ id: p.id, name: p.name, patientNo: p.patientNo }))
}
const ensurePatientOption = async (id?: number) => {
  if (!id || patientOptions.value.some((p) => p.id === id)) return
  try {
    const p: any = await getRehabPatient(id)
    if (p) patientOptions.value.push({ id: p.id, name: p.name, patientNo: p.patientNo })
  } catch {
    patientOptions.value.push({ id, name: '患者 #' + id })
  }
}

const blank = (): MotionSaveReqVO => ({
  patientId: undefined as unknown as number,
  visitType: 'initial',
  protocolFamilies: ['FMS'],
  dataSource: 'upload',
  cameraCount: 2,
  videoConsent: false,
  aiAllowed: false,
  tjaVariant: 'TJA_MODIFIED_0_2'
})
const form = ref<MotionSaveReqVO>(blank())

const uuid = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const rules: FormRules = {
  patientId: [{ required: true, message: '请选择患者', trigger: 'blur' }],
  visitType: [{ required: true, message: '请选择评估类型', trigger: 'change' }],
  protocolFamilies: [{ type: 'array', required: true, min: 1, message: '至少选择一个评估体系' }],
  dataSource: [{ required: true, message: '请选择数据来源', trigger: 'change' }],
  cameraCount: [{ required: true, message: '请填写相机数量', trigger: 'blur' }],
  opencapSessionId: [
    {
      validator: (_r: any, v: string, cb: (e?: Error) => void) =>
        form.value.dataSource !== 'opencap' || uuid.test(v || '') ? cb() : cb(new Error('请输入 UUID 格式的会话 ID')),
      trigger: 'blur'
    }
  ]
}

watch(
  () => form.value.patientId,
  async (pid) => {
    baselines.value = pid ? ((await MotionApi.listByPatient(pid)) || []).filter((b) => b.id !== form.value.id) : []
  }
)

const open = async (opts: { id?: number; patientId?: number } = {}) => {
  visible.value = true
  patientLocked.value = !!opts.patientId
  form.value = blank()
  captureTime.value = new Date()
  if (opts.patientId) form.value.patientId = opts.patientId
  await ensurePatientOption(opts.patientId)
  if (opts.id) {
    loading.value = true
    try {
      const d = await MotionApi.get(opts.id)
      form.value = {
        id: d.id,
        patientId: d.patientId,
        episodeId: d.episodeId,
        assessmentRecordId: d.assessmentRecordId,
        baselineId: d.baselineId,
        visitType: d.visitType,
        protocolFamilies: d.protocolFamilies || [],
        title: d.title,
        dataSource: d.dataSource,
        opencapSessionId: d.opencapSessionId,
        cameraCount: d.cameraCount || 2,
        videoConsent: d.videoConsent,
        aiAllowed: d.aiAllowed,
        sexGroup: d.sexGroup,
        limbLengthLeftCm: d.limbLengthLeftCm,
        limbLengthRightCm: d.limbLengthRightCm,
        tjaVariant: d.tjaVariant,
        therapistUserId: d.therapistUserId,
        remark: d.remark
      }
      captureTime.value = d.captureTime ? new Date(d.captureTime as any) : null
      await ensurePatientOption(d.patientId)
    } finally {
      loading.value = false
    }
  }
}
defineExpose({ open })

const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = { ...form.value, captureTime: captureTime.value ? captureTime.value.getTime() : undefined }
    if (data.dataSource !== 'opencap') data.opencapSessionId = ''
    if (data.id) {
      await MotionApi.update(data)
      message.success('已保存')
      emit('success', data.id)
    } else {
      const id = await MotionApi.create(data)
      message.success('已创建')
      emit('success', id)
    }
    visible.value = false
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
