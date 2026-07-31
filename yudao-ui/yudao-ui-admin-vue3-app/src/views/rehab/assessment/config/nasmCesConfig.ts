export interface NasmOption {
  label: string
  value: string
}

export interface NasmBilateralSelectConfig {
  key: string
  label: string
  options: NasmOption[]
}

export interface NasmBinaryConfig {
  key: string
  label: string
  hasSide?: boolean
}

export interface NasmControlSelectConfig {
  key: string
  label: string
  options: NasmOption[]
}

export interface NasmLessItemConfig {
  key: string
  label: string
  options: NasmOption[]
  riskValues: string[]
}

export interface NasmCesBasicInfo {
  name: string
  age: number | null
  assessment_date: string
  assessor: string
  focus: string
  summary_note: string
}

export interface NasmBinaryObservation {
  present: boolean | null
  left?: boolean | null
  right?: boolean | null
  note: string
}

export interface NasmBilateralSelectObservation {
  left: string
  right: string
  overall: boolean | null
  note: string
}

export interface NasmSelectWithNoteObservation {
  value: string
  note: string
}

export interface NasmDaviesTrial {
  trial_no: number
  point_distance_inch: number | null
  point_distance_cm: number | null
  duration_sec: number | null
  repetition_count: number | null
  repetition_quality_note: string
}

export interface NasmCesFormData {
  basic_info: NasmCesBasicInfo
  transition_assessments: Record<string, any>
  dynamic_assessments: Record<string, any>
  upper_extremity_davies_test: {
    trials: NasmDaviesTrial[]
    total_repetition_count: number | null
  }
  less_test: {
    items: Record<string, NasmSelectWithNoteObservation>
    less_total_score: number | null
  }
  summary: {
    transition_summary: {
      head_neck: string
      shoulder: string
      elbow: string
      lphc: string
      knee: string
      ankle_foot: string
    }
    dynamic_summary_note: string
    overall_ces_summary: string
  }
  notes: {
    general_note: string
  }
  ces_summary?: Record<string, any>
  action_summaries?: Array<Record<string, any>>
  risk_precheck?: Record<string, any>
  report_mapping?: Record<string, any>
}

export const YES_NO_OPTIONS: NasmOption[] = [
  { label: '是', value: '是' },
  { label: '否', value: '否' }
]

export const NORMAL_CONTROL_OPTIONS: NasmOption[] = [
  { label: '正常', value: '正常' },
  { label: '控制不足', value: '控制不足' },
  { label: '明显偏移', value: '明显偏移' },
  { label: '未评估', value: '未评估' }
]

const OPT_ABNORMAL_NORMAL = (abnormal: string): NasmOption[] => [
  { label: abnormal, value: abnormal },
  { label: '正常', value: '正常' }
]

const OPT_BOOLEAN_STYLE = (good: string, bad: string): NasmOption[] => [
  { label: good, value: good },
  { label: bad, value: bad }
]

export const OVERHEAD_SQUAT_FRONT_FEET: NasmBilateralSelectConfig[] = [
  { key: 'out_toe', label: '外八字', options: OPT_ABNORMAL_NORMAL('外八字') },
  { key: 'flat_foot', label: '扁平足', options: OPT_ABNORMAL_NORMAL('扁平足') }
]

export const OVERHEAD_SQUAT_FRONT_KNEE: NasmBilateralSelectConfig[] = [
  { key: 'knee_valgus', label: '膝内扣', options: OPT_ABNORMAL_NORMAL('膝内扣') },
  { key: 'knee_lateral_shift', label: '膝外移', options: OPT_ABNORMAL_NORMAL('膝外移') }
]

export const OVERHEAD_SQUAT_LATERAL_LPHC: NasmBilateralSelectConfig[] = [
  { key: 'excessive_forward_lean', label: '过度前倾', options: OPT_ABNORMAL_NORMAL('过度前倾') },
  { key: 'low_back_arch', label: '塌腰', options: OPT_ABNORMAL_NORMAL('塌腰') },
  { key: 'low_back_round', label: '弓腰', options: OPT_ABNORMAL_NORMAL('弓腰') }
]

export const OVERHEAD_SQUAT_LATERAL_SHOULDER: NasmBilateralSelectConfig[] = [
  { key: 'arms_fall_forward', label: '双臂向前落', options: OPT_ABNORMAL_NORMAL('双臂向前落') }
]

export const OVERHEAD_SQUAT_POSTERIOR_FEET: NasmBilateralSelectConfig[] = [
  { key: 'flat_foot', label: '扁平足', options: OPT_ABNORMAL_NORMAL('扁平足') },
  { key: 'heel_rise', label: '足跟抬起、离地', options: OPT_ABNORMAL_NORMAL('足跟抬起、离地') }
]

export const OVERHEAD_SQUAT_POSTERIOR_LPHC: NasmBilateralSelectConfig[] = [
  { key: 'asymmetric_shift', label: '非对称性偏移', options: OPT_ABNORMAL_NORMAL('非对称性偏移') }
]

export const SINGLE_LEG_SQUAT_ITEMS: NasmBinaryConfig[] = [
  { key: 'knee_valgus', label: '膝内扣' },
  { key: 'hip_hike', label: '髋上提' },
  { key: 'hip_drop', label: '髋下降' },
  { key: 'trunk_rotation_toward_support', label: '躯干旋转（支撑侧）' },
  { key: 'trunk_rotation_away_support', label: '躯干旋转（支撑侧对侧）' }
]

export const PUSH_UP_LPHC_ITEMS: NasmBinaryConfig[] = [
  { key: 'lumbar_sag', label: '腰下沉' },
  { key: 'lumbar_round', label: '腰拱起' }
]

export const PUSH_UP_SHOULDER_ITEMS: NasmBinaryConfig[] = [
  { key: 'shrug', label: '耸肩' },
  { key: 'winging', label: '翼状肩', hasSide: true }
]

export const PUSH_UP_HEAD_ITEMS: NasmBinaryConfig[] = [{ key: 'cervical_extension', label: '颈椎过伸' }]

export const STANDING_ROW_ITEMS: NasmBinaryConfig[] = [
  { key: 'lphc_sag', label: 'LPHC 塌腰' },
  { key: 'shoulder_shrug', label: '耸肩' },
  { key: 'forward_head', label: '头部前伸' }
]

export const STANDING_PRESS_ITEMS: NasmBinaryConfig[] = [
  { key: 'lphc_sag', label: 'LPHC 塌腰' },
  { key: 'shoulder_shrug', label: '耸肩', hasSide: true },
  { key: 'arm_forward_shift', label: '手臂前移', hasSide: true },
  { key: 'elbow_flexion', label: '屈肘', hasSide: true },
  { key: 'forward_head', label: '头部前伸' }
]

export const UE_HORIZONTAL_ABDUCTION_ITEMS: NasmBinaryConfig[] = [
  { key: 'shoulder_shrug', label: '耸肩', hasSide: true },
  { key: 'shoulder_protraction', label: '肩前伸', hasSide: true },
  { key: 'elbow_flexion', label: '屈肘', hasSide: true }
]

export const UE_ROTATION_SHOULDER_ITEMS: NasmBinaryConfig[] = [
  { key: 'shoulder_shrug', label: '耸肩', hasSide: true },
  { key: 'shoulder_protraction', label: '肩前伸', hasSide: true }
]

export const UE_ROTATION_HUMERUS_ITEMS: NasmBinaryConfig[] = [
  { key: 'internal_rotation_away_wall', label: '手远离墙壁，肱骨内旋', hasSide: true },
  { key: 'external_rotation_away_wall', label: '手远离墙壁，肱骨外旋', hasSide: true }
]

export const UE_FLEXION_ITEMS: NasmBinaryConfig[] = [
  { key: 'shoulder_shrug', label: '耸肩', hasSide: true },
  { key: 'lumbar_sag', label: '塌腰' },
  { key: 'elbow_flexion', label: '屈肘', hasSide: true }
]

export const STAR_EXCURSION_CONTROLS: NasmControlSelectConfig[] = [
  { key: 'sagittal_plane_control', label: '矢状面控制', options: NORMAL_CONTROL_OPTIONS },
  { key: 'frontal_plane_control', label: '额状面控制', options: NORMAL_CONTROL_OPTIONS },
  { key: 'transverse_plane_control', label: '水平面控制', options: NORMAL_CONTROL_OPTIONS }
]

export const GAIT_ANALYSIS_ITEMS: NasmBinaryConfig[] = [
  { key: 'flat_foot', label: '足：扁平足', hasSide: true },
  { key: 'out_toe', label: '足：外八字', hasSide: true },
  { key: 'knee_valgus', label: '膝：内扣', hasSide: true },
  { key: 'lphc_sag', label: 'LPHC：塌腰' },
  { key: 'lphc_excessive_rotation', label: 'LPHC：过度旋转' },
  { key: 'lphc_hip_hike', label: 'LPHC：髋上提', hasSide: true },
  { key: 'rounded_shoulder', label: '肩：圆肩', hasSide: true },
  { key: 'forward_head', label: '头：前伸' }
]

export const TUCK_JUMP_KNEE_THIGH_ITEMS: NasmBinaryConfig[] = [
  { key: 'lower_limb_valgus_on_landing', label: '落地时下肢外翻' },
  { key: 'thigh_not_parallel_at_apex', label: '大腿未与地面平行（跳到顶点时）' },
  { key: 'thigh_height_asymmetry_at_apex', label: '两条大腿高度不对称（腾空时）' }
]

export const TUCK_JUMP_FOOT_LANDING_ITEMS: NasmBinaryConfig[] = [
  { key: 'stance_width_not_shoulder_width', label: '双脚距离与肩宽不一致' },
  { key: 'feet_not_parallel', label: '双脚位置不平行（前后向）' },
  { key: 'uneven_foot_contact_time', label: '足触地时间不同' },
  { key: 'loud_landing_sound', label: '触地声过大' }
]

export const TUCK_JUMP_PLYO_TECHNIQUE_ITEMS: NasmBinaryConfig[] = [
  { key: 'jump_pause', label: '跳跃时间停顿' },
  { key: 'technique_worsens_within_10s', label: '前10秒内技术动作变差' },
  { key: 'landing_position_inconsistent', label: '落地不在同一位置（腾空时有过多动作）' }
]

export const LESS_ITEMS_CONFIG: NasmLessItemConfig[] = [
  { key: 'knee_flexion_gt_30_at_initial_contact', label: '首次触地时膝关节屈曲角度大于30度', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'knee_valgus_over_mid_foot_at_initial_contact', label: '首次触地时膝外翻，膝超过足中段', options: YES_NO_OPTIONS, riskValues: ['是'] },
  { key: 'trunk_flexion_at_contact', label: '触地时的躯干屈曲角度', options: OPT_BOOLEAN_STYLE('躯干屈曲', '躯干未屈曲'), riskValues: ['躯干未屈曲'] },
  { key: 'trunk_lateral_flexion_at_contact', label: '触地时的躯干侧屈情况', options: OPT_BOOLEAN_STYLE('躯干直立', '躯干未直立'), riskValues: ['躯干未直立'] },
  { key: 'ankle_plantarflexion_at_contact', label: '触地时的踝关节跖屈情况', options: OPT_BOOLEAN_STYLE('足趾到足跟', '非足趾到足跟'), riskValues: ['非足趾到足跟'] },
  { key: 'foot_external_rotation_gt_30', label: '首次触地时足外旋大于30度', options: OPT_BOOLEAN_STYLE('否', '是'), riskValues: ['是'] },
  { key: 'foot_internal_rotation_gt_30', label: '首次触地时足内旋大于30度', options: OPT_BOOLEAN_STYLE('否', '是'), riskValues: ['是'] },
  { key: 'stance_width_less_than_shoulder', label: '首次触地时站立宽度小于肩宽', options: OPT_BOOLEAN_STYLE('否', '是'), riskValues: ['是'] },
  { key: 'stance_width_greater_than_shoulder', label: '首次触地时站立宽度大于肩宽', options: OPT_BOOLEAN_STYLE('否', '是'), riskValues: ['是'] },
  { key: 'feet_symmetric_at_initial_contact', label: '首次触地时双足对称', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'knee_flexion_displacement_gt_45', label: '屈膝位移（跳跃前膝的位置）大于45度', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'knee_valgus_displacement', label: '膝外翻位移（跳跃前膝的位置）', options: OPT_BOOLEAN_STYLE('否', '是'), riskValues: ['是'] },
  { key: 'trunk_flexion_increase_at_max_knee_flexion', label: '膝关节最大屈曲时躯干屈曲角度大于首次触地时', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'hip_flexed_at_initial_contact', label: '首次触地时髋关节处在屈曲状态', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'hip_flexion_increase_at_max_knee_flexion', label: '膝关节最大屈曲时屈髋角度大于首次触地时', options: YES_NO_OPTIONS, riskValues: ['否'] },
  { key: 'sagittal_plane_joint_displacement', label: '矢状面上的关节位移', options: [
    { label: '柔和的', value: '柔和的' },
    { label: '普通的', value: '普通的' },
    { label: '僵硬的', value: '僵硬的' }
  ], riskValues: ['僵硬的'] },
  { key: 'overall_impression', label: '整体印象', options: [
    { label: '出色的', value: '出色的' },
    { label: '普通的', value: '普通的' },
    { label: '糟糕的', value: '糟糕的' }
  ], riskValues: ['糟糕的'] }
]

const createBinaryDefault = (hasSide = false): NasmBinaryObservation => {
  if (hasSide) {
    return { present: null, left: null, right: null, note: '' }
  }
  return { present: null, note: '' }
}

const createBilateralSelectDefault = (): NasmBilateralSelectObservation => ({
  left: '',
  right: '',
  overall: null,
  note: ''
})

const createSelectWithNoteDefault = (): NasmSelectWithNoteObservation => ({ value: '', note: '' })

const buildBinaryGroup = (items: NasmBinaryConfig[]): Record<string, NasmBinaryObservation> => {
  return items.reduce<Record<string, NasmBinaryObservation>>((acc, item) => {
    acc[item.key] = createBinaryDefault(item.hasSide)
    return acc
  }, {})
}

const buildBilateralGroup = (items: NasmBilateralSelectConfig[]): Record<string, NasmBilateralSelectObservation> => {
  return items.reduce<Record<string, NasmBilateralSelectObservation>>((acc, item) => {
    acc[item.key] = createBilateralSelectDefault()
    return acc
  }, {})
}

const buildLessItemsDefault = (): Record<string, NasmSelectWithNoteObservation> => {
  return LESS_ITEMS_CONFIG.reduce<Record<string, NasmSelectWithNoteObservation>>((acc, item) => {
    acc[item.key] = createSelectWithNoteDefault()
    return acc
  }, {})
}

const buildDaviesDefaultTrials = (): NasmDaviesTrial[] => {
  return [1, 2, 3].map((index) => ({
    trial_no: index,
    point_distance_inch: 36,
    point_distance_cm: 91.44,
    duration_sec: 15,
    repetition_count: null,
    repetition_quality_note: ''
  }))
}

export const buildDefaultNasmCesFormData = (): NasmCesFormData => {
  return {
    basic_info: {
      name: '',
      age: null,
      assessment_date: '',
      assessor: '',
      focus: '',
      summary_note: ''
    },
    transition_assessments: {
      overhead_squat: {
        reps_target: 5,
        modification: {
          heel_elevated: false,
          hands_on_hips: false,
          note: ''
        },
        front_view: {
          feet: buildBilateralGroup(OVERHEAD_SQUAT_FRONT_FEET),
          knee: buildBilateralGroup(OVERHEAD_SQUAT_FRONT_KNEE)
        },
        lateral_view: {
          lphc: buildBilateralGroup(OVERHEAD_SQUAT_LATERAL_LPHC),
          shoulder: buildBilateralGroup(OVERHEAD_SQUAT_LATERAL_SHOULDER)
        },
        posterior_view: {
          feet: buildBilateralGroup(OVERHEAD_SQUAT_POSTERIOR_FEET),
          lphc: buildBilateralGroup(OVERHEAD_SQUAT_POSTERIOR_LPHC)
        }
      },
      single_leg_squat: {
        reps_target: 5,
        left_support: {
          ...buildBinaryGroup(SINGLE_LEG_SQUAT_ITEMS),
          note: ''
        },
        right_support: {
          ...buildBinaryGroup(SINGLE_LEG_SQUAT_ITEMS),
          note: ''
        }
      },
      push_up: {
        reps_target: 10,
        variation: {
          kneeling_push_up: false,
          standing_cable_press: false
        },
        full_view: {
          lphc: buildBinaryGroup(PUSH_UP_LPHC_ITEMS),
          shoulder: buildBinaryGroup(PUSH_UP_SHOULDER_ITEMS),
          head_cervical: buildBinaryGroup(PUSH_UP_HEAD_ITEMS)
        }
      },
      standing_row: {
        reps_target: 10,
        variation_note: '',
        full_view: buildBinaryGroup(STANDING_ROW_ITEMS)
      },
      standing_dumbbell_overhead_press: {
        reps_target: 10,
        full_view: buildBinaryGroup(STANDING_PRESS_ITEMS)
      },
      upper_extremity_transition: {
        horizontal_abduction_test: {
          full_view: buildBinaryGroup(UE_HORIZONTAL_ABDUCTION_ITEMS)
        },
        rotation_test: {
          shoulder: buildBinaryGroup(UE_ROTATION_SHOULDER_ITEMS),
          humerus: buildBinaryGroup(UE_ROTATION_HUMERUS_ITEMS),
          humerus_wall_angle_deg: null,
          note: ''
        },
        flexion_test: {
          full_view: buildBinaryGroup(UE_FLEXION_ITEMS)
        }
      },
      star_excursion_balance_deviation_test: {
        left_side: {
          sagittal_plane_control: '未评估',
          frontal_plane_control: '未评估',
          transverse_plane_control: '未评估',
          note: ''
        },
        right_side: {
          sagittal_plane_control: '未评估',
          frontal_plane_control: '未评估',
          transverse_plane_control: '未评估',
          note: ''
        }
      }
    },
    dynamic_assessments: {
      gait_analysis: buildBinaryGroup(GAIT_ANALYSIS_ITEMS),
      tuck_jump_assessment: {
        duration_sec: 10,
        categories: {
          knee_thigh_action: buildBinaryGroup(TUCK_JUMP_KNEE_THIGH_ITEMS),
          foot_landing_position: buildBinaryGroup(TUCK_JUMP_FOOT_LANDING_ITEMS),
          plyometric_technique: buildBinaryGroup(TUCK_JUMP_PLYO_TECHNIQUE_ITEMS)
        },
        total_findings_count: null
      }
    },
    upper_extremity_davies_test: {
      trials: buildDaviesDefaultTrials(),
      total_repetition_count: null
    },
    less_test: {
      items: buildLessItemsDefault(),
      less_total_score: null
    },
    summary: {
      transition_summary: {
        head_neck: '',
        shoulder: '',
        elbow: '',
        lphc: '',
        knee: '',
        ankle_foot: ''
      },
      dynamic_summary_note: '',
      overall_ces_summary: ''
    },
    notes: {
      general_note: ''
    }
  }
}

const deepClone = (value: any) => JSON.parse(JSON.stringify(value))

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

export const mergeNasmCesFormData = (rawValue?: Record<string, any>): NasmCesFormData => {
  const defaultValue = buildDefaultNasmCesFormData()
  if (!rawValue || typeof rawValue !== 'object') {
    return defaultValue
  }
  const merged = deepClone(rawValue)
  fillMissingByTemplate(merged, defaultValue as unknown as Record<string, any>)
  return merged as NasmCesFormData
}

export const sumDaviesRepetitions = (trials?: NasmDaviesTrial[]): number | null => {
  if (!Array.isArray(trials) || trials.length === 0) {
    return null
  }
  const values = trials
    .map((item) => Number(item.repetition_count))
    .filter((item) => !Number.isNaN(item) && item >= 0)
  if (!values.length) {
    return null
  }
  return values.reduce((sum, current) => sum + current, 0)
}

export const countTuckJumpFindings = (categories: Record<string, Record<string, NasmBinaryObservation>>): number | null => {
  if (!categories || typeof categories !== 'object') {
    return null
  }
  let count = 0
  Object.values(categories).forEach((group) => {
    if (!group || typeof group !== 'object') {
      return
    }
    Object.values(group).forEach((item: any) => {
      if (item && typeof item === 'object' && item.present === true) {
        count += 1
      }
    })
  })
  return count > 0 ? count : null
}

export const estimateLessScore = (items: Record<string, NasmSelectWithNoteObservation>): number | null => {
  if (!items || typeof items !== 'object') {
    return null
  }
  let total = 0
  let filled = 0
  LESS_ITEMS_CONFIG.forEach((config) => {
    const value = items?.[config.key]?.value
    if (!value) {
      return
    }
    filled += 1
    if (config.riskValues.includes(value)) {
      total += 1
    }
  })
  if (!filled) {
    return null
  }
  return total
}
