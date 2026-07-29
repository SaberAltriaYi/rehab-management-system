import { buildDefaultMseAnalysis, SfmaClassification, SfmaMseAnalysis, SfmaMseBreakout } from '@/views/rehab/assessment/config/sfmaConfig'

const CLASSIFICATIONS: SfmaClassification[] = ['FN', 'FP', 'DN', 'DP']
const PAIN_RESULTS: SfmaClassification[] = ['FP', 'DP']

type MseFlowGroup = 'spinal_extension_flow' | 'lower_body_extension_flow' | 'upper_body_extension_flow'

export type MseFlowNodeCode =
  | 'trunk_extension_without_upper_extremity'
  | 'single_leg_stance_trunk_extension'
  | 'prone_press_up'
  | 'lumbar_fixed_internal_rotation_active_extension_rotation'
  | 'lumbar_fixed_internal_rotation_passive_extension_rotation'
  | 'prone_elbow_supported_extension_rotation'
  | 'standing_hip_extension'
  | 'prone_active_hip_extension'
  | 'prone_passive_hip_extension'
  | 'rolling_analysis_result_lower'
  | 'faber_test'
  | 'modified_thomas_test'
  | 'single_shoulder_extension'
  | 'supine_double_hip_flexion_lat_stretch'
  | 'supine_double_hip_extension_lat_stretch'
  | 'lumbar_fixed_external_rotation_extension'
  | 'lumbar_fixed_internal_rotation_active_extension_rotation_upper'
  | 'lumbar_fixed_internal_rotation_passive_extension_rotation_upper'

export interface MseNodeRule {
  node_code: MseFlowNodeCode
  node_name_zh: string
  flow_group: MseFlowGroup
  purpose: string
  instructions: string
  clinical_notes: string
  result_options: string[]
  stop_if_pain: boolean
  next_step_rules: string
}

const NODE_META: Record<MseFlowNodeCode, MseNodeRule> = {
  trunk_extension_without_upper_extremity: {
    node_code: 'trunk_extension_without_upper_extremity',
    node_name_zh: '无上肢参与的躯体后伸',
    flow_group: 'spinal_extension_flow',
    purpose: '排除上肢干扰，观察站立位脊柱/髋伸展表现。',
    instructions: '站立双手叉腰尽可能后伸，限制膝屈曲，观察能否无痛完成。',
    clinical_notes: 'FN 进入上半身伸展流程；DN/FP/DP 进入单腿站立后伸。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 上半身伸展流程；DN/FP/DP -> 单腿站立躯体后伸'
  },
  single_leg_stance_trunk_extension: {
    node_code: 'single_leg_stance_trunk_extension',
    node_name_zh: '单腿站立躯体后伸',
    flow_group: 'spinal_extension_flow',
    purpose: '识别对称/不对称站立伸展问题。',
    instructions: '左右单腿站立后伸并比较。',
    clinical_notes: '双侧FN更支持对称核心控制问题，异常则进入俯卧撑。',
    result_options: ['bilateral_FN', 'unilateral_abnormal', 'bilateral_abnormal'],
    stop_if_pain: false,
    next_step_rules: '双侧FN -> 上半身伸展流程；其余 -> 俯卧撑'
  },
  prone_press_up: {
    node_code: 'prone_press_up',
    node_name_zh: '俯卧撑（非负重伸展）',
    flow_group: 'spinal_extension_flow',
    purpose: '区分负重与非负重伸展问题。',
    instructions: '俯卧位后伸，必要时骨盆垫高后复测。',
    clinical_notes: 'FN 往往提示负重下稳定/控制问题；异常进入腰部固定主动旋转/伸展。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 上/下半身伸展流程；DN/FP/DP -> 腰部固定（内旋）主动旋转/伸展'
  },
  lumbar_fixed_internal_rotation_active_extension_rotation: {
    node_code: 'lumbar_fixed_internal_rotation_active_extension_rotation',
    node_name_zh: '腰部固定（内旋）主动旋转/伸展',
    flow_group: 'spinal_extension_flow',
    purpose: '聚焦胸椎主动伸展/旋转能力。',
    instructions: '俯卧位向后摆姿势，左右侧主动旋转/伸展。',
    clinical_notes: 'FN 进入俯卧位肘支撑；异常进入腰部固定被动旋转/伸展。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 俯卧位肘支撑旋转/伸展；DN/FP/DP -> 腰部固定（内旋）被动旋转/伸展'
  },
  lumbar_fixed_internal_rotation_passive_extension_rotation: {
    node_code: 'lumbar_fixed_internal_rotation_passive_extension_rotation',
    node_name_zh: '腰部固定（内旋）被动旋转/伸展',
    flow_group: 'spinal_extension_flow',
    purpose: '区分胸椎伸展旋转的结构限制与控制问题。',
    instructions: '同位姿被动测试左右。',
    clinical_notes: 'FP/DP疼痛终止；FN/单侧DN/双侧DN进入上/下半身流程。',
    result_options: ['FN', 'FP', 'DP', 'unilateral_DN', 'bilateral_DN'],
    stop_if_pain: true,
    next_step_rules: 'FP/DP -> STOP_PAIN；FN/单侧DN/双侧DN -> 上/下半身伸展流程'
  },
  prone_elbow_supported_extension_rotation: {
    node_code: 'prone_elbow_supported_extension_rotation',
    node_name_zh: '俯卧位肘支撑旋转/伸展',
    flow_group: 'spinal_extension_flow',
    purpose: '聚焦腰椎方向伸展/旋转问题。',
    instructions: '俯卧位肘支撑，左右侧旋转/伸展。',
    clinical_notes: 'FP/DP疼痛终止；双侧FN/单侧DN/双侧DN进入上/下半身流程。',
    result_options: ['bilateral_FN', 'unilateral_DN', 'bilateral_DN', 'FP', 'DP'],
    stop_if_pain: true,
    next_step_rules: 'FP/DP -> STOP_PAIN；其余 -> 上/下半身伸展流程'
  },
  standing_hip_extension: {
    node_code: 'standing_hip_extension',
    node_name_zh: '站立位髋关节后伸',
    flow_group: 'lower_body_extension_flow',
    purpose: '评估负重位髋伸展与下肢支撑策略。',
    instructions: '双侧站立位髋后伸并比较。',
    clinical_notes: 'FN（双侧>10°）可进入滚动；异常进入俯卧位髋关节主动后伸。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节主动后伸'
  },
  prone_active_hip_extension: {
    node_code: 'prone_active_hip_extension',
    node_name_zh: '俯卧位髋关节主动后伸',
    flow_group: 'lower_body_extension_flow',
    purpose: '在非负重位评估主动髋伸展。',
    instructions: '俯卧位左右主动后伸并记录。',
    clinical_notes: 'FN进入滚动；DN/FP/DP进入俯卧位髋关节被动后伸。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节被动后伸'
  },
  prone_passive_hip_extension: {
    node_code: 'prone_passive_hip_extension',
    node_name_zh: '俯卧位髋关节被动后伸',
    flow_group: 'lower_body_extension_flow',
    purpose: '比较主动/被动差异，区分控制与灵活性。',
    instructions: '俯卧位被动髋后伸，与主动结果比较。',
    clinical_notes: 'FN可进入改良托马斯；DN/FP/DP进入法伯尔；被动显著优于主动可进入滚动。',
    result_options: ['FN', 'FP', 'DN', 'DP', 'fn_gap_gt_25'],
    stop_if_pain: true,
    next_step_rules: 'fn_gap_gt_25 -> 滚动解析（下半身）；FN -> 改良托马斯；DN/FP/DP -> 法伯尔'
  },
  rolling_analysis_result_lower: {
    node_code: 'rolling_analysis_result_lower',
    node_name_zh: '滚动解析结果（下半身）',
    flow_group: 'lower_body_extension_flow',
    purpose: '评估基础伸展模式与负重髋伸展控制问题。',
    instructions: '记录滚动结果。',
    clinical_notes: 'FN提示负重髋伸展SMCD；DN提示基础伸展模式SMCD；FP/DP疼痛终止。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: true,
    next_step_rules: 'FN/DN -> END；FP/DP -> STOP_PAIN'
  },
  faber_test: {
    node_code: 'faber_test',
    node_name_zh: '法伯尔试验',
    flow_group: 'lower_body_extension_flow',
    purpose: '筛查髋/骶髂灵活性与疼痛诱发。',
    instructions: '仰卧位执行 FABER 双侧比较。',
    clinical_notes: 'FN或DN继续改良托马斯；FP/DP疼痛终止。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: true,
    next_step_rules: 'FN/DN -> 改良托马斯；FP/DP -> STOP_PAIN'
  },
  modified_thomas_test: {
    node_code: 'modified_thomas_test',
    node_name_zh: '改良托马斯试验',
    flow_group: 'lower_body_extension_flow',
    purpose: '区分前链/侧链TED、髋JMD与核心控制问题。',
    instructions: '按改良托马斯流程记录结果。',
    clinical_notes: 'FN或分型FN提示不同组织链受限；DN提示髋JMD/TED；FP/DP疼痛终止。',
    result_options: ['FN', 'FP', 'DN', 'DP', 'fn_with_knee_extension', 'fn_with_hip_abduction', 'fn_with_both'],
    stop_if_pain: true,
    next_step_rules: 'FN/分型FN/DN -> END；FP/DP -> STOP_PAIN'
  },
  single_shoulder_extension: {
    node_code: 'single_shoulder_extension',
    node_name_zh: '单肩后伸',
    flow_group: 'upper_body_extension_flow',
    purpose: '识别单侧上半身伸展障碍与疼痛。',
    instructions: '单臂上举过头并后伸，双侧比较。',
    clinical_notes: 'DN/FP/DP进入仰卧位双髋屈曲背阔肌拉伸；FN可提示复查脊柱/颈椎。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> 可结束上半身流程（建议复查脊柱/颈椎）；DN/FP/DP -> 仰卧位双髋屈曲背阔肌拉伸'
  },
  supine_double_hip_flexion_lat_stretch: {
    node_code: 'supine_double_hip_flexion_lat_stretch',
    node_name_zh: '仰卧位双髋屈曲背阔肌拉伸',
    flow_group: 'upper_body_extension_flow',
    purpose: '评估不负重位背阔肌长度与肩屈曲模式。',
    instructions: '仰卧双髋屈曲位，双臂上举接近床面。',
    clinical_notes: 'FN常提示负重上肢伸展SMCD；异常进入双髋伸展背阔肌拉伸。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> END（可提示上肢稳定/控制）；DN/FP/DP -> 仰卧位双髋伸展背阔肌拉伸'
  },
  supine_double_hip_extension_lat_stretch: {
    node_code: 'supine_double_hip_extension_lat_stretch',
    node_name_zh: '仰卧位双髋伸展背阔肌拉伸',
    flow_group: 'upper_body_extension_flow',
    purpose: '区分背阔肌后链问题与胸廓/肩带问题。',
    instructions: '仰卧双髋伸展位，记录手臂接近床面的变化。',
    clinical_notes: '轻微改善或异常均建议继续腰部固定（外旋）旋转/伸展。',
    result_options: ['FN', 'FP', 'DN', 'DP', 'partial_improvement'],
    stop_if_pain: false,
    next_step_rules: '任一结果 -> 腰部固定（外旋）旋转/伸展（FP/DP同时提示疼痛复核）'
  },
  lumbar_fixed_external_rotation_extension: {
    node_code: 'lumbar_fixed_external_rotation_extension',
    node_name_zh: '腰部固定（外旋）旋转/伸展',
    flow_group: 'upper_body_extension_flow',
    purpose: '降低肩胛稳定要求，观察胸椎伸展旋转。',
    instructions: '俯卧跪位手外旋头后，左右旋转/伸展。',
    clinical_notes: 'FN多提示肩胛/盂肱稳定控制问题；异常进入内旋主动旋转/伸展。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> END（肩胛/盂肱控制方向）；DN/FP/DP -> 腰部固定（内旋）主动旋转/伸展（上半身）'
  },
  lumbar_fixed_internal_rotation_active_extension_rotation_upper: {
    node_code: 'lumbar_fixed_internal_rotation_active_extension_rotation_upper',
    node_name_zh: '腰部固定（内旋）主动旋转/伸展（上半身）',
    flow_group: 'upper_body_extension_flow',
    purpose: '在上半身流程中进一步确认胸椎主动表现。',
    instructions: '同内旋主动旋转/伸展方法记录。',
    clinical_notes: 'FN可提示肩带JMD/TED方向；异常进入内旋被动旋转/伸展。',
    result_options: ['FN', 'FP', 'DN', 'DP'],
    stop_if_pain: false,
    next_step_rules: 'FN -> END（肩带JMD/TED方向）；DN/FP/DP -> 腰部固定（内旋）被动旋转/伸展（上半身）'
  },
  lumbar_fixed_internal_rotation_passive_extension_rotation_upper: {
    node_code: 'lumbar_fixed_internal_rotation_passive_extension_rotation_upper',
    node_name_zh: '腰部固定（内旋）被动旋转/伸展（上半身）',
    flow_group: 'upper_body_extension_flow',
    purpose: '区分胸椎双侧/单侧结构限制与控制问题。',
    instructions: '同内旋被动旋转/伸展方法记录。',
    clinical_notes: 'FP/DP疼痛终止；FN提示胸椎双侧SMCD；DN提示胸椎JMD/TED。',
    result_options: ['FN', 'FP', 'DP', 'unilateral_DN', 'bilateral_DN'],
    stop_if_pain: true,
    next_step_rules: 'FP/DP -> STOP_PAIN；FN/单侧DN/双侧DN -> END'
  }
}

export const MSE_SPINAL_NODE_ORDER: MseFlowNodeCode[] = [
  'trunk_extension_without_upper_extremity',
  'single_leg_stance_trunk_extension',
  'prone_press_up',
  'lumbar_fixed_internal_rotation_active_extension_rotation',
  'lumbar_fixed_internal_rotation_passive_extension_rotation',
  'prone_elbow_supported_extension_rotation'
]

export const MSE_LOWER_NODE_ORDER: MseFlowNodeCode[] = [
  'standing_hip_extension',
  'prone_active_hip_extension',
  'prone_passive_hip_extension',
  'rolling_analysis_result_lower',
  'faber_test',
  'modified_thomas_test'
]

export const MSE_UPPER_NODE_ORDER: MseFlowNodeCode[] = [
  'single_shoulder_extension',
  'supine_double_hip_flexion_lat_stretch',
  'supine_double_hip_extension_lat_stretch',
  'lumbar_fixed_external_rotation_extension',
  'lumbar_fixed_internal_rotation_active_extension_rotation_upper',
  'lumbar_fixed_internal_rotation_passive_extension_rotation_upper'
]

export const MSE_FLOW_NODE_ORDER: MseFlowNodeCode[] = [...MSE_SPINAL_NODE_ORDER, ...MSE_LOWER_NODE_ORDER, ...MSE_UPPER_NODE_ORDER]

const asClassification = (value: unknown): SfmaClassification => {
  const next = String(value || '') as SfmaClassification
  return CLASSIFICATIONS.includes(next) ? next : ''
}

const isPainResult = (value: unknown) => PAIN_RESULTS.includes(asClassification(value))

const getNodeRef = (analysis: SfmaMseAnalysis, nodeCode: MseFlowNodeCode) => {
  const meta = NODE_META[nodeCode]
  return (analysis[meta.flow_group] as any)[nodeCode] as Record<string, any>
}

const cloneDeep = <T>(value: T): T => JSON.parse(JSON.stringify(value))

const classifySingleLegSummary = (left: SfmaClassification, right: SfmaClassification) => {
  if (!left || !right) return ''
  if (left === 'FN' && right === 'FN') return 'bilateral_FN'
  if (left !== 'FN' && right !== 'FN') return 'bilateral_abnormal'
  return 'unilateral_abnormal'
}

const deriveDualResult = (left: SfmaClassification, right: SfmaClassification): SfmaClassification => {
  if (!left || !right) return ''
  if (left === 'DP' || right === 'DP') return 'DP'
  if (left === 'FP' || right === 'FP') return 'FP'
  if (left === 'DN' || right === 'DN') return 'DN'
  return 'FN'
}

const derivePassiveType = (left: SfmaClassification, right: SfmaClassification) => {
  if (!left || !right) return ''
  if (left === 'DP' || right === 'DP') return 'DP'
  if (left === 'FP' || right === 'FP') return 'FP'
  if (left === 'DN' && right === 'DN') return 'bilateral_DN'
  if ((left === 'DN' && right === 'FN') || (left === 'FN' && right === 'DN')) return 'unilateral_DN'
  return 'FN'
}

const nodeCompleted = (node: Record<string, any>) => {
  return Boolean(node.result_type || node.result_code)
}

export const hasMseNodeValue = (payload: SfmaMseBreakout, nodeCode: MseFlowNodeCode) => {
  const analysis = payload.mse_analysis
  if (!analysis) return false
  const node = getNodeRef(analysis, nodeCode)
  if (!node) return false
  return Boolean(
    node.result_type ||
      node.result_code ||
      node.left_result ||
      node.right_result ||
      node.note ||
      node.summary_text ||
      node.gap_percent ||
      node.used_pad
  )
}

export const getMseNodeRule = (nodeCode: MseFlowNodeCode) => NODE_META[nodeCode]

export const runMseAnalysisFlowEngine = (payload: SfmaMseBreakout) => {
  const analysis = cloneDeep(buildDefaultMseAnalysis())
  const incoming = payload.mse_analysis || ({} as SfmaMseBreakout['mse_analysis'])

  ;(['spinal_extension_flow', 'lower_body_extension_flow', 'upper_body_extension_flow'] as const).forEach((group) => {
    const source = (incoming as any)[group] || {}
    const target = (analysis as any)[group] || {}
    Object.keys(target).forEach((nodeCode) => {
      target[nodeCode] = { ...target[nodeCode], ...(source[nodeCode] || {}) }
      const meta = NODE_META[nodeCode as MseFlowNodeCode]
      if (meta) {
        target[nodeCode].node_code = meta.node_code
        target[nodeCode].node_name_zh = meta.node_name_zh
        target[nodeCode].purpose = meta.purpose
        target[nodeCode].instructions = meta.instructions
        target[nodeCode].clinical_notes = meta.clinical_notes
        target[nodeCode].next_step_rules = meta.next_step_rules
        target[nodeCode].stop_if_pain = meta.stop_if_pain
      }
    })
  })

  const enabled = new Set<MseFlowNodeCode>(['trunk_extension_without_upper_extremity'])
  let stopAndTreatPain = false
  let upperFlowNeeded = false
  let lowerFlowNeeded = false
  let thoracicIssue = false
  let lumbarIssue = false
  let weightBearingIssue = false

  const n1 = getNodeRef(analysis, 'trunk_extension_without_upper_extremity')
  n1.result_type = asClassification(n1.result_type)
  n1.pain_present = Boolean(n1.pain_present) || isPainResult(n1.result_type)
  n1.result_code = n1.result_type
  n1.summary_text = n1.result_type ? `无上肢参与的躯体后伸：${n1.result_type}。` : '无上肢参与的躯体后伸尚未录入。'
  if (n1.result_type) {
    if (n1.result_type === 'FN') {
      upperFlowNeeded = true
      weightBearingIssue = true
    } else {
      enabled.add('single_leg_stance_trunk_extension')
    }
  }

  const n2 = getNodeRef(analysis, 'single_leg_stance_trunk_extension')
  n2.left_result = asClassification(n2.left_result)
  n2.right_result = asClassification(n2.right_result)
  const bilateralSummary = n2.bilateral_summary || classifySingleLegSummary(n2.left_result, n2.right_result)
  n2.bilateral_summary = bilateralSummary
  n2.result_type = bilateralSummary
  n2.result_code = bilateralSummary
  n2.pain_present = Boolean(n2.pain_present) || isPainResult(n2.left_result) || isPainResult(n2.right_result)
  n2.summary_text = bilateralSummary
    ? `单腿站立躯体后伸：${bilateralSummary}（左:${n2.left_result || '-'} / 右:${n2.right_result || '-'}）。`
    : '单腿站立躯体后伸尚未录入。'
  if (enabled.has('single_leg_stance_trunk_extension') && bilateralSummary) {
    if (bilateralSummary === 'bilateral_FN') {
      upperFlowNeeded = true
      weightBearingIssue = true
    } else {
      enabled.add('prone_press_up')
    }
  }

  const n3 = getNodeRef(analysis, 'prone_press_up')
  n3.result_type = asClassification(n3.result_type)
  n3.result_code = n3.result_type
  n3.pain_present = Boolean(n3.pain_present) || isPainResult(n3.result_type)
  n3.summary_text = n3.result_type
    ? `俯卧撑：${n3.result_type}${n3.used_pad ? '（使用垫高）' : ''}。`
    : '俯卧撑尚未录入。'
  if (enabled.has('prone_press_up') && n3.result_type) {
    if (n3.result_type === 'FN') {
      upperFlowNeeded = true
      lowerFlowNeeded = true
      weightBearingIssue = true
    } else {
      enabled.add('lumbar_fixed_internal_rotation_active_extension_rotation')
    }
  }

  const n4 = getNodeRef(analysis, 'lumbar_fixed_internal_rotation_active_extension_rotation')
  n4.left_result = asClassification(n4.left_result)
  n4.right_result = asClassification(n4.right_result)
  n4.result_type = asClassification(n4.result_type) || deriveDualResult(n4.left_result, n4.right_result)
  n4.result_code = n4.result_type
  n4.pain_present = Boolean(n4.pain_present) || isPainResult(n4.result_type)
  n4.summary_text = n4.result_type
    ? `腰部固定（内旋）主动旋转/伸展：${n4.result_type}（左:${n4.left_result || '-'} / 右:${n4.right_result || '-'}）。`
    : '腰部固定（内旋）主动旋转/伸展尚未录入。'
  if (enabled.has('lumbar_fixed_internal_rotation_active_extension_rotation') && n4.result_type) {
    if (n4.result_type === 'FN') {
      enabled.add('prone_elbow_supported_extension_rotation')
    } else {
      enabled.add('lumbar_fixed_internal_rotation_passive_extension_rotation')
    }
  }

  const n5 = getNodeRef(analysis, 'lumbar_fixed_internal_rotation_passive_extension_rotation')
  n5.left_result = asClassification(n5.left_result)
  n5.right_result = asClassification(n5.right_result)
  n5.result_type = String(n5.result_type || derivePassiveType(n5.left_result, n5.right_result))
  n5.result_code = n5.result_type
  n5.pain_present = Boolean(n5.pain_present) || n5.result_type === 'FP' || n5.result_type === 'DP'
  n5.summary_text = n5.result_type
    ? `腰部固定（内旋）被动旋转/伸展：${n5.result_type}（左:${n5.left_result || '-'} / 右:${n5.right_result || '-'}）。`
    : '腰部固定（内旋）被动旋转/伸展尚未录入。'
  if (enabled.has('lumbar_fixed_internal_rotation_passive_extension_rotation') && n5.result_type) {
    if (n5.result_type === 'FP' || n5.result_type === 'DP') {
      stopAndTreatPain = true
    } else {
      upperFlowNeeded = true
      lowerFlowNeeded = true
      thoracicIssue = true
    }
  }

  const n6 = getNodeRef(analysis, 'prone_elbow_supported_extension_rotation')
  n6.left_result = asClassification(n6.left_result)
  n6.right_result = asClassification(n6.right_result)
  n6.result_type = String(n6.result_type || derivePassiveType(n6.left_result, n6.right_result).replace('FN', 'bilateral_FN'))
  n6.result_code = n6.result_type
  n6.pain_present = Boolean(n6.pain_present) || n6.result_type === 'FP' || n6.result_type === 'DP'
  n6.summary_text = n6.result_type
    ? `俯卧位肘支撑旋转/伸展：${n6.result_type}（左:${n6.left_result || '-'} / 右:${n6.right_result || '-'}）。`
    : '俯卧位肘支撑旋转/伸展尚未录入。'
  if (enabled.has('prone_elbow_supported_extension_rotation') && n6.result_type) {
    if (n6.result_type === 'FP' || n6.result_type === 'DP') {
      stopAndTreatPain = true
    } else {
      upperFlowNeeded = true
      lowerFlowNeeded = true
      lumbarIssue = true
      if (n6.result_type === 'bilateral_FN') {
        weightBearingIssue = true
      }
    }
  }

  if (upperFlowNeeded) enabled.add('single_shoulder_extension')
  if (lowerFlowNeeded) enabled.add('standing_hip_extension')

  const l1 = getNodeRef(analysis, 'standing_hip_extension')
  l1.result_type = asClassification(l1.result_type)
  l1.result_code = l1.result_type
  l1.pain_present = Boolean(l1.pain_present) || isPainResult(l1.result_type)
  l1.summary_text = l1.result_type ? `站立位髋关节后伸：${l1.result_type}。` : '站立位髋关节后伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('standing_hip_extension') && l1.result_type) {
    if (l1.result_type === 'FN') {
      enabled.add('rolling_analysis_result_lower')
      weightBearingIssue = true
    } else {
      enabled.add('prone_active_hip_extension')
    }
  }

  const l2 = getNodeRef(analysis, 'prone_active_hip_extension')
  l2.result_type = asClassification(l2.result_type)
  l2.result_code = l2.result_type
  l2.pain_present = Boolean(l2.pain_present) || isPainResult(l2.result_type)
  l2.summary_text = l2.result_type ? `俯卧位髋关节主动后伸：${l2.result_type}。` : '俯卧位髋关节主动后伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('prone_active_hip_extension') && l2.result_type) {
    if (l2.result_type === 'FN') {
      enabled.add('rolling_analysis_result_lower')
    } else {
      enabled.add('prone_passive_hip_extension')
    }
  }

  const l3 = getNodeRef(analysis, 'prone_passive_hip_extension')
  l3.result_type = String(l3.result_type || '')
  l3.result_code = l3.result_type
  l3.pain_present = Boolean(l3.pain_present) || l3.result_type === 'FP' || l3.result_type === 'DP'
  l3.summary_text = l3.result_type ? `俯卧位髋关节被动后伸：${l3.result_type}。` : '俯卧位髋关节被动后伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('prone_passive_hip_extension') && l3.result_type) {
    if (l3.result_type === 'FP' || l3.result_type === 'DP') {
      stopAndTreatPain = true
    } else if (l3.result_type === 'fn_gap_gt_25') {
      enabled.add('rolling_analysis_result_lower')
    } else if (l3.result_type === 'FN') {
      enabled.add('modified_thomas_test')
    } else {
      enabled.add('faber_test')
    }
  }

  const l4 = getNodeRef(analysis, 'rolling_analysis_result_lower')
  l4.result_type = asClassification(l4.result_type)
  l4.result_code = l4.result_type
  l4.pain_present = Boolean(l4.pain_present) || isPainResult(l4.result_type)
  l4.summary_text = l4.result_type ? `滚动解析（下半身）：${l4.result_type}。` : '滚动解析（下半身）尚未录入。'
  if (!stopAndTreatPain && enabled.has('rolling_analysis_result_lower') && isPainResult(l4.result_type)) {
    stopAndTreatPain = true
  }

  const l5 = getNodeRef(analysis, 'faber_test')
  l5.result_type = asClassification(l5.result_type)
  l5.result_code = l5.result_type
  l5.pain_present = Boolean(l5.pain_present) || isPainResult(l5.result_type)
  l5.summary_text = l5.result_type ? `法伯尔试验：${l5.result_type}。` : '法伯尔试验尚未录入。'
  if (!stopAndTreatPain && enabled.has('faber_test') && l5.result_type) {
    if (isPainResult(l5.result_type)) {
      stopAndTreatPain = true
    } else {
      enabled.add('modified_thomas_test')
    }
  }

  const l6 = getNodeRef(analysis, 'modified_thomas_test')
  l6.result_type = String(l6.result_type || '')
  l6.result_code = l6.result_type
  l6.pain_present = Boolean(l6.pain_present) || l6.result_type === 'FP' || l6.result_type === 'DP'
  l6.summary_text = l6.result_type ? `改良托马斯试验：${l6.result_type}。` : '改良托马斯试验尚未录入。'
  if (!stopAndTreatPain && enabled.has('modified_thomas_test') && (l6.result_type === 'FP' || l6.result_type === 'DP')) {
    stopAndTreatPain = true
  }

  const u1 = getNodeRef(analysis, 'single_shoulder_extension')
  u1.result_type = asClassification(u1.result_type)
  u1.result_code = u1.result_type
  u1.pain_present = Boolean(u1.pain_present) || isPainResult(u1.result_type)
  u1.summary_text = u1.result_type ? `单肩后伸：${u1.result_type}。` : '单肩后伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('single_shoulder_extension') && u1.result_type && u1.result_type !== 'FN') {
    enabled.add('supine_double_hip_flexion_lat_stretch')
  }

  const u2 = getNodeRef(analysis, 'supine_double_hip_flexion_lat_stretch')
  u2.result_type = asClassification(u2.result_type)
  u2.result_code = u2.result_type
  u2.pain_present = Boolean(u2.pain_present) || isPainResult(u2.result_type)
  u2.summary_text = u2.result_type ? `仰卧位双髋屈曲背阔肌拉伸：${u2.result_type}。` : '仰卧位双髋屈曲背阔肌拉伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('supine_double_hip_flexion_lat_stretch') && u2.result_type && u2.result_type !== 'FN') {
    enabled.add('supine_double_hip_extension_lat_stretch')
  }

  const u3 = getNodeRef(analysis, 'supine_double_hip_extension_lat_stretch')
  u3.result_type = String(u3.result_type || '')
  u3.result_code = u3.result_type
  u3.pain_present = Boolean(u3.pain_present) || u3.result_type === 'FP' || u3.result_type === 'DP'
  u3.summary_text = u3.result_type ? `仰卧位双髋伸展背阔肌拉伸：${u3.result_type}。` : '仰卧位双髋伸展背阔肌拉伸尚未录入。'
  if (!stopAndTreatPain && enabled.has('supine_double_hip_extension_lat_stretch') && u3.result_type) {
    enabled.add('lumbar_fixed_external_rotation_extension')
  }

  const u4 = getNodeRef(analysis, 'lumbar_fixed_external_rotation_extension')
  u4.result_type = asClassification(u4.result_type)
  u4.result_code = u4.result_type
  u4.pain_present = Boolean(u4.pain_present) || isPainResult(u4.result_type)
  u4.summary_text = u4.result_type ? `腰部固定（外旋）旋转/伸展：${u4.result_type}。` : '腰部固定（外旋）旋转/伸展尚未录入。'
  if (!stopAndTreatPain && enabled.has('lumbar_fixed_external_rotation_extension') && u4.result_type && u4.result_type !== 'FN') {
    enabled.add('lumbar_fixed_internal_rotation_active_extension_rotation_upper')
  }

  const u5 = getNodeRef(analysis, 'lumbar_fixed_internal_rotation_active_extension_rotation_upper')
  u5.result_type = asClassification(u5.result_type)
  u5.result_code = u5.result_type
  u5.pain_present = Boolean(u5.pain_present) || isPainResult(u5.result_type)
  u5.summary_text = u5.result_type
    ? `腰部固定（内旋）主动旋转/伸展（上半身）：${u5.result_type}。`
    : '腰部固定（内旋）主动旋转/伸展（上半身）尚未录入。'
  if (!stopAndTreatPain && enabled.has('lumbar_fixed_internal_rotation_active_extension_rotation_upper') && u5.result_type && u5.result_type !== 'FN') {
    enabled.add('lumbar_fixed_internal_rotation_passive_extension_rotation_upper')
  }

  const u6 = getNodeRef(analysis, 'lumbar_fixed_internal_rotation_passive_extension_rotation_upper')
  u6.result_type = String(u6.result_type || '')
  u6.result_code = u6.result_type
  u6.pain_present = Boolean(u6.pain_present) || u6.result_type === 'FP' || u6.result_type === 'DP'
  u6.summary_text = u6.result_type
    ? `腰部固定（内旋）被动旋转/伸展（上半身）：${u6.result_type}。`
    : '腰部固定（内旋）被动旋转/伸展（上半身）尚未录入。'
  if (!stopAndTreatPain && enabled.has('lumbar_fixed_internal_rotation_passive_extension_rotation_upper') && (u6.result_type === 'FP' || u6.result_type === 'DP')) {
    stopAndTreatPain = true
  }

  const summary = analysis.summary
  summary.thoracic_extension_issue = thoracicIssue || ['unilateral_DN', 'bilateral_DN'].includes(String(n5.result_type))
  summary.lumbar_extension_issue = lumbarIssue || ['unilateral_DN', 'bilateral_DN'].includes(String(n6.result_type))
  summary.weight_bearing_stability_issue = weightBearingIssue
  summary.pain_dominant = stopAndTreatPain || MSE_FLOW_NODE_ORDER.some((code) => {
    const node = getNodeRef(analysis, code)
    return Boolean(node.pain_present) || node.result_type === 'FP' || node.result_type === 'DP'
  })
  summary.upper_body_extension_flow_needed = upperFlowNeeded
  summary.lower_body_extension_flow_needed = lowerFlowNeeded
  summary.next_flow_targets = [
    ...(upperFlowNeeded ? ['upper_body_extension_flow'] : []),
    ...(lowerFlowNeeded ? ['lower_body_extension_flow'] : [])
  ]
  summary.stop_and_treat_pain = stopAndTreatPain
  summary.manual_review_required = summary.pain_dominant || Boolean(payload.needs_manual_review)

  const likely: string[] = []
  if (summary.thoracic_extension_issue) likely.push('当前更像胸椎伸展问题')
  if (summary.lumbar_extension_issue) likely.push('当前更像腰椎伸展问题')
  if (summary.weight_bearing_stability_issue) likely.push('当前更像负重下脊柱伸展稳定/运动控制问题')
  if (summary.upper_body_extension_flow_needed) likely.push('当前应继续进入上半身伸展流程')
  if (summary.lower_body_extension_flow_needed) likely.push('当前应继续进入下半身伸展流程')
  if (summary.stop_and_treat_pain) likely.push('当前应停止解析并优先处理疼痛')
  summary.likely_pattern = likely
  summary.primary_region = summary.thoracic_extension_issue
    ? '胸椎伸展链'
    : summary.lumbar_extension_issue
      ? '腰椎伸展链'
      : summary.weight_bearing_stability_issue
        ? '负重伸展控制链'
        : ''
  summary.summary_text = summary.stop_and_treat_pain
    ? '当前解析在疼痛性结果处停止，建议优先处理疼痛后再继续。'
    : likely.length
      ? `多节段伸展解析提示：${likely.join('；')}。`
      : '当前数据不足以形成明确伸展流程结论，建议继续补充分解节点并结合人工复核。'

  const pendingNode = MSE_FLOW_NODE_ORDER.find((code) => enabled.has(code) && !nodeCompleted(getNodeRef(analysis, code)))
  const nextStep = (() => {
    if (payload.breakout_status === 'skipped') return ''
    if (stopAndTreatPain) return '停止并优先处理疼痛'
    if (pendingNode) return `继续${NODE_META[pendingNode].node_name_zh}`
    const anyInput = MSE_FLOW_NODE_ORDER.some((code) => hasMseNodeValue(payload, code))
    return anyInput ? '流程已完成' : ''
  })()

  return {
    analysis,
    enabledNodes: enabled,
    nextStep,
    stopAndTreatPain,
    currentNode: pendingNode || null,
    nodeRules: NODE_META
  }
}
