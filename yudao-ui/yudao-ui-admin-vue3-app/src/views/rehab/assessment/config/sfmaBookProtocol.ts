export type SfmaBookStepStatus =
  | 'pending'
  | 'completed'
  | 'skipped'
  | 'not_applicable'
  | 'stopped_due_to_pain'

export type SfmaBookWorkflowStatus =
  | 'not_started'
  | 'in_progress'
  | 'completed'
  | 'skipped'
  | 'stopped_due_to_pain'

export interface SfmaBookStepDefinition {
  test_code: string
  test_name_zh: string
  side: 'none' | 'bilateral'
  criterion: string
  condition: string
}

export interface SfmaBookWorkflowDefinition {
  code: string
  name: string
  order: number
  trigger_test_codes: string[]
  steps: SfmaBookStepDefinition[]
}

export interface SfmaBookProtocolDefinition {
  protocol_id: string
  protocol_version: string
  source: string
  classification_options: string[]
  workflow_order: string[]
  rules: string[]
  workflows: SfmaBookWorkflowDefinition[]
}

export interface SfmaBookStepResult {
  test_code: string
  status: SfmaBookStepStatus
  classification: string
  left_classification: string
  right_classification: string
  value: string
  left_value: string
  right_value: string
  note: string
}

export interface SfmaBookWorkflowResult {
  workflow_code: string
  workflow_name_zh: string
  status: SfmaBookWorkflowStatus
  trigger_classifications: Array<{ test_code: string; classification: string }>
  steps: SfmaBookStepResult[]
  note: string
}

export interface SfmaBookProtocolData {
  protocol_id: string
  protocol_version: string
  source: string
  workflows: Record<string, SfmaBookWorkflowResult>
}

export const buildDefaultSfmaBookProtocolData = (): SfmaBookProtocolData => ({
  protocol_id: 'sfma_book_cn',
  protocol_version: '2026.07',
  source: '《动作-功能性动作系统筛查评估与纠正策略》第7、8章及附录3',
  workflows: {}
})

export const buildDefaultSfmaBookStepResult = (testCode: string): SfmaBookStepResult => ({
  test_code: testCode,
  status: 'pending',
  classification: '',
  left_classification: '',
  right_classification: '',
  value: '',
  left_value: '',
  right_value: '',
  note: ''
})
