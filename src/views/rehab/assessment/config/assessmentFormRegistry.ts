import type { Component } from 'vue'
import type { AssessmentTypeCode } from './assessmentTypeOptions'
import StaticAssessmentForm from '../components/forms/StaticAssessmentForm.vue'
import BodyCompositionForm from '../components/forms/BodyCompositionForm.vue'
import NasmCesForm from '../components/forms/NasmCesForm.vue'
import SfmaForm from '../components/forms/SfmaForm.vue'
import FmsForm from '../components/forms/FmsForm.vue'
import YbtForm from '../components/forms/YbtForm.vue'
import OpenCapForm from '../components/forms/OpenCapForm.vue'
import ObservationOnlyForm from '../components/forms/ObservationOnlyForm.vue'
import ComprehensiveAssessmentForm from '../components/forms/ComprehensiveAssessmentForm.vue'
import { buildDefaultStaticAssessmentFormData } from './staticAssessmentConfig'
import { buildDefaultNasmCesFormData } from './nasmCesConfig'
import { buildDefaultSfmaFormData } from './sfmaConfig'
import {
  buildDefaultBodyCompositionFormData,
  buildDefaultComprehensiveFormData,
  buildDefaultFmsFormData,
  buildDefaultObservationFormData,
  buildDefaultOpenCapFormData,
  buildDefaultYbtFormData
} from './structuredAssessmentConfig'

export interface AssessmentFormExpose {
  validate?: () => Promise<boolean> | boolean
  getFormData?: () => Record<string, any>
  reset?: () => void
}

export interface AssessmentFormRegistryItem {
  typeCode: AssessmentTypeCode
  typeNameZh: string
  icon?: string
  formComponent?: Component
  defaultValue: Record<string, any>
  moduleType: string
  enabled: boolean
  description?: string
}

const REGISTRY_LIST: AssessmentFormRegistryItem[] = [
  {
    typeCode: 'static_assessment',
    typeNameZh: '静态评估',
    icon: 'ep:position',
    formComponent: StaticAssessmentForm,
    defaultValue: buildDefaultStaticAssessmentFormData(),
    moduleType: 'static',
    enabled: true,
    description: '静态四视图体态、量化角度和风险提示。'
  },
  {
    typeCode: 'body_composition',
    typeNameZh: '身体成分',
    icon: 'ep:odometer',
    formComponent: BodyCompositionForm,
    defaultValue: buildDefaultBodyCompositionFormData(),
    moduleType: 'body_comp',
    enabled: true,
    description: '体成分、生长发育指标、测量质量与风险提示。'
  },
  {
    typeCode: 'nasm_ces',
    typeNameZh: 'NASM-CES',
    icon: 'ep:trend-charts',
    formComponent: NasmCesForm,
    defaultValue: buildDefaultNasmCesFormData(),
    moduleType: 'nasm',
    enabled: true,
    description: 'NASM-CES 动作偏差观测、纠正策略与风险提示。'
  },
  {
    typeCode: 'sfma',
    typeNameZh: 'SFMA',
    icon: 'ep:postcard',
    formComponent: SfmaForm,
    defaultValue: buildDefaultSfmaFormData(),
    moduleType: 'sfma',
    enabled: true,
    description: 'SFMA Top Tier 初筛、Breakout 分解与汇总分析。'
  },
  {
    typeCode: 'fms',
    typeNameZh: 'FMS',
    icon: 'ep:operation',
    formComponent: FmsForm,
    defaultValue: buildDefaultFmsFormData(),
    moduleType: 'fms',
    enabled: true,
    description: 'FMS 七项评分、清除测试、左右差与总分汇总。'
  },
  {
    typeCode: 'ybt',
    typeNameZh: 'YBT',
    icon: 'ep:compass',
    formComponent: YbtForm,
    defaultValue: buildDefaultYbtFormData(),
    moduleType: 'ybt',
    enabled: true,
    description: 'YBT 上下肢方向距离、标准化综合分与左右差。'
  },
  {
    typeCode: 'opencap',
    typeNameZh: 'OpenCap / OpenSim',
    icon: 'ep:video-camera',
    formComponent: OpenCapForm,
    defaultValue: buildDefaultOpenCapFormData(),
    moduleType: 'opencap',
    enabled: true,
    description: 'OpenCap trial、运动学指标、数据质量和人工结论。'
  },
  {
    typeCode: 'observation_only',
    typeNameZh: '人工观察记录',
    icon: 'ep:edit-pen',
    formComponent: ObservationOnlyForm,
    defaultValue: buildDefaultObservationFormData(),
    moduleType: 'observation',
    enabled: true,
    description: '动作表现、疼痛、红旗、处理建议和复查安排。'
  },
  {
    typeCode: 'comprehensive_assessment',
    typeNameZh: '综合评估',
    icon: 'ep:grid',
    formComponent: ComprehensiveAssessmentForm,
    defaultValue: buildDefaultComprehensiveFormData(),
    moduleType: 'comprehensive',
    enabled: true,
    description: '按本次需要组合多种评估模块并形成综合结论。'
  }
]

export const ASSESSMENT_FORM_REGISTRY = REGISTRY_LIST.reduce<Record<string, AssessmentFormRegistryItem>>(
  (acc, item) => {
    acc[item.typeCode] = item
    return acc
  },
  {}
)

export const getAssessmentFormRegistryItem = (typeCode?: string) => {
  if (!typeCode) {
    return undefined
  }
  return ASSESSMENT_FORM_REGISTRY[typeCode]
}

export const cloneDefaultAssessmentFormData = (typeCode?: string): Record<string, any> => {
  const item = getAssessmentFormRegistryItem(typeCode)
  if (!item) {
    return {}
  }
  return JSON.parse(JSON.stringify(item.defaultValue || {}))
}
