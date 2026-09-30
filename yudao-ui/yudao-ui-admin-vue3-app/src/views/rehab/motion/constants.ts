/** 动作评估界面常量（与后端 MotionLabels / RehabMotionConstants 对齐） */

export const TEST_OPTIONS: Array<{ value: string; label: string; family: string }> = [
  { value: 'FMS_DEEP_SQUAT', label: 'FMS 深蹲', family: 'FMS' },
  { value: 'FMS_HURDLE_STEP', label: 'FMS 跨栏步', family: 'FMS' },
  { value: 'FMS_INLINE_LUNGE', label: 'FMS 直线弓步', family: 'FMS' },
  { value: 'FMS_SHOULDER_MOBILITY', label: 'FMS 肩部灵活性', family: 'FMS' },
  { value: 'FMS_ASLR', label: 'FMS 主动直腿抬高', family: 'FMS' },
  { value: 'FMS_TSPU', label: 'FMS 躯干稳定俯卧撑', family: 'FMS' },
  { value: 'FMS_ROTARY_STABILITY', label: 'FMS 旋转稳定性', family: 'FMS' },
  { value: 'NASM_OHS', label: 'NASM 过顶深蹲', family: 'NASM' },
  { value: 'NASM_SLS', label: 'NASM 单腿蹲', family: 'NASM' },
  { value: 'NASM_PUSHUP', label: 'NASM 俯卧撑', family: 'NASM' },
  { value: 'NASM_ROW', label: 'NASM 站姿划船', family: 'NASM' },
  { value: 'NASM_DB_PRESS', label: 'NASM 站姿哑铃推举', family: 'NASM' },
  { value: 'NASM_SHOULDER_HABD', label: 'NASM 肩水平外展', family: 'NASM' },
  { value: 'NASM_SHOULDER_ROT', label: 'NASM 肩旋转', family: 'NASM' },
  { value: 'NASM_SHOULDER_FLEX', label: 'NASM 肩前屈', family: 'NASM' },
  { value: 'NASM_GAIT', label: 'NASM 步态', family: 'NASM' },
  { value: 'YBT_LQ', label: 'Y-Balance 下肢', family: 'YBT' },
  { value: 'TUCK_JUMP', label: '10 秒团身跳', family: 'TJA' },
  { value: 'LESS', label: 'LESS 落地误差评分', family: 'LESS' }
]

export const testLabel = (code?: string) => TEST_OPTIONS.find((t) => t.value === code)?.label || code || '-'

export const SIDE_OPTIONS = [
  { value: 'bilateral', label: '双侧' },
  { value: 'left', label: '左' },
  { value: 'right', label: '右' }
]
/** 以整体（双侧）记录的测试；其余测试按左/右记录（与引擎 tests_registry.sides、后端 BILATERAL_TESTS 一致；YBT 侧别 = 支撑腿） */
export const BILATERAL_TESTS = new Set([
  'FMS_DEEP_SQUAT',
  'FMS_TSPU',
  'NASM_OHS',
  'NASM_PUSHUP',
  'NASM_ROW',
  'NASM_DB_PRESS',
  'NASM_GAIT',
  'TUCK_JUMP',
  'LESS'
])
export const sideOptionsFor = (testCode?: string) =>
  !testCode ? SIDE_OPTIONS : SIDE_OPTIONS.filter((o) => (BILATERAL_TESTS.has(testCode) ? o.value === 'bilateral' : o.value !== 'bilateral'))
/** 侧别归一：双侧测试固定 bilateral；左右测试保留 left/right，否则置空要求治疗师选择（不猜测侧别） */
export const normalizeSide = (testCode: string | undefined, side?: string) => {
  const opts = sideOptionsFor(testCode)
  if (opts.length === 1) return opts[0].value
  return opts.some((o) => o.value === side) ? side! : ''
}
export const sideLabel = (s?: string) =>
  s === 'overall' ? '' : SIDE_OPTIONS.find((x) => x.value === s)?.label || s || ''

/**
 * FMS 官方分级条件（与引擎 tiers、后端 RehabMotionConstants.FMS_CONDITIONS 一致）：
 * 深蹲 标准→足跟垫高；躯干稳定俯卧撑 高位→低位手位；旋转稳定 同侧→对角。其他测试无条件。
 */
export const FMS_CONDITIONS: Record<string, { value: string; label: string }[]> = {
  FMS_DEEP_SQUAT: [
    { value: 'standard', label: '标准' },
    { value: 'heels_elevated', label: '足跟垫高' }
  ],
  FMS_TSPU: [
    { value: 'high', label: '高位手位（3分）' },
    { value: 'low', label: '低位手位（2分）' }
  ],
  FMS_ROTARY_STABILITY: [
    { value: 'unilateral', label: '同侧（3分）' },
    { value: 'diagonal', label: '对角（2分）' }
  ]
}
export const conditionOptions = (testCode?: string) => (testCode && FMS_CONDITIONS[testCode]) || []
export const conditionLabel = (testCode?: string, value?: string) =>
  conditionOptions(testCode).find((c) => c.value === value)?.label || value || ''

export const CLEARING_TESTS = [
  { code: 'CT-SM-L', label: '肩部清除测试（左）→ 肩部灵活性' },
  { code: 'CT-SM-R', label: '肩部清除测试（右）→ 肩部灵活性' },
  { code: 'CT-EXT', label: '伸展清除测试 → 躯干稳定俯卧撑' },
  { code: 'CT-FLX', label: '屈曲清除测试 → 旋转稳定性' }
]

/** LESS 17 项（Padua 2009）；16、17 为 0/1/2，其余 0/1 */
export const LESS_ITEMS = [
  { code: 'LESS-01', label: '初始触地膝屈曲 < 30°', max: 1 },
  { code: 'LESS-02', label: '初始触地髋未屈曲', max: 1 },
  { code: 'LESS-03', label: '初始触地躯干未前屈', max: 1 },
  { code: 'LESS-04', label: '踝：足跟先着地或全足着地', max: 1 },
  { code: 'LESS-05', label: '初始触地膝外翻', max: 1 },
  { code: 'LESS-06', label: '初始触地躯干侧屈', max: 1 },
  { code: 'LESS-07', label: '站距过宽', max: 1 },
  { code: 'LESS-08', label: '站距过窄', max: 1 },
  { code: 'LESS-09', label: '足内旋 > 30°', max: 1 },
  { code: 'LESS-10', label: '足外旋 > 30°', max: 1 },
  { code: 'LESS-11', label: '双足触地不对称', max: 1 },
  { code: 'LESS-12', label: '膝屈曲位移 < 45°', max: 1 },
  { code: 'LESS-13', label: '最大膝屈曲时髋屈曲未增加', max: 1 },
  { code: 'LESS-14', label: '最大膝屈曲时躯干前屈未增加', max: 1 },
  { code: 'LESS-15', label: '膝外翻位移', max: 1 },
  { code: 'LESS-16', label: '关节位移（软 0 / 中 1 / 僵硬 2）', max: 2 },
  { code: 'LESS-17', label: '整体印象（优 0 / 中 1 / 差 2）', max: 2 }
]

export const TJA_ITEMS = [
  { code: 'TJ-01', label: '下肢外翻' },
  { code: 'TJ-02', label: '大腿未达到平行' },
  { code: 'TJ-03', label: '左右大腿不等高' },
  { code: 'TJ-04', label: '落地站距非肩宽' },
  { code: 'TJ-05', label: '双足前后不平行' },
  { code: 'TJ-06', label: '双足接触时机不一致' },
  { code: 'TJ-07', label: '落地声音过大（仅人工）' },
  { code: 'TJ-08', label: '跳跃之间停顿' },
  { code: 'TJ-09', label: '10 秒结束前技术下降' },
  { code: 'TJ-10', label: '未落回同一足印' }
]

export const TJA_VARIANTS = [
  { value: 'TJA_MODIFIED_0_2', label: '改良版 0/1/2（总分 0-20，表格默认）', max: 2 },
  { value: 'TJA_MYER_2008_DICHOTOMOUS', label: 'Myer 2008 原版 0/1（总分 0-10）', max: 1 }
]

export const FAMILY_OPTIONS = [
  { value: 'FMS', label: 'FMS 功能性动作筛查' },
  { value: 'NASM_CES', label: 'NASM-CES 动作评估' },
  { value: 'YBT_LQ', label: 'Y-Balance（扩展）' },
  { value: 'TUCK_JUMP', label: '团身跳 TJA（扩展）' },
  { value: 'LESS', label: 'LESS（扩展）' }
]

export const VISIT_OPTIONS = [
  { value: 'initial', label: '初评' },
  { value: 'follow_up', label: '复评' },
  { value: 'discharge', label: '结案评估' }
]
export const visitLabel = (v?: string) => VISIT_OPTIONS.find((x) => x.value === v)?.label || v || '-'

export const STATE_TAG: Record<string, string> = {
  WAITING_UPLOAD: 'info',
  UPLOADING: 'info',
  OPENCAP_PROCESSING: 'warning',
  DOWNLOADING: 'warning',
  PARSING: 'warning',
  RULES: 'warning',
  AI_GENERATING: 'warning',
  PDF_RENDERING: 'warning',
  PENDING_REVIEW: 'primary',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'info'
}

export const FINAL_STATUS_OPTIONS = [
  { value: 'confirmed', label: '确认系统分' },
  { value: 'modified', label: '修改' },
  { value: 'not_applicable', label: '不适用' }
]
export const FINAL_STATUS_LABEL: Record<string, string> = {
  pending: '待审核',
  confirmed: '已确认',
  modified: '已修改',
  not_applicable: '不适用',
  needs_recheck: '需复核（系统分已变化）'
}

export const SYSTEM_STATUS_LABEL: Record<string, string> = {
  suggested: '系统建议',
  pending_manual: '需人工判定',
  pain_zero: '疼痛 → 0 分',
  pending_pain: '疼痛信息缺失',
  no_valid_trial: '无有效试次',
  pending_trial: '缺少试次',
  evidence_only: '仅证据（NASM 不评分）',
  computed: '已计算',
  LIMB_LENGTH_MISSING: '缺腿长',
  pending_data: '数据不足',
  pending_rating: '待人工评定'
}

export const CLASSIFICATION_LABEL: Record<string, string> = {
  standard_formula: 'A 标准公式',
  mot_direct: 'B .mot 直接',
  derived: 'C 推导（待验证）',
  proxy: 'D 代理指标',
  manual: 'M 人工'
}

export const QC_LABEL: Record<string, string> = {
  pass: '通过',
  warn: '警告',
  fail: '未通过',
  not_analyzed: '未分析'
}

/** 前端展示用的分值范围（最终校验在服务端） */
export const scoreRange = (scheme?: string): { min: number; max: number; step: number } | null => {
  switch (scheme) {
    case 'FMS_0_3':
      return { min: 0, max: 3, step: 1 }
    case 'LESS_MEAN_OF_3':
      return { min: 0, max: 19, step: 0.01 }
    case 'TJA_MODIFIED_0_2':
      return { min: 0, max: 20, step: 1 }
    case 'TJA_MYER_2008_DICHOTOMOUS':
      return { min: 0, max: 10, step: 1 }
    case 'YBT_COMPOSITE_PERCENT':
      return { min: 0, max: 200, step: 0.1 }
    default:
      return null
  }
}
