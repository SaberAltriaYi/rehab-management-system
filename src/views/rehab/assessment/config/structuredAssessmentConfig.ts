const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))

const isPlainObject = (value: unknown): value is Record<string, any> => {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

export const mergeStructuredAssessmentData = <T extends Record<string, any>>(
  defaults: T,
  value?: Record<string, any>
): T => {
  const merge = (base: any, source: any): any => {
    if (!isPlainObject(base) || !isPlainObject(source)) {
      return source === undefined ? clone(base) : clone(source)
    }
    const result: Record<string, any> = clone(base)
    Object.keys(source).forEach((key) => {
      result[key] = key in base ? merge(base[key], source[key]) : clone(source[key])
    })
    return result
  }
  return merge(defaults, value || {})
}

export const buildDefaultBodyCompositionFormData = () => ({
  measurements: {
    heightCm: undefined,
    weightKg: undefined,
    bmi: undefined,
    bodyFatPercent: undefined,
    skeletalMuscleKg: undefined,
    bodyWaterPercent: undefined,
    visceralFatLevel: undefined,
    waistCm: undefined,
    hipCm: undefined,
    waistHipRatio: undefined
  },
  growth: {
    stage: '',
    heightPercentile: undefined,
    weightPercentile: undefined,
    bmiPercentile: undefined
  },
  measurementMeta: {
    device: '',
    fasting: false,
    measuredAt: '',
    quality: 'good'
  },
  riskFlags: [] as string[],
  summary: {
    conclusion: '',
    recommendation: ''
  }
})

const buildFmsItem = (code: string, name: string, bilateral = true) => ({
  code,
  name,
  bilateral,
  leftScore: bilateral ? undefined : null,
  rightScore: bilateral ? undefined : null,
  score: bilateral ? undefined : undefined,
  pain: false,
  clearingTest: 'not_tested',
  note: ''
})

export const buildDefaultFmsFormData = () => ({
  items: [
    buildFmsItem('deep_squat', '深蹲', false),
    buildFmsItem('hurdle_step', '跨栏步'),
    buildFmsItem('inline_lunge', '直线弓箭步'),
    buildFmsItem('shoulder_mobility', '肩部灵活性'),
    buildFmsItem('active_straight_leg_raise', '主动直膝抬腿'),
    buildFmsItem('trunk_stability_pushup', '躯干稳定俯卧撑', false),
    buildFmsItem('rotary_stability', '旋转稳定性')
  ],
  summary: {
    totalScore: 0,
    asymmetryCount: 0,
    painDetected: false,
    incompleteCount: 7,
    riskLevel: 'pending',
    conclusion: ''
  }
})

const buildYbtRegion = (region: 'lower' | 'upper') => ({
  region,
  enabled: region === 'lower',
  limbLength: {
    left: undefined,
    right: undefined
  },
  directions:
    region === 'lower'
      ? [
          { code: 'anterior', name: '前方 A', left: undefined, right: undefined },
          { code: 'posteromedial', name: '后内侧 PM', left: undefined, right: undefined },
          { code: 'posterolateral', name: '后外侧 PL', left: undefined, right: undefined }
        ]
      : [
          { code: 'medial', name: '内侧 M', left: undefined, right: undefined },
          { code: 'inferolateral', name: '下外侧 IL', left: undefined, right: undefined },
          { code: 'superolateral', name: '上外侧 SL', left: undefined, right: undefined }
        ],
  result: {
    leftCompositePercent: undefined,
    rightCompositePercent: undefined,
    maxAsymmetryCm: undefined,
    riskFlag: false
  },
  note: ''
})

export const buildDefaultYbtFormData = () => ({
  lowerQuarter: buildYbtRegion('lower'),
  upperQuarter: buildYbtRegion('upper'),
  summary: {
    conclusion: '',
    recommendation: ''
  }
})

export const buildDefaultOpenCapMetric = () => ({
  name: '',
  side: 'none',
  minimum: undefined,
  maximum: undefined,
  rangeOfMotion: undefined,
  unit: 'deg'
})

export const buildDefaultOpenCapTrial = () => ({
  name: '',
  movement: '',
  sourceUrl: '',
  durationSeconds: undefined,
  frameRate: undefined,
  quality: 'good',
  metrics: [buildDefaultOpenCapMetric()],
  qualityFlags: [] as string[],
  note: ''
})

export const buildDefaultOpenCapFormData = () => ({
  session: {
    sessionId: '',
    capturedAt: '',
    captureDevice: '',
    processingVersion: ''
  },
  trials: [buildDefaultOpenCapTrial()],
  summary: {
    conclusion: '',
    limitation: '',
    recommendation: ''
  }
})

export const buildDefaultObservationFormData = () => ({
  context: {
    activity: '',
    environment: '',
    loadCondition: '',
    footwear: ''
  },
  pain: {
    score: undefined,
    bodyRegions: [] as string[],
    trigger: ''
  },
  observations: {
    posture: '',
    gait: '',
    balance: '',
    movementCompensation: '',
    breathing: ''
  },
  redFlags: [] as string[],
  summary: {
    conclusion: '',
    recommendation: '',
    followUp: ''
  }
})

export const COMPREHENSIVE_MODULE_OPTIONS = [
  { value: 'static_assessment', label: '静态评估' },
  { value: 'body_composition', label: '身体成分' },
  { value: 'nasm_ces', label: 'NASM-CES' },
  { value: 'sfma', label: 'SFMA' },
  { value: 'fms', label: 'FMS' },
  { value: 'ybt', label: 'YBT' },
  { value: 'opencap', label: 'OpenCap / OpenSim' },
  { value: 'observation_only', label: '人工观察记录' }
] as const

export const buildDefaultComprehensiveFormData = () => ({
  selectedModules: ['static_assessment', 'observation_only'],
  modules: {} as Record<string, Record<string, any>>,
  summary: {
    chiefProblem: '',
    conclusion: '',
    priority: '',
    recommendation: ''
  }
})
