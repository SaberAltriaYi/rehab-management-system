import { buildDefaultMsfAnalysis, SfmaClassification, SfmaMsfAnalysis, SfmaMsfBreakout } from '@/views/rehab/assessment/config/sfmaConfig'

export type MsfFlowNodeCode =
  | 'single_leg_stance_forward_bend'
  | 'long_sit_toe_touch'
  | 'active_straight_leg_raise'
  | 'passive_straight_leg_raise'
  | 'prone_backward_rocking'
  | 'supine_double_knees_to_chest'
  | 'rolling_analysis_result'

export type MsfFlowResultCode =
  | SfmaClassification
  | 'DN_or_FP_or_DP'
  | 'bilateral_FN'
  | 'bilateral_abnormal_or_pain'
  | 'unilateral_abnormal_or_pain'
  | 'sacrum_normal'
  | 'sacrum_limited'
  | 'fn_and_sacrum_normal'
  | 'abnormal_with_sacrum_normal'
  | 'abnormal_with_sacrum_limited'
  | 'fn_gt_80'
  | 'fn_gap_gt_10_and_lt_80'
  | 'fp_or_dp'
  | 'dn_pslr_lte_aslr'
  | ''

interface MsfRuleRow {
  step_no: number
  node_code: MsfFlowNodeCode
  node_name_zh: string
  purpose: string
  input_fields: string[]
  result_rules: Array<{ if: string; result_code: MsfFlowResultCode }>
  next_step_rules: Array<{ when_result?: MsfFlowResultCode; when_result_in?: MsfFlowResultCode[]; next_step: string }>
  output_meaning: string[] | Record<string, string>
  instructions: string
  clinical_notes: string
  stop_if_pain: boolean
}

type FlowNodes = SfmaMsfAnalysis['flow_nodes']

const TEST_RESULTS: SfmaClassification[] = ['FN', 'FP', 'DN', 'DP']
const PAIN_RESULTS: SfmaClassification[] = ['FP', 'DP']

export const MSF_FLOW_NODE_ORDER: Array<{ code: MsfFlowNodeCode; label: string }> = [
  { code: 'single_leg_stance_forward_bend', label: '单腿站立体前屈' },
  { code: 'long_sit_toe_touch', label: '长坐位触摸足趾' },
  { code: 'active_straight_leg_raise', label: '主动直腿抬高（ASLR）' },
  { code: 'passive_straight_leg_raise', label: '被动直腿抬高（PSLR）' },
  { code: 'prone_backward_rocking', label: '俯卧位向后摆动' },
  { code: 'supine_double_knees_to_chest', label: '仰卧位双膝触胸' },
  { code: 'rolling_analysis_result', label: '滚动解析结果' }
]

export const MSF_NODE_PANEL_MAP: Record<MsfFlowNodeCode, string> = {
  single_leg_stance_forward_bend: 'single-leg-forward-flexion',
  long_sit_toe_touch: 'long-sit-toe-touch',
  active_straight_leg_raise: 'aslr',
  passive_straight_leg_raise: 'pslr',
  prone_backward_rocking: 'prone-rock-back',
  supine_double_knees_to_chest: 'supine-knees-to-chest',
  rolling_analysis_result: 'rolling'
}

export const MSF_RULE_TABLE: MsfRuleRow[] = [
  {
    step_no: 1,
    node_code: 'single_leg_stance_forward_bend',
    node_name_zh: '单腿站立体前屈',
    purpose: '判断多节段屈曲问题更偏双侧还是单侧，并作为疼痛诱发筛查。',
    input_fields: ['left_result', 'right_result', 'pain_present', 'note'],
    result_rules: [
      { if: "left_result == 'FN' AND right_result == 'FN' AND pain_present == false", result_code: 'bilateral_FN' },
      { if: "(left_result IN ['DN','DP','FP'] AND right_result IN ['DN','DP','FP']) OR pain_present == true", result_code: 'bilateral_abnormal_or_pain' },
      { if: "(left_result IN ['DN','DP','FP'] XOR right_result IN ['DN','DP','FP'])", result_code: 'unilateral_abnormal_or_pain' }
    ],
    next_step_rules: [{ when_result_in: ['bilateral_FN', 'bilateral_abnormal_or_pain', 'unilateral_abnormal_or_pain'], next_step: 'long_sit_toe_touch' }],
    output_meaning: ['记录是双侧问题还是单侧问题', '不在此步终止流程'],
    instructions: '一侧脚蹬在台阶上，对侧膝伸直，双手相叠前屈并触碰支撑腿同侧足趾，双侧重复。',
    clinical_notes: '该节点用于暴露单侧前屈问题，无论结果如何都进入长坐位触摸足趾。',
    stop_if_pain: false
  },
  {
    step_no: 2,
    node_code: 'long_sit_toe_touch',
    node_name_zh: '长坐位触摸足趾',
    purpose: '区分负重下稳定性问题、髋屈曲受限、脊柱屈曲受限或后链问题。',
    input_fields: ['can_touch_toes', 'sacral_angle_deg', 'pain_present', 'note'],
    result_rules: [
      { if: 'can_touch_toes == true AND sacral_angle_deg >= 80 AND pain_present == false', result_code: 'fn_and_sacrum_normal' },
      { if: 'sacral_angle_deg >= 80 AND (pain_present == true OR can_touch_toes == false)', result_code: 'abnormal_with_sacrum_normal' },
      { if: 'sacral_angle_deg < 80', result_code: 'abnormal_with_sacrum_limited' }
    ],
    next_step_rules: [
      { when_result: 'fn_and_sacrum_normal', next_step: 'rolling_analysis_result' },
      { when_result: 'abnormal_with_sacrum_normal', next_step: 'prone_backward_rocking' },
      { when_result: 'abnormal_with_sacrum_limited', next_step: 'active_straight_leg_raise' }
    ],
    output_meaning: {
      fn_and_sacrum_normal: '更支持负重下髋关节稳定性/协调/时序问题。',
      abnormal_with_sacrum_normal: '更支持负重下脊柱稳定性问题或脊柱灵活性差。',
      abnormal_with_sacrum_limited: '更支持髋关节屈曲受限、脊柱屈曲受限或两者兼有。'
    },
    instructions: '长坐位双下肢伸直前屈触趾，记录能否触趾、骶骨角度与疼痛表现。',
    clinical_notes: '骶骨角度80°为关键阈值。',
    stop_if_pain: false
  },
  {
    step_no: 3,
    node_code: 'active_straight_leg_raise',
    node_name_zh: '主动直腿抬高',
    purpose: '评估膝伸直状态下髋关节主动屈曲能力。',
    input_fields: ['left_aslr_deg', 'right_aslr_deg', 'pain_present', 'note'],
    result_rules: [
      { if: 'left_aslr_deg > 70 AND right_aslr_deg > 70 AND pain_present == false', result_code: 'FN' },
      { if: 'left_aslr_deg <= 70 OR right_aslr_deg <= 70 OR pain_present == true', result_code: 'DN_or_FP_or_DP' }
    ],
    next_step_rules: [
      { when_result: 'FN', next_step: 'prone_backward_rocking' },
      { when_result: 'DN_or_FP_or_DP', next_step: 'passive_straight_leg_raise' }
    ],
    output_meaning: {
      FN: '主动屈髋基本正常，继续俯卧位向后摆动。',
      DN_or_FP_or_DP: '需进一步用被动直腿抬高区分后链/髋灵活性与核心控制问题。'
    },
    instructions: '仰卧位记录左右抬高角度，非测试侧膝保持贴床，正常参考角度大于70°。',
    clinical_notes: 'ASLR 与 PSLR 联合用于区分结构性限制与控制问题。',
    stop_if_pain: false
  },
  {
    step_no: 4,
    node_code: 'passive_straight_leg_raise',
    node_name_zh: '被动直腿抬高',
    purpose: '区分后链组织延展性问题、髋关节灵活性问题和核心/主动屈髋控制问题。',
    input_fields: ['left_pslr_deg', 'right_pslr_deg', 'left_aslr_deg', 'right_aslr_deg', 'pain_present', 'note'],
    result_rules: [
      { if: 'left_pslr_deg > 80 AND right_pslr_deg > 80 AND pain_present == false', result_code: 'fn_gt_80' },
      { if: "left_pslr_deg < 80 AND right_pslr_deg < 80 AND ((left_pslr_deg - left_aslr_deg) > 10 OR (right_pslr_deg - right_aslr_deg) > 10) AND pain_present == false", result_code: 'fn_gap_gt_10_and_lt_80' },
      { if: 'pain_present == true', result_code: 'fp_or_dp' },
      { if: '(left_pslr_deg <= left_aslr_deg OR right_pslr_deg <= right_aslr_deg) AND pain_present == false', result_code: 'dn_pslr_lte_aslr' }
    ],
    next_step_rules: [
      { when_result: 'fn_gt_80', next_step: 'rolling_analysis_result' },
      { when_result: 'fn_gap_gt_10_and_lt_80', next_step: 'supine_double_knees_to_chest' },
      { when_result: 'fp_or_dp', next_step: 'STOP_PAIN' },
      { when_result: 'dn_pslr_lte_aslr', next_step: 'supine_double_knees_to_chest' }
    ],
    output_meaning: {
      fn_gt_80: '更支持核心部位SMCD和/或主动屈髋SMCD，继续滚动解析。',
      fn_gap_gt_10_and_lt_80: '更支持核心部位SMCD，继续双膝触胸排查后链/髋灵活性。',
      fp_or_dp: '疼痛主导，停止当前流程，优先处理疼痛。',
      dn_pslr_lte_aslr: '更支持髋关节JMD和/或后链TED，继续双膝触胸。'
    },
    instructions: '仰卧位治疗师被动抬高下肢，保持膝伸直与骨盆稳定，记录角度并与ASLR比较。',
    clinical_notes: 'PSLR 与 ASLR 差值用于识别控制型与结构型限制。',
    stop_if_pain: true
  },
  {
    step_no: 5,
    node_code: 'prone_backward_rocking',
    node_name_zh: '俯卧位向后摆动',
    purpose: '判断不负重/脊柱不负重姿势下的脊柱屈曲能力。',
    input_fields: ['result', 'pain_present', 'note'],
    result_rules: [
      { if: "result == 'FN' AND pain_present == false", result_code: 'FN' },
      { if: "result == 'DN' AND pain_present == false", result_code: 'DN' },
      { if: "result IN ['FP','DP'] OR pain_present == true", result_code: 'fp_or_dp' }
    ],
    next_step_rules: [
      { when_result: 'FN', next_step: 'END' },
      { when_result: 'DN', next_step: 'END' },
      { when_result: 'fp_or_dp', next_step: 'STOP_PAIN' }
    ],
    output_meaning: {
      FN: '输出：负重脊柱屈曲SMCD。',
      DN: '输出：脊柱关节灵活性异常（JMD）和/或组织延展性异常（TED）。',
      fp_or_dp: '停止流程，优先处理疼痛。'
    },
    instructions: '胸膝位向后摆，观察臀部贴近足跟与胸廓触大腿情况。',
    clinical_notes: '用于评估非负重位脊柱屈曲能力。',
    stop_if_pain: true
  },
  {
    step_no: 6,
    node_code: 'rolling_analysis_result',
    node_name_zh: '滚动解析结果',
    purpose: '区分负重髋关节屈曲模式SMCD与基础屈曲动作模式SMCD。',
    input_fields: ['result', 'pain_present', 'note'],
    result_rules: [
      { if: "result == 'FN' AND pain_present == false", result_code: 'FN' },
      { if: "result == 'DN' AND pain_present == false", result_code: 'DN' },
      { if: "result IN ['FP','DP'] OR pain_present == true", result_code: 'fp_or_dp' }
    ],
    next_step_rules: [
      { when_result: 'FN', next_step: 'END' },
      { when_result: 'DN', next_step: 'END' },
      { when_result: 'fp_or_dp', next_step: 'STOP_PAIN' }
    ],
    output_meaning: {
      FN: '输出：负重髋关节屈曲模式稳定性和运动控制功能异常（SMCD）。',
      DN: '输出：基础屈曲动作模式SMCD。',
      fp_or_dp: '停止流程，优先处理疼痛。'
    },
    instructions: '可接入滚动测试结果；未接入时先记录占位结果。',
    clinical_notes: '用于细化SMCD方向。',
    stop_if_pain: true
  },
  {
    step_no: 7,
    node_code: 'supine_double_knees_to_chest',
    node_name_zh: '仰卧位双手抱大腿膝触胸',
    purpose: '判断不负重状态下的髋关节灵活性与后链组织延展性。',
    input_fields: ['result', 'pain_present', 'note'],
    result_rules: [
      { if: "result == 'FN' AND pain_present == false", result_code: 'FN' },
      { if: "result == 'DN' AND pain_present == false", result_code: 'DN' },
      { if: "result IN ['FP','DP'] OR pain_present == true", result_code: 'fp_or_dp' }
    ],
    next_step_rules: [
      { when_result: 'FN', next_step: 'END' },
      { when_result: 'DN', next_step: 'END' },
      { when_result: 'fp_or_dp', next_step: 'STOP_PAIN' }
    ],
    output_meaning: {
      FN: '输出：后链TED和/或主动屈髋SMCD。',
      DN: '输出：髋关节JMD和/或后链TED。',
      fp_or_dp: '停止流程，优先处理疼痛。'
    },
    instructions: '仰卧位双膝抱胸，观察大腿是否可压近胸部及疼痛表现。',
    clinical_notes: '用于区分髋关节灵活性与后链组织延展性问题。',
    stop_if_pain: true
  }
]

const asResult = (value: unknown): SfmaClassification => {
  const next = String(value || '') as SfmaClassification
  return TEST_RESULTS.includes(next) ? next : ''
}

const hasPainByResult = (value: unknown) => PAIN_RESULTS.includes(asResult(value))

const toNumber = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') return null
  const next = Number(value)
  return Number.isFinite(next) ? next : null
}

export const hasMsfNodeValue = (payload: SfmaMsfBreakout, node: MsfFlowNodeCode) => {
  switch (node) {
    case 'single_leg_stance_forward_bend':
      return Boolean(payload.single_leg_standing_forward_flexion_result)
    case 'long_sit_toe_touch':
      return Boolean(
        payload.long_sit_toe_touch_result ||
        payload.long_sit_sacral_angle_deg != null ||
        (payload.long_sit_toe_touch_reach_status && payload.long_sit_toe_touch_reach_status !== '未测') ||
        (payload.long_sit_sacral_angle_status && payload.long_sit_sacral_angle_status !== '未测')
      )
    case 'active_straight_leg_raise':
      return Boolean(payload.aslr_result || payload.aslr_left_deg != null || payload.aslr_right_deg != null)
    case 'passive_straight_leg_raise':
      return Boolean(payload.pslr_result || payload.pslr_left_deg != null || payload.pslr_right_deg != null)
    case 'prone_backward_rocking':
      return Boolean(payload.prone_rock_back_result)
    case 'supine_double_knees_to_chest':
      return Boolean(payload.supine_knees_to_chest_result)
    case 'rolling_analysis_result':
      return Boolean(payload.rolling_result)
    default:
      return false
  }
}

const isNodeCompleted = (nodes: FlowNodes, node: MsfFlowNodeCode) => {
  const resultCode = String(nodes[node]?.result_code || nodes[node]?.result_type || '').trim()
  return resultCode.length > 0
}

const cloneFlowNodes = (source: FlowNodes): FlowNodes => JSON.parse(JSON.stringify(source))

const getSingleLegResultType = (payload: SfmaMsfBreakout): MsfFlowResultCode => {
  if (!payload.single_leg_standing_forward_flexion_result) return ''
  if (payload.single_leg_standing_forward_flexion_result === '双侧功能正常且无痛') return 'bilateral_FN'
  if (payload.single_leg_standing_forward_flexion_result === '单侧功能障碍或疼痛') return 'unilateral_abnormal_or_pain'
  return 'bilateral_abnormal_or_pain'
}

const getSacralAngleStatus = (payload: SfmaMsfBreakout): '' | 'normal' | 'limited' => {
  const angle = toNumber(payload.long_sit_sacral_angle_deg)
  const explicit = payload.long_sit_sacral_angle_status
  if (explicit === '正常(≥80°)') return 'normal'
  if (explicit === '受限(<80°)') return 'limited'
  if (angle == null) return ''
  return angle >= (payload.sacral_angle_threshold_ref || 80) ? 'normal' : 'limited'
}

const getLongSitResultType = (payload: SfmaMsfBreakout): MsfFlowResultCode => {
  const touchToes = payload.long_sit_toe_touch_reach_status === '可触及足趾'
  const sacralStatus = getSacralAngleStatus(payload)
  const painPresent = hasPainByResult(payload.long_sit_toe_touch_result)
  if (!sacralStatus) return ''
  if (touchToes && sacralStatus === 'normal' && !painPresent) return 'fn_and_sacrum_normal'
  if (sacralStatus === 'normal' && (painPresent || !touchToes)) return 'abnormal_with_sacrum_normal'
  if (sacralStatus === 'limited') return 'abnormal_with_sacrum_limited'
  return ''
}

const getAslrResultType = (payload: SfmaMsfBreakout): MsfFlowResultCode => {
  const left = toNumber(payload.aslr_left_deg)
  const right = toNumber(payload.aslr_right_deg)
  const painPresent = hasPainByResult(payload.aslr_result)
  if (left == null || right == null) {
    return asResult(payload.aslr_result) ? 'DN_or_FP_or_DP' : ''
  }
  if (left > (payload.aslr_threshold_ref || 70) && right > (payload.aslr_threshold_ref || 70) && !painPresent) return 'FN'
  return 'DN_or_FP_or_DP'
}

const getPslrResultType = (payload: SfmaMsfBreakout): MsfFlowResultCode => {
  const leftPslr = toNumber(payload.pslr_left_deg)
  const rightPslr = toNumber(payload.pslr_right_deg)
  const leftAslr = toNumber(payload.aslr_left_deg)
  const rightAslr = toNumber(payload.aslr_right_deg)
  const painPresent = hasPainByResult(payload.pslr_result)
  if (painPresent) return 'fp_or_dp'
  if (leftPslr == null || rightPslr == null) return ''
  if (leftPslr > (payload.pslr_threshold_ref || 80) && rightPslr > (payload.pslr_threshold_ref || 80)) return 'fn_gt_80'
  const hasGap = (leftPslr < (payload.pslr_threshold_ref || 80) && leftAslr != null && leftPslr - leftAslr > 10) ||
    (rightPslr < (payload.pslr_threshold_ref || 80) && rightAslr != null && rightPslr - rightAslr > 10)
  if (leftPslr < (payload.pslr_threshold_ref || 80) && rightPslr < (payload.pslr_threshold_ref || 80) && hasGap) return 'fn_gap_gt_10_and_lt_80'
  if ((leftAslr != null && leftPslr <= leftAslr) || (rightAslr != null && rightPslr <= rightAslr)) return 'dn_pslr_lte_aslr'
  return ''
}

const getSimpleNodeResultType = (result: unknown): MsfFlowResultCode => {
  const value = asResult(result)
  if (!value) return ''
  return PAIN_RESULTS.includes(value) ? 'fp_or_dp' : value
}

const buildLikelyPattern = (summary: SfmaMsfAnalysis['summary']): string[] => {
  const list: string[] = []
  if (summary.loaded_flexion_smcd_issue) list.push('更像负重下屈曲模式稳定/运动控制问题')
  if (summary.hip_flexion_mobility_issue) list.push('更像髋关节屈曲灵活性问题')
  if (summary.posterior_chain_ted_issue) list.push('更像后链组织延展性问题')
  if (summary.spinal_flexion_mobility_issue) list.push('更像脊柱屈曲灵活性问题')
  if (summary.core_or_active_hip_flexion_smcd_issue) list.push('更像核心稳定/主动屈髋控制问题')
  if (summary.base_flexion_pattern_smcd_issue) list.push('更像基础屈曲动作模式SMCD问题')
  if (summary.rotation_flow_needed) list.push('建议继续进入旋转动作解析')
  if (summary.stop_and_treat_pain) list.push('当前应停止解析并优先处理疼痛')
  return list
}

const buildSummaryText = (summary: SfmaMsfAnalysis['summary'], findings: string[]): string => {
  if (summary.stop_and_treat_pain) {
    return '当前解析在疼痛性结果处停止，建议优先处理疼痛后再继续。'
  }
  if (!findings.length) {
    return '当前数据不足以形成明确流程结论，建议继续补充流程节点并结合人工复核。'
  }
  return `多节段屈曲解析提示：${findings.join('；')}。`
}

const patchNodeMeta = (analysis: SfmaMsfAnalysis) => {
  const nodes = analysis.flow_nodes
  MSF_RULE_TABLE.forEach((rule) => {
    const node = nodes[rule.node_code]
    node.node_code = rule.node_code
    node.node_name_zh = rule.node_name_zh
    node.purpose = rule.purpose
    node.instructions = rule.instructions
    node.clinical_notes = rule.clinical_notes
    node.next_step_rules = rule.next_step_rules
      .map((next) => {
        if (next.when_result) {
          return `${next.when_result}→${next.next_step}`
        }
        return `${(next.when_result_in || []).join('/')}→${next.next_step}`
      })
      .join('；')
    node.input_fields = [...rule.input_fields]
    node.output_meaning = JSON.parse(JSON.stringify(rule.output_meaning))
    node.stop_if_pain = rule.stop_if_pain
  })
}

export const runMsfAnalysisFlowEngine = (payload: SfmaMsfBreakout) => {
  const analysis = buildDefaultMsfAnalysis()
  patchNodeMeta(analysis)

  const nodes = analysis.flow_nodes
  const summary = analysis.summary
  const enabledNodes = new Set<MsfFlowNodeCode>(['single_leg_stance_forward_bend'])

  const singleLegNode = nodes.single_leg_stance_forward_bend
  singleLegNode.result = payload.single_leg_standing_forward_flexion_result || ''
  singleLegNode.left_result = payload.single_leg_standing_forward_flexion_asymmetry === '左侧更差' ? 'DN' : 'FN'
  singleLegNode.right_result = payload.single_leg_standing_forward_flexion_asymmetry === '右侧更差' ? 'DN' : 'FN'
  singleLegNode.bilateral_summary = payload.single_leg_standing_forward_flexion_result || ''
  singleLegNode.pain_present = payload.single_leg_standing_forward_flexion_result.includes('疼痛')
  singleLegNode.note = payload.single_leg_standing_forward_flexion_note || ''
  singleLegNode.result_type = getSingleLegResultType(payload)
  singleLegNode.result_code = singleLegNode.result_type
  singleLegNode.summary_text = singleLegNode.result
    ? `单腿站立体前屈：${singleLegNode.result}${payload.single_leg_standing_forward_flexion_asymmetry ? `（${payload.single_leg_standing_forward_flexion_asymmetry}）` : ''}。`
    : '单腿站立体前屈尚未录入。'

  if (singleLegNode.result_type) enabledNodes.add('long_sit_toe_touch')

  const longSitNode = nodes.long_sit_toe_touch
  const longSitResult = asResult(payload.long_sit_toe_touch_result)
  const longSitResultType = getLongSitResultType(payload)
  const sacralDeg = toNumber(payload.long_sit_sacral_angle_deg)
  const sacralStatus = getSacralAngleStatus(payload)
  longSitNode.result = longSitResult
  longSitNode.can_touch_toes = payload.long_sit_toe_touch_reach_status === '可触及足趾'
  longSitNode.sacral_angle_deg = sacralDeg
  longSitNode.sacral_angle_status = sacralStatus === 'normal' ? 'normal' : sacralStatus === 'limited' ? 'limited' : ''
  longSitNode.pain_present = hasPainByResult(longSitResult)
  longSitNode.note = payload.long_sit_toe_touch_note || ''
  longSitNode.result_type = longSitResultType
  longSitNode.result_code = longSitResultType
  longSitNode.summary_text = longSitResultType
    ? `长坐位触趾结果：${longSitResultType}${sacralDeg != null ? `（骶骨角${sacralDeg}°）` : ''}。`
    : '长坐位触趾尚未形成分流结果。'

  if (enabledNodes.has('long_sit_toe_touch') && longSitResultType) {
    if (longSitResultType === 'fn_and_sacrum_normal') {
      summary.rotation_flow_needed = true
      enabledNodes.add('rolling_analysis_result')
    } else if (longSitResultType === 'abnormal_with_sacrum_normal') {
      enabledNodes.add('prone_backward_rocking')
    } else if (longSitResultType === 'abnormal_with_sacrum_limited') {
      enabledNodes.add('active_straight_leg_raise')
    }
  }

  const aslrNode = nodes.active_straight_leg_raise
  const aslrResult = asResult(payload.aslr_result)
  const aslrResultType = getAslrResultType(payload)
  aslrNode.result = aslrResult
  aslrNode.left_aslr_deg = toNumber(payload.aslr_left_deg)
  aslrNode.right_aslr_deg = toNumber(payload.aslr_right_deg)
  aslrNode.bilateral_summary = aslrResult
  aslrNode.pain_present = hasPainByResult(aslrResult)
  aslrNode.note = payload.aslr_note || ''
  aslrNode.result_type = aslrResultType
  aslrNode.result_code = aslrResultType
  aslrNode.summary_text = aslrResultType
    ? `ASLR 分流：${aslrResultType}${aslrNode.left_aslr_deg != null || aslrNode.right_aslr_deg != null ? `（左${aslrNode.left_aslr_deg ?? '-'}°/右${aslrNode.right_aslr_deg ?? '-'}°）` : ''}。`
    : 'ASLR 尚未形成分流结果。'

  if (enabledNodes.has('active_straight_leg_raise') && aslrResultType) {
    if (aslrResultType === 'FN') {
      enabledNodes.add('prone_backward_rocking')
    } else {
      enabledNodes.add('passive_straight_leg_raise')
    }
  }

  const pslrNode = nodes.passive_straight_leg_raise
  const pslrResult = asResult(payload.pslr_result)
  const pslrResultType = getPslrResultType(payload)
  pslrNode.result = pslrResult
  pslrNode.left_pslr_deg = toNumber(payload.pslr_left_deg)
  pslrNode.right_pslr_deg = toNumber(payload.pslr_right_deg)
  pslrNode.pain_present = pslrResultType === 'fp_or_dp'
  pslrNode.note = payload.pslr_note || ''
  pslrNode.result_type = pslrResultType
  pslrNode.result_code = pslrResultType
  pslrNode.summary_text = pslrResultType
    ? `PSLR 分流：${pslrResultType}。`
    : 'PSLR 尚未形成分流结果。'

  let stopAndTreatPain = false
  if (enabledNodes.has('passive_straight_leg_raise') && pslrResultType) {
    if (pslrResultType === 'fp_or_dp') {
      stopAndTreatPain = true
    } else if (pslrResultType === 'fn_gt_80') {
      enabledNodes.add('rolling_analysis_result')
    } else if (pslrResultType === 'fn_gap_gt_10_and_lt_80' || pslrResultType === 'dn_pslr_lte_aslr') {
      enabledNodes.add('supine_double_knees_to_chest')
    }
  }

  const proneNode = nodes.prone_backward_rocking
  const proneResult = asResult(payload.prone_rock_back_result)
  const proneResultType = getSimpleNodeResultType(proneResult)
  proneNode.result = proneResult
  proneNode.pain_present = hasPainByResult(proneResult)
  proneNode.note = payload.prone_rock_back_note || ''
  proneNode.result_type = proneResultType
  proneNode.result_code = proneResultType
  proneNode.summary_text = proneResultType ? `俯卧位向后摆动结果：${proneResultType}。` : '俯卧位向后摆动尚未录入。'

  if (!stopAndTreatPain && enabledNodes.has('prone_backward_rocking') && proneResultType === 'fp_or_dp') {
    stopAndTreatPain = true
  }

  const supineNode = nodes.supine_double_knees_to_chest
  const supineResult = asResult(payload.supine_knees_to_chest_result)
  const supineResultType = getSimpleNodeResultType(supineResult)
  supineNode.result = supineResult
  supineNode.pain_present = hasPainByResult(supineResult)
  supineNode.note = payload.supine_knees_to_chest_note || ''
  supineNode.result_type = supineResultType
  supineNode.result_code = supineResultType
  supineNode.summary_text = supineResultType ? `双膝触胸结果：${supineResultType}。` : '双膝触胸尚未录入。'

  if (!stopAndTreatPain && enabledNodes.has('supine_double_knees_to_chest') && supineResultType === 'fp_or_dp') {
    stopAndTreatPain = true
  }

  const rollingNode = nodes.rolling_analysis_result
  const rollingResult = asResult(payload.rolling_result)
  const rollingResultType = getSimpleNodeResultType(rollingResult)
  rollingNode.result = rollingResult
  rollingNode.pain_present = hasPainByResult(rollingResult)
  rollingNode.note = payload.rolling_note || ''
  rollingNode.result_type = rollingResultType
  rollingNode.result_code = rollingResultType
  rollingNode.summary_text = rollingResultType ? `滚动解析结果：${rollingResultType}。` : '滚动解析结果尚未录入。'

  if (!stopAndTreatPain && enabledNodes.has('rolling_analysis_result') && rollingResultType === 'fp_or_dp') {
    stopAndTreatPain = true
  }

  summary.single_vs_bilateral_pattern = singleLegNode.result_type || ''
  const nodeEnabled = (code: MsfFlowNodeCode) => enabledNodes.has(code)
  const longSitActive = nodeEnabled('long_sit_toe_touch')
  const pslrActive = nodeEnabled('passive_straight_leg_raise')
  const proneActive = nodeEnabled('prone_backward_rocking')
  const supineActive = nodeEnabled('supine_double_knees_to_chest')
  const rollingActive = nodeEnabled('rolling_analysis_result')

  summary.rotation_flow_needed = longSitActive && longSitResultType === 'fn_and_sacrum_normal'
  summary.stop_and_treat_pain = stopAndTreatPain
  summary.manual_review_required = stopAndTreatPain || payload.needs_manual_review

  if (longSitActive && longSitResultType === 'abnormal_with_sacrum_limited') {
    summary.hip_flexion_mobility_issue = true
    summary.spinal_flexion_mobility_issue = true
  }
  if (longSitActive && longSitResultType === 'abnormal_with_sacrum_normal') {
    summary.spinal_flexion_mobility_issue = true
  }
  if (longSitActive && rollingActive && longSitResultType === 'fn_and_sacrum_normal' && rollingResultType === 'FN') {
    summary.loaded_flexion_smcd_issue = true
  }
  if (longSitActive && rollingActive && longSitResultType === 'fn_and_sacrum_normal' && rollingResultType === 'DN') {
    summary.base_flexion_pattern_smcd_issue = true
  }
  if (proneActive && proneResultType === 'FN') {
    summary.loaded_flexion_smcd_issue = true
  }
  if (proneActive && proneResultType === 'DN') {
    summary.spinal_flexion_mobility_issue = true
  }
  if (pslrActive && (pslrResultType === 'fn_gt_80' || pslrResultType === 'fn_gap_gt_10_and_lt_80')) {
    summary.core_or_active_hip_flexion_smcd_issue = true
  }
  if (pslrActive && pslrResultType === 'dn_pslr_lte_aslr') {
    summary.posterior_chain_ted_issue = true
    summary.hip_flexion_mobility_issue = true
  }
  if (supineActive && supineResultType === 'FN') {
    summary.posterior_chain_ted_issue = true
    summary.core_or_active_hip_flexion_smcd_issue = true
  }
  if (supineActive && supineResultType === 'DN') {
    summary.hip_flexion_mobility_issue = true
    summary.posterior_chain_ted_issue = true
  }

  const findingTexts: string[] = []
  if (longSitActive && longSitResultType === 'fn_and_sacrum_normal') {
    findingTexts.push('更支持负重下髋关节稳定性问题、协调性差或时序性不佳')
  }
  if (longSitActive && longSitResultType === 'abnormal_with_sacrum_normal') {
    findingTexts.push('更支持负重下脊柱稳定性问题或脊柱灵活性不足')
  }
  if (longSitActive && longSitResultType === 'abnormal_with_sacrum_limited') {
    findingTexts.push('更支持髋屈曲受限、脊柱屈曲受限或两者兼有')
  }
  if (pslrActive && pslrResultType === 'fn_gt_80') {
    findingTexts.push('更支持核心稳定性或主动屈髋控制问题')
  }
  if (pslrActive && pslrResultType === 'fn_gap_gt_10_and_lt_80') {
    findingTexts.push('更支持核心稳定/控制问题，同时需继续排查后链或髋灵活性')
  }
  if (pslrActive && pslrResultType === 'dn_pslr_lte_aslr') {
    findingTexts.push('更支持后链紧张/僵硬或髋关节灵活性不足')
  }
  if (proneActive && proneResultType === 'FN') {
    findingTexts.push('负重下脊柱屈曲SMCD倾向')
  }
  if (proneActive && proneResultType === 'DN') {
    findingTexts.push('脊柱JMD和/或TED倾向')
  }
  if (supineActive && supineResultType === 'FN') {
    findingTexts.push('后链TED和/或主动屈髋SMCD倾向')
  }
  if (supineActive && supineResultType === 'DN') {
    findingTexts.push('髋关节JMD和/或后链TED倾向')
  }
  if (rollingActive && rollingResultType === 'FN') {
    findingTexts.push('负重髋关节屈曲模式SMCD倾向')
  }
  if (rollingActive && rollingResultType === 'DN') {
    findingTexts.push('基础屈曲动作模式SMCD倾向')
  }

  summary.likely_pattern = buildLikelyPattern(summary)
  summary.primary_region = summary.hip_flexion_mobility_issue
    ? '髋-骨盆'
    : summary.spinal_flexion_mobility_issue
      ? '脊柱'
      : summary.posterior_chain_ted_issue
        ? '后链'
        : summary.loaded_flexion_smcd_issue
          ? '负重屈曲控制'
          : summary.base_flexion_pattern_smcd_issue
            ? '基础屈曲控制'
            : ''
  summary.summary_text = buildSummaryText(summary, findingTexts)

  const nextStep = (() => {
    if (payload.breakout_status === 'skipped') return '' as SfmaMsfBreakout['flow_next_step']
    if (stopAndTreatPain) return '停止并优先处理疼痛' as const
    const pending = MSF_FLOW_NODE_ORDER.find((item) => enabledNodes.has(item.code) && !isNodeCompleted(nodes, item.code))
    if (!pending) {
      if (summary.rotation_flow_needed) {
        return '继续进入旋转动作解析' as SfmaMsfBreakout['flow_next_step']
      }
      const hasAny = MSF_FLOW_NODE_ORDER.some((item) => hasMsfNodeValue(payload, item.code))
      if (!hasAny && payload.breakout_status === 'in_progress') {
        return '继续单腿站立体前屈' as const
      }
      return hasAny ? ('流程已完成' as const) : ('' as const)
    }
    if (pending.code === 'single_leg_stance_forward_bend') return '继续单腿站立体前屈' as const
    if (pending.code === 'long_sit_toe_touch') return '继续长坐位触摸足趾' as const
    if (pending.code === 'active_straight_leg_raise') return '继续主动直腿抬高' as const
    if (pending.code === 'passive_straight_leg_raise') return '继续被动直腿抬高' as const
    if (pending.code === 'prone_backward_rocking') return '继续俯卧位向后摆动' as const
    if (pending.code === 'supine_double_knees_to_chest') return '继续仰卧位双膝触胸' as const
    if (pending.code === 'rolling_analysis_result') return '继续滚动解析测试' as const
    return '' as const
  })()

  analysis.flexion_flow = cloneFlowNodes(nodes)
  return {
    analysis,
    enabledNodes,
    nextStep,
    stopAndTreatPain
  }
}
