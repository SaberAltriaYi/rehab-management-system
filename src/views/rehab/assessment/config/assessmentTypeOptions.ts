export type AssessmentTypeCode =
  | 'static_assessment'
  | 'body_composition'
  | 'nasm_ces'
  | 'sfma'
  | 'fms'
  | 'ybt'
  | 'opencap'
  | 'observation_only'
  | 'comprehensive_assessment'

export interface AssessmentTypeOption {
  value: AssessmentTypeCode
  label: string
  description?: string
  enabled?: boolean
}

export const ASSESSMENT_TYPE_OPTIONS: AssessmentTypeOption[] = [
  {
    value: 'static_assessment',
    label: '静态评估',
    description: '体态静态排列与基础量化指标',
    enabled: true
  },
  {
    value: 'body_composition',
    label: '身体成分',
    description: '体重、BMI、体脂、肌肉量等指标',
    enabled: true
  },
  {
    value: 'nasm_ces',
    label: 'NASM-CES',
    description: '动作偏差识别与纠正训练取向',
    enabled: true
  },
  {
    value: 'sfma',
    label: 'SFMA',
    description: '功能动作筛查与模式分类',
    enabled: true
  },
  {
    value: 'fms',
    label: 'FMS',
    description: '基础动作功能评分',
    enabled: true
  },
  {
    value: 'ybt',
    label: 'YBT',
    description: '平衡与左右差评估',
    enabled: true
  },
  {
    value: 'opencap',
    label: 'OpenCap / OpenSim',
    description: '运动学专项与左右对比',
    enabled: true
  },
  {
    value: 'observation_only',
    label: '人工观察记录',
    description: '仅记录临床观察与主观反馈',
    enabled: true
  },
  {
    value: 'comprehensive_assessment',
    label: '综合评估',
    description: '组合多种评估模块并形成综合结论',
    enabled: true
  }
]

export const ASSESSMENT_TYPE_LABEL_MAP = ASSESSMENT_TYPE_OPTIONS.reduce<Record<string, string>>(
  (acc, item) => {
    acc[item.value] = item.label
    return acc
  },
  {}
)

export const getAssessmentTypeLabel = (typeCode?: string): string => {
  if (!typeCode) {
    return '-'
  }
  return ASSESSMENT_TYPE_LABEL_MAP[typeCode] || typeCode
}

export const getAssessmentTypeOption = (typeCode?: string) => {
  return ASSESSMENT_TYPE_OPTIONS.find((item) => item.value === typeCode)
}
