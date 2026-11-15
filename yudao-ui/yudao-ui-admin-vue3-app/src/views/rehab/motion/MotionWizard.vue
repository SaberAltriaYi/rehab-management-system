<template>
  <Dialog
    v-model="visible"
    :title="form.id ? '编辑动作评估' : '新建动作评估'"
    width="860px"
    top="5vh"
  >
    <div class="wizard-intro">
      <div
        ><p class="eyebrow">CLINICAL MOVEMENT WORKFLOW</p
        ><h3>{{ form.id ? '更新评估配置' : '创建一次可追溯的动作评估' }}</h3
        ><p
          >先确认患者和协议，再进入 OpenCap 采集。保存后可继续上传数据、映射 Trial
          和启动异步分析。</p
        ></div
      >
      <el-tag type="info" effect="plain">治疗师审核后才可签署</el-tag>
    </div>
    <el-steps :active="step" simple class="wizard-steps"
      ><el-step
        v-for="item in STEP_ITEMS"
        :key="item.title"
        :title="item.title"
        :description="item.description"
    /></el-steps>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="116px"
      v-loading="loading"
      class="wizard-form"
    >
      <section v-show="step === 0" class="wizard-section">
        <div class="section-heading"
          ><span class="section-number">01</span
          ><div
            ><h4>患者与评估阶段</h4
            ><p>患者资料来自主系统；这里仅选择当前治疗师有权访问的患者。</p></div
          ></div
        >
        <el-form-item label="患者" prop="patientId"
          ><el-select
            v-model="form.patientId"
            filterable
            remote
            clearable
            :remote-method="searchPatients"
            :disabled="!!form.id || patientLocked"
            placeholder="输入姓名或患者编号搜索"
            class="full-width"
            ><el-option
              v-for="p in patientOptions"
              :key="p.id"
              :label="`${p.name}（${p.patientNo || p.id}）`"
              :value="p.id" /></el-select
          ><div class="field-hint"
            >找不到患者？先到主系统患者管理完成建档，再返回这里创建评估。</div
          ></el-form-item
        >
        <el-form-item label="评估阶段" prop="visitType"
          ><el-radio-group v-model="form.visitType" class="choice-grid"
            ><el-radio v-for="v in VISIT_OPTIONS" :key="v.value" :value="v.value" border>{{
              v.label
            }}</el-radio></el-radio-group
          ></el-form-item
        >
        <el-form-item v-if="form.visitType !== 'initial'" label="对比基线"
          ><el-select
            v-model="form.baselineId"
            clearable
            placeholder="选择同一患者的既往评估"
            class="full-width"
            ><el-option
              v-for="b in baselines"
              :key="b.id"
              :label="`#${b.id} ${visitLabel(b.visitType)} ${b.captureTime ? formatDate(b.captureTime as any, 'YYYY-MM-DD') : ''} ${b.statusLabel || ''}`"
              :value="b.id" /></el-select
          ><div class="field-hint"
            >复评应尽量使用同一协议、相似采集条件，并保留初评作为比较基线。</div
          ></el-form-item
        >
      </section>
      <section v-show="step === 1" class="wizard-section">
        <div class="section-heading"
          ><span class="section-number">02</span
          ><div
            ><h4>协议与命名</h4><p>命名会用于 OpenCap Session、Trial 映射和后续报告追溯。</p></div
          ></div
        >
        <el-form-item label="评估体系" prop="protocolFamilies"
          ><el-checkbox-group v-model="form.protocolFamilies" class="protocol-grid"
            ><el-checkbox v-for="f in FAMILY_OPTIONS" :key="f.value" :value="f.value" border>{{
              f.label
            }}</el-checkbox></el-checkbox-group
          ><div class="field-hint"
            >YBT、团身跳和 LESS 会单独标注协议来源，不归入 NASM 官方评分。</div
          ></el-form-item
        >
        <el-form-item label="评估命名"
          ><el-input
            v-model="form.title"
            maxlength="128"
            placeholder="例如：FMS 初评 — 2026-10-15"
          /><div class="name-rule"
            ><strong>建议命名：</strong>{{ suggestedName
            }}<span>患者姓名不会被自动写入 OpenCap Trial 名称。</span></div
          ></el-form-item
        >
        <el-form-item label="采集时间"
          ><el-date-picker
            v-model="captureTime"
            type="datetime"
            placeholder="选择实际采集时间"
            class="full-width"
        /></el-form-item>
      </section>
      <section v-show="step === 2" class="wizard-section">
        <div class="section-heading"
          ><span class="section-number">03</span
          ><div
            ><h4>OpenCap 采集与数据来源</h4
            ><p>系统不会替你伪造 Session；治疗师在 OpenCap 完成采集后回填会话信息。</p></div
          ></div
        >
        <el-form-item label="数据来源" prop="dataSource"
          ><el-radio-group v-model="form.dataSource" class="source-cards"
            ><el-radio value="opencap" border
              ><strong>OpenCap 会话</strong
              ><span>官网完成采集后，回填 Session ID 并读取 Trial。</span></el-radio
            ><el-radio value="upload" border
              ><strong>导入导出包</strong
              ><span>上传 OpenCapData 文件夹，系统按策略筛选文件。</span></el-radio
            ></el-radio-group
          ></el-form-item
        >
        <el-form-item
          v-if="form.dataSource === 'opencap'"
          label="OpenCap 会话 ID"
          prop="opencapSessionId"
          ><el-input
            v-model="form.opencapSessionId"
            placeholder="粘贴 UUID，例如 abe79267-...."
          /><div class="opencap-actions"
            ><el-button link type="primary" @click="openOpenCap"
              ><Icon icon="ep:link" class="mr-4px" />打开 OpenCap 官网</el-button
            ><span class="field-hint">完成采集后返回此页，再读取 Trial 列表。</span></div
          ></el-form-item
        >
        <el-form-item label="相机数量" prop="cameraCount"
          ><el-input-number v-model="form.cameraCount" :min="2" :max="8" /><span class="inline-hint"
            >至少 2 台相机</span
          ></el-form-item
        >
        <el-form-item label="视频授权"
          ><el-switch v-model="form.videoConsent" /><span class="inline-hint"
            >未授权时不上传、保存或发送视频，仅使用运动学数据。</span
          ></el-form-item
        >
        <el-form-item label="AI 解释"
          ><el-switch v-model="form.aiAllowed" /><span class="inline-hint"
            >AI 只解释结构化证据，不改分、不诊断、不签发。</span
          ></el-form-item
        >
      </section>
      <section v-show="step === 3" class="wizard-section">
        <div class="section-heading"
          ><span class="section-number">04</span
          ><div
            ><h4>协议补充与保存</h4
            ><p>只有被协议要求的输入才需要填写；缺失数据不会被系统用 0 代替。</p></div
          ></div
        >
        <el-form-item label="性别分组"
          ><el-select v-model="form.sexGroup" clearable placeholder="可选" style="width: 180px"
            ><el-option label="男" value="male" /><el-option label="女" value="female" /></el-select
        ></el-form-item>
        <el-form-item v-if="form.protocolFamilies.includes('YBT_LQ')" label="腿长（cm）"
          ><div class="number-pair"
            ><span
              >左
              <el-input-number
                v-model="form.limbLengthLeftCm"
                :min="40"
                :max="140"
                :precision="1" /></span
            ><span
              >右
              <el-input-number
                v-model="form.limbLengthRightCm"
                :min="40"
                :max="140"
                :precision="1" /></span></div
          ><div class="field-hint">ASIS 至内踝，用于 YBT 归一化。</div></el-form-item
        >
        <el-form-item v-if="form.protocolFamilies.includes('TUCK_JUMP')" label="TJA 版本"
          ><el-select v-model="form.tjaVariant" class="full-width"
            ><el-option
              v-for="v in TJA_VARIANTS"
              :key="v.value"
              :label="v.label"
              :value="v.value" /></el-select
        ></el-form-item>
        <el-form-item label="备注"
          ><el-input
            v-model="form.remark"
            type="textarea"
            maxlength="500"
            :rows="3"
            placeholder="只填写与本次评估有关的信息，请勿填写无关身份资料。"
        /></el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="保存后不会立即生成正式评分。请在详情页完成数据导入、质控、规则计算、治疗师审核和签署。"
        />
      </section>
    </el-form>
    <template #footer
      ><div class="wizard-footer"
        ><span class="footer-progress">第 {{ step + 1 }} / {{ STEP_ITEMS.length }} 步</span
        ><div
          ><el-button @click="visible = false">取消</el-button
          ><el-button v-if="step > 0" @click="step--">上一步</el-button
          ><el-button v-if="step < STEP_ITEMS.length - 1" type="primary" @click="nextStep"
            >下一步</el-button
          ><el-button v-else type="primary" :loading="loading" @click="submit"
            >保存并进入详情</el-button
          ></div
        ></div
      ></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { MotionApi, MotionAssessmentVO, MotionSaveReqVO } from '@/api/rehab/motion'
import { formatDate } from '@/utils/formatTime'
import { getRehabPatient, getRehabPatientPage } from '@/api/rehab/patient'
import {
  FAMILY_OPTIONS,
  OPENCAP_WEB_URL,
  TEMPLATE_ASSESSMENT_NAMES,
  TJA_VARIANTS,
  VISIT_OPTIONS,
  visitLabel
} from './constants'
defineOptions({ name: 'RehabMotionWizard' })
const emit = defineEmits(['success'])
const message = useMessage()
const STEP_ITEMS = [
  { title: '患者', description: '选择归属' },
  { title: '协议', description: '确定命名' },
  { title: '采集', description: 'OpenCap 数据' },
  { title: '保存', description: '补充信息' }
]
const visible = ref(false)
const loading = ref(false)
const patientLocked = ref(false)
const step = ref(0)
const formRef = ref()
const baselines = ref<MotionAssessmentVO[]>([])
const captureTime = ref<Date | null>(new Date())
const patientOptions = ref<Array<{ id: number; name: string; patientNo?: string }>>([])
const form = ref<MotionSaveReqVO>(blank())
function blank(): MotionSaveReqVO {
  return {
    patientId: undefined as unknown as number,
    visitType: 'initial',
    protocolFamilies: ['FMS'],
    dataSource: 'upload',
    cameraCount: 2,
    videoConsent: false,
    aiAllowed: false,
    tjaVariant: 'TJA_MODIFIED_0_2'
  }
}
const suggestedName = computed(() => {
  const family = form.value.protocolFamilies?.[0]
  const prefix =
    family === 'FMS'
      ? TEMPLATE_ASSESSMENT_NAMES[11]
      : family === 'NASM_CES'
        ? TEMPLATE_ASSESSMENT_NAMES[0]
        : family === 'YBT_LQ'
          ? TEMPLATE_ASSESSMENT_NAMES[23]
          : family === 'TUCK_JUMP'
            ? TEMPLATE_ASSESSMENT_NAMES[9]
            : family === 'LESS'
              ? TEMPLATE_ASSESSMENT_NAMES[8]
              : '动作评估'
  return `${prefix} — ${visitLabel(form.value.visitType)}`
})
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
        form.value.dataSource !== 'opencap' || uuid.test(v || '')
          ? cb()
          : cb(new Error('请输入 UUID 格式的会话 ID')),
      trigger: 'blur'
    }
  ]
}
const searchPatients = async (keyword: string) => {
  const page = await getRehabPatientPage({ pageNo: 1, pageSize: 20, keyword } as any)
  patientOptions.value = (page?.list || []).map((p: any) => ({
    id: p.id,
    name: p.name,
    patientNo: p.patientNo
  }))
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
watch(
  () => form.value.patientId,
  async (pid) => {
    baselines.value = pid
      ? ((await MotionApi.listByPatient(pid)) || []).filter((b) => b.id !== form.value.id)
      : []
  }
)
const open = async (opts: { id?: number; patientId?: number } = {}) => {
  visible.value = true
  step.value = 0
  patientLocked.value = !!opts.patientId
  form.value = blank()
  captureTime.value = new Date()
  patientOptions.value = []
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
const openOpenCap = () => window.open(OPENCAP_WEB_URL, '_blank', 'noopener,noreferrer')
const nextStep = async () => {
  const ok = await formRef.value.validate().catch(() => false)
  if (ok) step.value = Math.min(step.value + 1, STEP_ITEMS.length - 1)
}
const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = {
      ...form.value,
      captureTime: captureTime.value ? captureTime.value.getTime() : undefined
    }
    if (data.dataSource !== 'opencap') data.opencapSessionId = ''
    if (data.id) {
      await MotionApi.update(data)
      message.success('评估配置已保存')
      emit('success', data.id)
    } else {
      const id = await MotionApi.create(data)
      message.success('评估已创建')
      emit('success', id)
    }
    visible.value = false
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wizard-intro {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 2px 0 18px;
}
.eyebrow {
  margin: 0 0 6px;
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.wizard-intro h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 20px;
}
.wizard-intro p:last-child {
  margin: 7px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.wizard-steps {
  margin: 0 0 20px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
.wizard-form {
  min-height: 340px;
}
.wizard-section {
  padding: 2px 4px 4px;
}
.section-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 22px;
}
.section-number {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}
.section-heading h4 {
  margin: 0;
  font-size: 16px;
}
.section-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.full-width {
  width: 100%;
}
.choice-grid,
.protocol-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.choice-grid :deep(.el-radio),
.protocol-grid :deep(.el-checkbox) {
  height: auto;
  margin: 0;
  padding: 10px 14px;
}
.source-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}
.source-cards :deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin: 0;
  padding: 15px;
  white-space: normal;
}
.source-cards :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.source-cards span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.field-hint,
.inline-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.inline-hint {
  margin-left: 12px;
}
.name-rule {
  margin-top: 4px;
  color: var(--el-color-primary);
  font-size: 12px;
}
.name-rule span {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
}
.opencap-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.number-pair {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}
.number-pair span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.wizard-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.footer-progress {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@media (max-width: 640px) {
  .wizard-intro {
    flex-direction: column;
  }
  .source-cards {
    grid-template-columns: 1fr;
  }
  .wizard-steps :deep(.el-step__title) {
    font-size: 12px;
  }
}
</style>
