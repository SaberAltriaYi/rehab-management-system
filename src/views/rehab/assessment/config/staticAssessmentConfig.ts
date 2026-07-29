export interface StaticAssessmentOption {
  label: string
  value: string
}

export interface StaticAssessmentFieldConfig {
  key: string
  label: string
  options: StaticAssessmentOption[]
  normalValues?: string[]
  redundantGroupKey?: string
  description?: string
}

export interface StaticAssessmentBasicInfo {
  name: string
  height_cm: number | null
  weight_kg: number | null
  assessment_date: string
  age: number | null
  gender: string
  assessor: string
}

export interface StaticAssessmentViewBlock {
  [key: string]: string | null
}

export interface StaticAssessmentFormData {
  basic_info: StaticAssessmentBasicInfo
  posterior_view: {
    left: StaticAssessmentViewBlock
    right: StaticAssessmentViewBlock
    midline: StaticAssessmentViewBlock
  }
  lateral_view: {
    left: StaticAssessmentViewBlock
    right: StaticAssessmentViewBlock
  }
  anterior_view: {
    left: StaticAssessmentViewBlock
    right: StaticAssessmentViewBlock
    midline: StaticAssessmentViewBlock
  }
  notes: {
    general_note: string
    spine_alignment_note: string
  }
  static_summary?: Record<string, any>
}

const OPTIONS_NORMAL = (first: string, second: string): StaticAssessmentOption[] => [
  { label: first, value: first },
  { label: second, value: second },
  { label: '正常', value: '正常' }
]

const OPTIONS_HIGH_NORMAL_LOW: StaticAssessmentOption[] = [
  { label: '高', value: '高' },
  { label: '正常', value: '正常' },
  { label: '低', value: '低' }
]

const OPTIONS_SLIGHT_HIGH_NORMAL: StaticAssessmentOption[] = [
  { label: '较高', value: '较高' },
  { label: '正常', value: '正常' }
]

const OPTIONS_INNER_OUTER_NORMAL: StaticAssessmentOption[] = [
  { label: '内旋', value: '内旋' },
  { label: '外旋', value: '外旋' },
  { label: '正常', value: '正常' }
]

export const BASIC_GENDER_OPTIONS: StaticAssessmentOption[] = [
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]

export const POSTERIOR_BILATERAL_FIELDS: StaticAssessmentFieldConfig[] = [
  {
    key: 'ear_height',
    label: '耳朵高度',
    options: OPTIONS_NORMAL('偏上', '偏下'),
    normalValues: ['正常']
  },
  {
    key: 'shoulder_height',
    label: '肩膀高度',
    options: OPTIONS_HIGH_NORMAL_LOW,
    normalValues: ['正常']
  },
  {
    key: 'scapula_adduction_abduction',
    label: '肩胛骨内收/外展',
    options: OPTIONS_NORMAL('内收', '外展'),
    normalValues: ['正常']
  },
  {
    key: 'scapula_inferior_angle',
    label: '肩胛下角',
    options: OPTIONS_HIGH_NORMAL_LOW,
    normalValues: ['正常']
  },
  {
    key: 'scapula_rotation',
    label: '肩胛骨旋转',
    options: [
      { label: '外旋', value: '外旋' },
      { label: '正常', value: '正常' },
      { label: '内收', value: '内收' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'winged_scapula',
    label: '翼状肩胛骨',
    options: [
      { label: '外翻', value: '外翻' },
      { label: '正常', value: '正常' },
      { label: '内翻', value: '内翻' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'upper_limb_posture',
    label: '上肢姿势',
    options: [
      { label: '空隙大', value: '空隙大' },
      { label: '空隙小', value: '空隙小' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'elbow_posture',
    label: '手肘姿势',
    options: OPTIONS_INNER_OUTER_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'hand_posture',
    label: '手部姿势',
    options: [
      { label: '旋前', value: '旋前' },
      { label: '正常', value: '正常' },
      { label: '旋后', value: '旋后' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'psis_height',
    label: '髂后上棘',
    options: OPTIONS_SLIGHT_HIGH_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'gluteal_line_height',
    label: '臀线',
    options: OPTIONS_SLIGHT_HIGH_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'knee_varus_valgus',
    label: '膝内翻/外翻',
    options: [
      { label: '内翻', value: '内翻' },
      { label: '外翻', value: '外翻' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'lower_leg_midline',
    label: '小腿中线',
    options: [
      { label: '偏外', value: '偏外' },
      { label: '正常', value: '正常' },
      { label: '偏内', value: '偏内' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'achilles_tendon',
    label: '跟腱',
    options: [
      { label: '外凹', value: '外凹' },
      { label: '正常', value: '正常' },
      { label: '内凹', value: '内凹' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'ankle_height',
    label: '踝关节',
    options: OPTIONS_SLIGHT_HIGH_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'foot_posture',
    label: '足部姿势',
    options: [
      { label: '偏外', value: '偏外' },
      { label: '正常', value: '正常' },
      { label: '偏内', value: '偏内' }
    ],
    normalValues: ['正常']
  }
]

export const POSTERIOR_MIDLINE_FIELDS: StaticAssessmentFieldConfig[] = [
  {
    key: 'head_neck_tilt',
    label: '头颈部倾斜',
    options: [
      { label: '左曲', value: '左曲' },
      { label: '右曲', value: '右曲' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_head_neck_tilt'
  },
  {
    key: 'neck_rotation',
    label: '颈部旋转',
    options: OPTIONS_NORMAL('左旋', '右旋'),
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_neck_rotation'
  },
  {
    key: 'thoracic_spine_shift',
    label: '胸椎',
    options: OPTIONS_NORMAL('偏左', '偏右'),
    normalValues: ['正常']
  },
  {
    key: 'thorax_tilt',
    label: '胸廓倾斜',
    options: [
      { label: '左倾', value: '左倾' },
      { label: '右倾', value: '右倾' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_thorax_tilt'
  },
  {
    key: 'thorax_rotation',
    label: '胸廓旋转',
    options: OPTIONS_NORMAL('左旋', '右旋'),
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_thorax_rotation'
  },
  {
    key: 'pelvic_tilt',
    label: '骨盆区域',
    options: [
      { label: '左倾', value: '左倾' },
      { label: '右倾', value: '右倾' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_pelvic_tilt'
  },
  {
    key: 'pelvic_rotation',
    label: '骨盆旋转',
    options: OPTIONS_NORMAL('左旋', '右旋'),
    normalValues: ['正常'],
    redundantGroupKey: 'posterior_pelvic_rotation'
  }
]

export const LATERAL_BILATERAL_FIELDS: StaticAssessmentFieldConfig[] = [
  {
    key: 'head_position',
    label: '头部姿势',
    options: [
      { label: '前倾', value: '前倾' },
      { label: '后仰', value: '后仰' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'cervical_curve',
    label: '颈椎',
    options: [
      { label: '过曲', value: '过曲' },
      { label: '反弓', value: '反弓' },
      { label: '曲度变直', value: '曲度变直' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'cervicothoracic_junction',
    label: '颈胸椎连接',
    options: [
      { label: '正常', value: '正常' },
      { label: '隆起', value: '隆起' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'shoulder_position',
    label: '肩膀姿势',
    options: OPTIONS_INNER_OUTER_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'thoracic_curve',
    label: '胸部',
    options: [
      { label: '过曲', value: '过曲' },
      { label: '曲度变直', value: '曲度变直' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'abdomen',
    label: '腹部',
    options: [
      { label: '突出', value: '突出' },
      { label: '平坦', value: '平坦' }
    ],
    normalValues: ['平坦']
  },
  {
    key: 'lumbar_curve',
    label: '腰椎',
    options: [
      { label: '过曲', value: '过曲' },
      { label: '曲度变直', value: '曲度变直' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'pelvis_tilt',
    label: '骨盆',
    options: [
      { label: '前倾', value: '前倾' },
      { label: '正常', value: '正常' },
      { label: '后倾', value: '后倾' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'knee_position',
    label: '膝盖',
    options: [
      { label: '过伸', value: '过伸' },
      { label: '正常', value: '正常' },
      { label: '屈曲', value: '屈曲' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'ankle_foot_position',
    label: '脚踝/足部',
    options: [
      { label: '趾屈', value: '趾屈' },
      { label: '背伸', value: '背伸' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  }
]

export const ANTERIOR_MIDLINE_FIELDS: StaticAssessmentFieldConfig[] = [
  {
    key: 'chest_shift',
    label: '胸部',
    options: OPTIONS_NORMAL('偏右', '偏左'),
    normalValues: ['正常']
  },
  {
    key: 'arm_symmetry',
    label: '手臂',
    options: [
      { label: '维度相比较对侧较大', value: '维度相比较对侧较大' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'wrist_hand_position',
    label: '手部及手腕',
    options: [
      { label: '尺侧', value: '尺侧' },
      { label: '正常', value: '正常' },
      { label: '桡侧', value: '桡侧' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'abdomen_alignment',
    label: '腹部（胸骨/耻骨联合/肚脐）',
    options: OPTIONS_NORMAL('偏右', '偏左'),
    normalValues: ['正常']
  },
  {
    key: 'pelvic_lateral_shift',
    label: '骨盆侧向位移',
    options: [
      { label: '右移', value: '右移' },
      { label: '左移', value: '左移' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'pelvic_rotation',
    label: '骨盆旋转',
    options: OPTIONS_NORMAL('右旋', '左旋'),
    normalValues: ['正常'],
    redundantGroupKey: 'anterior_pelvic_rotation'
  },
  {
    key: 'standing_pressure',
    label: '站立',
    options: [
      { label: '足底压力偏右', value: '足底压力偏右' },
      { label: '足底压力偏左', value: '足底压力偏左' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  }
]

export const ANTERIOR_BILATERAL_FIELDS: StaticAssessmentFieldConfig[] = [
  {
    key: 'knee_varus_valgus',
    label: '膝内翻/膝外翻',
    options: [
      { label: '内翻', value: '内翻' },
      { label: '外翻', value: '外翻' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'knee_rotation',
    label: '膝盖旋转',
    options: OPTIONS_INNER_OUTER_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'patella_position',
    label: '髌骨位置',
    options: [
      { label: '正常', value: '正常' },
      { label: '偏内', value: '偏内' },
      { label: '偏外', value: '偏外' }
    ],
    normalValues: ['正常'],
    description: 'TODO: 待确认髌骨位置选项是否需要更细分。'
  },
  {
    key: 'tibial_rotation',
    label: '胫骨',
    options: OPTIONS_INNER_OUTER_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'ankle_rotation',
    label: '脚踝',
    options: OPTIONS_INNER_OUTER_NORMAL,
    normalValues: ['正常']
  },
  {
    key: 'foot_posture',
    label: '足部姿势',
    options: [
      { label: '偏外', value: '偏外' },
      { label: '偏内', value: '偏内' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  },
  {
    key: 'arch_type',
    label: '足弓',
    options: [
      { label: '扁平足', value: '扁平足' },
      { label: '高足弓', value: '高足弓' },
      { label: '正常', value: '正常' }
    ],
    normalValues: ['正常']
  }
]

const buildViewDefaults = (fields: StaticAssessmentFieldConfig[]): StaticAssessmentViewBlock => {
  return fields.reduce<StaticAssessmentViewBlock>((acc, item) => {
    acc[item.key] = null
    return acc
  }, {})
}

const buildPosteriorSideDefaults = (): StaticAssessmentViewBlock => {
  const base = buildViewDefaults(POSTERIOR_BILATERAL_FIELDS)
  // 冗余字段预留：用于后续“中轴 + 左右冗余”冲突检测（当前界面不主动展示）
  base.head_neck_tilt = null
  base.neck_rotation = null
  base.thorax_tilt = null
  base.thorax_rotation = null
  base.pelvic_tilt = null
  base.pelvic_rotation = null
  return base
}

const buildAnteriorSideDefaults = (): StaticAssessmentViewBlock => {
  const base = buildViewDefaults(ANTERIOR_BILATERAL_FIELDS)
  // 冗余字段预留：用于后续“中轴 + 左右冗余”冲突检测（当前界面不主动展示）
  base.pelvic_rotation = null
  return base
}

const deepClone = <T>(value: T): T => JSON.parse(JSON.stringify(value))

const fillMissingByTemplate = (target: Record<string, any>, template: Record<string, any>) => {
  Object.keys(template).forEach((key) => {
    const templateValue = template[key]
    const targetValue = target[key]
    if (targetValue === undefined) {
      target[key] = deepClone(templateValue)
      return
    }
    if (
      templateValue &&
      typeof templateValue === 'object' &&
      !Array.isArray(templateValue) &&
      targetValue &&
      typeof targetValue === 'object' &&
      !Array.isArray(targetValue)
    ) {
      fillMissingByTemplate(targetValue, templateValue)
    }
  })
}

export const buildDefaultStaticAssessmentFormData = (): StaticAssessmentFormData => {
  return {
    basic_info: {
      name: '',
      height_cm: null,
      weight_kg: null,
      assessment_date: '',
      age: null,
      gender: '',
      assessor: ''
    },
    posterior_view: {
      left: buildPosteriorSideDefaults(),
      right: buildPosteriorSideDefaults(),
      midline: buildViewDefaults(POSTERIOR_MIDLINE_FIELDS)
    },
    lateral_view: {
      left: buildViewDefaults(LATERAL_BILATERAL_FIELDS),
      right: buildViewDefaults(LATERAL_BILATERAL_FIELDS)
    },
    anterior_view: {
      left: buildAnteriorSideDefaults(),
      right: buildAnteriorSideDefaults(),
      midline: buildViewDefaults(ANTERIOR_MIDLINE_FIELDS)
    },
    notes: {
      general_note: '',
      spine_alignment_note: ''
    }
  }
}

export const mergeStaticAssessmentFormData = (rawValue?: Record<string, any>): StaticAssessmentFormData => {
  const defaultValue = buildDefaultStaticAssessmentFormData()
  if (!rawValue || typeof rawValue !== 'object') {
    return defaultValue
  }
  const merged = deepClone(rawValue)
  fillMissingByTemplate(merged, defaultValue as unknown as Record<string, any>)
  return merged as StaticAssessmentFormData
}
