import {
  buildDefaultSfmaBookProtocolData,
  SfmaBookProtocolData
} from '@/views/rehab/assessment/config/sfmaBookProtocol'

export type SfmaClassification = '' | 'FN' | 'FP' | 'DN' | 'DP'

export interface SfmaTopTierDefinition {
  test_code: string
  test_name_zh: string
  side: 'left' | 'right' | 'bilateral' | 'none'
  group: 'cervical' | 'upper_extremity' | 'multi_segmental' | 'single_leg_stance' | 'deep_squat'
  breakout_key:
    | 'cervical_pattern'
    | 'cervical_rotation_breakout'
    | 'cervical_extension_breakout'
    | 'upper_extremity_pattern1_breakout'
    | 'upper_extremity_pattern2_breakout'
    | 'msf_breakout'
    | 'mse_breakout'
    | 'msr_breakout'
    | 'arms_down_squat_breakout'
    | 'upper_extremity_pattern_left'
    | 'upper_extremity_pattern_right'
    | 'msf'
    | 'mse'
    | 'msr_left'
    | 'msr_right'
    | 'sls_left'
    | 'sls_right'
    | 'cervical_flexion_breakout'
    | 'arms_down_squat_breakout'
    | 'deep_squat'
}

export interface SfmaTopTierRecord {
  test_code: string
  test_name_zh: string
  side: 'left' | 'right' | 'bilateral' | 'none'
  classification: SfmaClassification
  pain_present: boolean
  movement_quality_note: string
  key_observation_note: string
  rom_key_value: string
  pain_vas: number | null
  needs_breakout_suggestion: boolean
  breakout_reason_text: string
  clinician_note: string
  review_priority: 'low' | 'normal' | 'high'
  caution_text: string
}

export interface SfmaBreakoutRecommendation {
  recommendation_id: string
  test_code: string
  test_name_zh: string
  classification: SfmaClassification
  breakout_key: string
  recommendation_status: 'suggested' | 'accepted' | 'skipped'
  recommendation_stage: 'dn_first' | 'fp_second' | 'dp_last'
  recommendation_order: number
  recommendation_reason: string
  recommendation_note: string
  review_priority: 'low' | 'normal' | 'high'
  caution_text: string
}

export interface SfmaBreakoutRecord {
  status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  findings: string
  rom_key_values: string
  pain_present: boolean
  pain_vas: number | null
  mobility_restriction_signs: string
  motor_control_signs: string
  asymmetry_signs: string
  stop_due_to_pain: boolean
  stop_reason: string
  clinician_note: string
  method: string
  scale: string
  source_id: string
  date: string
  sls_time_sec: number | null
}

export interface SfmaFormData {
  basic_info: {
    name: string
    age: number | null
    assessment_date: string
    assessor: string
    focus: string
    summary_note: string
  }
  top_tier: Record<string, SfmaTopTierRecord>
  breakout_recommendations: SfmaBreakoutRecommendation[]
  breakouts: Record<string, SfmaBreakoutRecord>
  book_protocol: SfmaBookProtocolData
  cervical_flexion_top_tier: SfmaCervicalFlexionTopTier
  cervical_flexion_breakout: SfmaCervicalFlexionBreakout
  cervical_extension_top_tier: SfmaCervicalExtensionTopTier
  cervical_extension_breakout: SfmaCervicalExtensionBreakout
  cervical_rotation_top_tier: SfmaCervicalRotationTopTier
  cervical_rotation_breakout: SfmaCervicalRotationBreakout
  upper_extremity_pattern1_top_tier: SfmaUpperExtremityPattern1TopTier
  upper_extremity_pattern1_breakout: SfmaUpperExtremityPattern1Breakout
  upper_extremity_pattern2_top_tier: SfmaUpperExtremityPattern2TopTier
  upper_extremity_pattern2_breakout: SfmaUpperExtremityPattern2Breakout
  msf_breakout: SfmaMsfBreakout
  mse_breakout: SfmaMseBreakout
  msr_breakout: SfmaMsrBreakout
  arms_down_squat_breakout: SfmaArmsDownSquatBreakout
  analysis_flows: Record<string, any>
  summary: Record<string, any>
  risk_precheck?: Record<string, any>
  report_mapping?: Record<string, any>
}

export interface SfmaCervicalFlexionTopTier {
  test_code: 'cervical_flexion'
  test_name_zh: '颈椎屈曲'
  classification: SfmaClassification
  pain_present: boolean
  pain_vas: number | null
  top_tier_note: string
  needs_breakout_suggestion: boolean
  breakout_target: 'cervical_flexion_breakout' | ''
  breakout_reason_text: string
  review_priority: 'low' | 'medium' | 'high'
}

export interface SfmaCervicalFlexionBreakout {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  breakout_note: string
  active_cervical_flexion_quality: '' | '正常' | '受限' | '明显受限' | '无法完成'
  active_cervical_flexion_pain: boolean
  active_cervical_flexion_rom_key: number | null
  active_cervical_flexion_end_feel_note: string
  passive_cervical_flexion_quality: '' | '正常' | '受限' | '明显受限' | '未测'
  passive_cervical_flexion_pain: boolean
  passive_cervical_flexion_rom_key: number | null
  passive_vs_active_difference: '' | '被动优于主动' | '主动与被动接近' | '被动也受限' | '未判断'
  upper_cervical_flexion_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  upper_cervical_note: string
  compensation_patterns: Array<'胸椎代偿屈曲' | '肩胛上提' | '肩部前伸' | '躯干前移' | '下巴前引' | '其他'>
  compensation_other_note: string
  related_region_influence: Array<
    | '胸椎活动受限影响'
    | '肩带紧张影响'
    | '软组织长度问题疑似参与'
    | '疼痛抑制影响'
    | '控制障碍疑似参与'
    | '暂不明确'
  >
  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏疼痛主导'
    | '更偏运动控制问题'
    | '需进一步颈椎解析'
    | '需结合其他模式综合判断'
  >
  breakout_summary_text: string
  needs_manual_review: boolean
}

export interface SfmaCervicalExtensionTopTier {
  test_code: 'cervical_extension'
  test_name_zh: '颈椎伸展'
  classification: SfmaClassification
  pain_present: boolean
  pain_vas: number | null
  top_tier_note: string
  needs_breakout_suggestion: boolean
  breakout_target: 'cervical_extension_breakout' | ''
  breakout_reason_text: string
  review_priority: 'low' | 'medium' | 'high'
}

export interface SfmaCervicalExtensionBreakout {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  breakout_note: string
  active_cervical_extension_quality: '' | '正常' | '受限' | '明显受限' | '无法完成'
  active_cervical_extension_pain: boolean
  active_cervical_extension_rom_key: number | null
  active_cervical_extension_end_feel_note: string
  passive_cervical_extension_quality: '' | '正常' | '受限' | '明显受限' | '未测'
  passive_cervical_extension_pain: boolean
  passive_cervical_extension_rom_key: number | null
  passive_vs_active_difference: '' | '被动优于主动' | '主动与被动接近' | '被动也受限' | '未判断'
  upper_cervical_extension_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  upper_cervical_note: string
  compensation_patterns: Array<'胸椎代偿伸展' | '肩胛上提' | '肩部后移受限' | '躯干后仰代偿' | '下巴前引' | '其他'>
  compensation_other_note: string
  related_region_influence: Array<
    | '胸椎伸展受限影响'
    | '肩带紧张影响'
    | '软组织长度问题疑似参与'
    | '疼痛抑制影响'
    | '控制障碍疑似参与'
    | '暂不明确'
  >
  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏疼痛主导'
    | '更偏运动控制问题'
    | '需进一步颈椎解析'
    | '需结合其他模式综合判断'
  >
  breakout_summary_text: string
  needs_manual_review: boolean
}

export interface SfmaCervicalRotationTopTierSide {
  test_code: 'cervical_rotation_left' | 'cervical_rotation_right'
  test_name_zh: '颈椎旋转（左）' | '颈椎旋转（右）'
  side: 'left' | 'right'
  classification: SfmaClassification
  pain_present: boolean
  pain_vas: number | null
  top_tier_note: string
  needs_breakout_suggestion: boolean
  breakout_target: 'cervical_rotation_breakout' | ''
  breakout_reason_text: string
  review_priority: 'low' | 'medium' | 'high'
}

export interface SfmaCervicalRotationTopTier {
  left: SfmaCervicalRotationTopTierSide
  right: SfmaCervicalRotationTopTierSide
}

export interface SfmaCervicalRotationBreakoutSide {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  breakout_note: string
  active_cervical_rotation_quality: '' | '正常' | '受限' | '明显受限' | '无法完成'
  active_cervical_rotation_pain: boolean
  active_cervical_rotation_rom_key: number | null
  passive_cervical_rotation_quality: '' | '正常' | '受限' | '明显受限' | '未测'
  passive_cervical_rotation_pain: boolean
  passive_cervical_rotation_rom_key: number | null
  passive_vs_active_difference: '' | '被动优于主动' | '主动与被动接近' | '被动也受限' | '未判断'
  upper_cervical_rotation_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  upper_cervical_note: string
  compensation_patterns: Array<'胸椎旋转代偿' | '肩胛上提' | '躯干侧倾代偿' | '下巴前引' | '肩部前伸' | '其他'>
  compensation_other_note: string
  related_region_influence: Array<
    | '胸椎旋转受限影响'
    | '肩带紧张影响'
    | '软组织长度问题疑似参与'
    | '疼痛抑制影响'
    | '控制障碍疑似参与'
    | '暂不明确'
  >
  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏疼痛主导'
    | '更偏运动控制问题'
    | '需进一步颈椎解析'
    | '需结合其他模式综合判断'
  >
  breakout_summary_text: string
  needs_manual_review: boolean
}

export interface SfmaCervicalRotationBreakout {
  left: SfmaCervicalRotationBreakoutSide
  right: SfmaCervicalRotationBreakoutSide
  asymmetry_focus: string
  overall_note: string
}

export interface SfmaUpperExtremityPattern1TopTierSide {
  test_code: 'upper_extremity_pattern1_left' | 'upper_extremity_pattern1_right'
  test_name_zh: '上肢模式1（左）' | '上肢模式1（右）'
  side: 'left' | 'right'
  classification: SfmaClassification
  pain_present: boolean
  pain_vas: number | null
  top_tier_note: string
  needs_breakout_suggestion: boolean
  breakout_target: 'upper_extremity_pattern1_breakout' | ''
  breakout_reason_text: string
  review_priority: 'low' | 'medium' | 'high'
}

export interface SfmaUpperExtremityPattern1TopTier {
  left: SfmaUpperExtremityPattern1TopTierSide
  right: SfmaUpperExtremityPattern1TopTierSide
}

export interface SfmaUpperExtremityPattern1BreakoutSide {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  breakout_note: string
  prone_active_result: SfmaClassification
  prone_active_pain_vas: number | null
  prone_active_note: string
  prone_passive_result: SfmaClassification
  prone_passive_pain_vas: number | null
  prone_passive_note: string
  supine_interactive_result: SfmaClassification
  supine_interactive_pain_vas: number | null
  supine_interactive_note: string
  flow_recommendation_text: string
  local_biomechanics_needed: boolean
  stop_and_treat: boolean
  active_ue_pattern1_quality: '' | '正常' | '受限' | '明显受限' | '无法完成'
  active_ue_pattern1_pain: boolean
  active_ue_pattern1_rom_key: number | null
  scapular_control_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  thoracic_influence_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  glenohumeral_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  compensation_patterns: Array<'肩胛上提' | '躯干侧倾代偿' | '肘屈曲代偿' | '腕代偿' | '头前伸' | '其他'>
  compensation_other_note: string
  related_region_influence: Array<
    | '胸椎活动受限影响'
    | '肩带紧张影响'
    | '软组织长度问题疑似参与'
    | '疼痛抑制影响'
    | '控制障碍疑似参与'
    | '暂不明确'
  >
  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏疼痛主导'
    | '更偏运动控制问题'
    | '需进一步肩带/胸椎解析'
    | '需结合其他模式综合判断'
  >
  breakout_summary_text: string
  needs_manual_review: boolean
}

export interface SfmaUpperExtremityPattern1Breakout {
  left: SfmaUpperExtremityPattern1BreakoutSide
  right: SfmaUpperExtremityPattern1BreakoutSide
  asymmetry_focus: string
  overall_note: string
}

export interface SfmaUpperExtremityPattern2TopTierSide {
  test_code: 'upper_extremity_pattern2_left' | 'upper_extremity_pattern2_right'
  test_name_zh: '上肢模式2（左）' | '上肢模式2（右）'
  side: 'left' | 'right'
  classification: SfmaClassification
  pain_present: boolean
  pain_vas: number | null
  top_tier_note: string
  needs_breakout_suggestion: boolean
  breakout_target: 'upper_extremity_pattern2_breakout' | ''
  breakout_reason_text: string
  review_priority: 'low' | 'medium' | 'high'
}

export interface SfmaUpperExtremityPattern2TopTier {
  left: SfmaUpperExtremityPattern2TopTierSide
  right: SfmaUpperExtremityPattern2TopTierSide
}

export interface SfmaUpperExtremityPattern2BreakoutSide {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped' | 'partial' | 'stopped_due_to_pain'
  breakout_note: string
  prone_active_result: SfmaClassification
  prone_active_pain_vas: number | null
  prone_active_note: string
  prone_passive_result: SfmaClassification
  prone_passive_pain_vas: number | null
  prone_passive_note: string
  supine_interactive_result: SfmaClassification
  supine_interactive_pain_vas: number | null
  supine_interactive_note: string
  flow_recommendation_text: string
  local_biomechanics_needed: boolean
  stop_and_treat: boolean
  active_ue_pattern2_quality: '' | '正常' | '受限' | '明显受限' | '无法完成'
  active_ue_pattern2_pain: boolean
  active_ue_pattern2_rom_key: number | null
  scapular_control_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  thoracic_influence_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  glenohumeral_observation: '' | '正常' | '疑似受限' | '疑似代偿' | '未测'
  compensation_patterns: Array<'肩胛下沉代偿' | '躯干侧倾代偿' | '肘屈曲代偿' | '腕代偿' | '头前伸' | '其他'>
  compensation_other_note: string
  related_region_influence: Array<
    | '胸椎活动受限影响'
    | '肩带紧张影响'
    | '软组织长度问题疑似参与'
    | '疼痛抑制影响'
    | '控制障碍疑似参与'
    | '暂不明确'
  >
  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏疼痛主导'
    | '更偏运动控制问题'
    | '需进一步肩带/胸椎解析'
    | '需结合其他模式综合判断'
  >
  breakout_summary_text: string
  needs_manual_review: boolean
}

export interface SfmaUpperExtremityPattern2Breakout {
  left: SfmaUpperExtremityPattern2BreakoutSide
  right: SfmaUpperExtremityPattern2BreakoutSide
  asymmetry_focus: string
  overall_note: string
}

export interface SfmaMsfAnalysisFlowNode {
  node_code: string
  node_name_zh: string
  purpose: string
  instructions: string
  clinical_notes: string
  result_type: string
  next_step_rules: string
  stop_if_pain: boolean
  summary_text: string
  [key: string]: any
}

export interface SfmaMsfAnalysisSummary {
  single_vs_bilateral_pattern: string
  primary_region: string
  likely_pattern: string[]
  hip_flexion_mobility_issue: boolean
  posterior_chain_ted_issue: boolean
  spinal_flexion_mobility_issue: boolean
  loaded_flexion_smcd_issue: boolean
  base_flexion_pattern_smcd_issue: boolean
  core_or_active_hip_flexion_smcd_issue: boolean
  rotation_flow_needed: boolean
  stop_and_treat_pain: boolean
  manual_review_required: boolean
  summary_text: string
}

export interface SfmaMsfAnalysis {
  flow_nodes: {
    single_leg_stance_forward_bend: SfmaMsfAnalysisFlowNode
    long_sit_toe_touch: SfmaMsfAnalysisFlowNode
    active_straight_leg_raise: SfmaMsfAnalysisFlowNode
    passive_straight_leg_raise: SfmaMsfAnalysisFlowNode
    prone_backward_rocking: SfmaMsfAnalysisFlowNode
    supine_double_knees_to_chest: SfmaMsfAnalysisFlowNode
    rolling_analysis_result: SfmaMsfAnalysisFlowNode
  }
  flexion_flow: {
    single_leg_stance_forward_bend: SfmaMsfAnalysisFlowNode
    long_sit_toe_touch: SfmaMsfAnalysisFlowNode
    active_straight_leg_raise: SfmaMsfAnalysisFlowNode
    passive_straight_leg_raise: SfmaMsfAnalysisFlowNode
    prone_backward_rocking: SfmaMsfAnalysisFlowNode
    supine_double_knees_to_chest: SfmaMsfAnalysisFlowNode
    rolling_analysis_result: SfmaMsfAnalysisFlowNode
  }
  summary: SfmaMsfAnalysisSummary
}

export interface SfmaMseAnalysisFlowNode {
  node_code: string
  node_name_zh: string
  purpose: string
  instructions: string
  clinical_notes: string
  result_type: string
  next_step_rules: string
  stop_if_pain: boolean
  summary_text: string
  [key: string]: any
}

export interface SfmaMseAnalysisSummary {
  primary_region: string
  likely_pattern: string[]
  thoracic_extension_issue: boolean
  lumbar_extension_issue: boolean
  weight_bearing_stability_issue: boolean
  pain_dominant: boolean
  upper_body_extension_flow_needed: boolean
  lower_body_extension_flow_needed: boolean
  next_flow_targets: string[]
  stop_and_treat_pain: boolean
  manual_review_required: boolean
  summary_text: string
}

export interface SfmaMseAnalysis {
  spinal_extension_flow: {
    trunk_extension_without_upper_extremity: SfmaMseAnalysisFlowNode
    single_leg_stance_trunk_extension: SfmaMseAnalysisFlowNode
    prone_press_up: SfmaMseAnalysisFlowNode
    lumbar_fixed_internal_rotation_active_extension_rotation: SfmaMseAnalysisFlowNode
    lumbar_fixed_internal_rotation_passive_extension_rotation: SfmaMseAnalysisFlowNode
    prone_elbow_supported_extension_rotation: SfmaMseAnalysisFlowNode
  }
  lower_body_extension_flow: {
    standing_hip_extension: SfmaMseAnalysisFlowNode
    prone_active_hip_extension: SfmaMseAnalysisFlowNode
    prone_passive_hip_extension: SfmaMseAnalysisFlowNode
    rolling_analysis_result_lower: SfmaMseAnalysisFlowNode
    faber_test: SfmaMseAnalysisFlowNode
    modified_thomas_test: SfmaMseAnalysisFlowNode
  }
  upper_body_extension_flow: {
    single_shoulder_extension: SfmaMseAnalysisFlowNode
    supine_double_hip_flexion_lat_stretch: SfmaMseAnalysisFlowNode
    supine_double_hip_extension_lat_stretch: SfmaMseAnalysisFlowNode
    lumbar_fixed_external_rotation_extension: SfmaMseAnalysisFlowNode
    lumbar_fixed_internal_rotation_active_extension_rotation_upper: SfmaMseAnalysisFlowNode
    lumbar_fixed_internal_rotation_passive_extension_rotation_upper: SfmaMseAnalysisFlowNode
  }
  summary: SfmaMseAnalysisSummary
}

export interface SfmaMsfBreakout {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped'
  breakout_reason_from_top_tier: string
  breakout_note: string
  needs_manual_review: boolean

  single_leg_standing_forward_flexion_result: '' | '双侧功能正常且无痛' | '双侧功能障碍或疼痛' | '单侧功能障碍或疼痛'
  single_leg_standing_forward_flexion_asymmetry: '' | '无明显左右差' | '左侧更差' | '右侧更差' | '难以判断'
  single_leg_standing_forward_flexion_note: string

  long_sit_toe_touch_result: SfmaClassification
  long_sit_toe_touch_reach_status: '' | '可触及足趾' | '未触及足趾' | '未测'
  long_sit_sacral_angle_deg: number | null
  long_sit_sacral_angle_status: '' | '正常(≥80°)' | '受限(<80°)' | '未测'
  long_sit_toe_touch_note: string

  rolling_result: SfmaClassification
  rolling_note: string

  aslr_result: SfmaClassification
  aslr_left_deg: number | null
  aslr_right_deg: number | null
  aslr_note: string

  pslr_result: SfmaClassification
  pslr_left_deg: number | null
  pslr_right_deg: number | null
  pslr_vs_aslr_interpretation: '' | 'PSLR>80°' | 'PSLR<80°且比ASLR大10°以上' | 'PSLR≤ASLR' | '未判断'
  pslr_note: string

  prone_rock_back_result: SfmaClassification
  prone_rock_back_note: string

  supine_knees_to_chest_result: SfmaClassification
  supine_knees_to_chest_note: string

  flow_next_step:
    | ''
    | '继续长坐位触摸足趾'
    | '继续进入旋转动作解析'
    | '继续滚动解析测试'
    | '继续主动直腿抬高'
    | '继续被动直腿抬高'
    | '继续俯卧位向后摆动'
    | '继续仰卧位双膝触胸'
    | '停止并优先处理疼痛'
    | '流程已完成'
    | '需结合人工复核判断下一步'
  flow_algorithm_note: string
  sacral_angle_threshold_ref: number
  aslr_threshold_ref: number
  pslr_threshold_ref: number

  active_flexion_global_quality: '' | '正常' | '轻度受限' | '明显受限' | '无法完成'
  active_flexion_pain: boolean
  active_flexion_pain_area: Array<'颈肩' | '胸背' | '腰背' | '髋后侧' | '大腿后侧' | '膝后侧' | '小腿后侧' | '其他'>
  active_flexion_pain_other_note: string
  fingertips_to_floor_status: '' | '可轻松触地' | '接近触地' | '明显不能触地' | '未测'
  fingertips_to_floor_distance_cm: number | null
  uniform_curve_observation: '' | '屈曲链条流畅' | '局部僵硬' | '分段代偿明显' | '难以判断'
  movement_quality_note: string

  hamstring_posterior_chain_tension: '' | '不明显' | '轻度' | '中度' | '明显'
  left_right_posterior_chain_asymmetry: '' | '无明显左右差' | '左侧更紧张/受限' | '右侧更紧张/受限' | '难以判断'
  ankle_dorsiflexion_influence: '' | '不明显' | '疑似有影响' | '明显有影响' | '未测'
  knee_extension_limitation_influence: '' | '不明显' | '疑似有影响' | '明显有影响' | '未测'
  lower_extremity_note: string

  hip_flexion_contribution: '' | '正常' | '减少' | '明显不足' | '难以判断'
  pelvis_anterior_posterior_control: '' | '正常' | '骨盆运动受限' | '骨盆代偿明显' | '难以判断'
  left_right_hip_asymmetry: '' | '无明显左右差' | '左侧受限更明显' | '右侧受限更明显' | '难以判断'
  hip_pelvis_note: string

  lumbar_flexion_participation: '' | '正常' | '减少' | '明显不足' | '过度代偿'
  thoracic_flexion_participation: '' | '正常' | '减少' | '明显不足' | '过度代偿'
  segmental_spinal_mobility_observation: '' | '整体协调' | '局部僵硬' | '分段不均' | '难以判断'
  spine_thorax_note: string

  shoulder_girdle_relaxation: '' | '正常' | '紧张' | '明显紧张' | '难以判断'
  upper_extremity_hanging_pattern: '' | '自然下垂' | '伴明显紧张' | '左右不对称' | '难以判断'
  shoulder_upper_extremity_note: string

  compensation_patterns: Array<'躯干偏移' | '左右旋转代偿' | '颈部代偿' | '肩带紧张代偿' | '骨盆偏移' | '髋部代偿' | '膝屈曲代偿' | '足踝代偿' | '其他'>
  compensation_other_note: string
  pain_dominant_pattern: '' | '否' | '疑似是' | '明显是'
  symptom_irritability: '' | '低' | '中' | '高' | '不明确'
  pain_control_priority_hint: '' | '否' | '是，建议优先疼痛管理' | '是，建议优先人工复核'

  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏后侧链张力限制'
    | '更偏髋/骨盆参与不足'
    | '更偏脊柱分节活动受限'
    | '更偏运动控制问题'
    | '更偏疼痛主导'
    | '需结合其他模式综合判断'
  >
  primary_restriction_chain: Array<
    | '足踝-后侧链'
    | '膝后侧-后侧链'
    | '髋-骨盆链'
    | '腰椎-骨盆链'
    | '胸椎-肩带链'
    | '全链条控制不足'
    | '暂不明确'
  >
  primary_control_deficit_chain: Array<
    | 'LPHC控制不足'
    | '髋主导不足'
    | '旋转控制不足'
    | '左右对称控制不足'
    | '躯干控制不足'
    | '暂不明确'
  >
  left_right_asymmetry_focus: '' | '无明显左右差' | '左侧问题更突出' | '右侧问题更突出' | '左右均有但模式不同' | '难以判断'

  breakout_summary_text: string
  clinical_meaning_hint: string
  training_direction_hint: string
  reassessment_priority: 'low' | 'medium' | 'high'
  pause_or_referral_hint: '' | '无需' | '建议优先人工复核' | '建议结合进一步医学评估' | '建议暂缓推进训练'
  msf_analysis: SfmaMsfAnalysis
}

export interface SfmaMseBreakout {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped'
  breakout_reason_from_top_tier: string
  breakout_note: string
  needs_manual_review: boolean

  active_extension_global_quality: '' | '正常' | '轻度受限' | '明显受限' | '无法完成'
  active_extension_pain: boolean
  active_extension_pain_area: Array<'颈肩' | '胸背' | '腰背' | '髋前侧' | '膝前侧' | '小腿/踝前侧' | '其他'>
  active_extension_pain_other_note: string
  extension_curve_observation: '' | '伸展链条流畅' | '局部僵硬' | '分段代偿明显' | '难以判断'
  center_of_mass_control: '' | '平衡稳定' | '轻度不稳' | '明显不稳' | '难以判断'
  movement_quality_note: string

  ankle_stability_or_mobility_influence: '' | '不明显' | '疑似有影响' | '明显有影响' | '未测'
  knee_extension_or_locking_pattern: '' | '正常' | '过度锁定' | '屈曲代偿' | '难以判断'
  lower_extremity_support_symmetry: '' | '对称' | '左侧支撑更差' | '右侧支撑更差' | '难以判断'
  lower_extremity_note: string

  hip_extension_contribution: '' | '正常' | '减少' | '明显不足' | '难以判断'
  anterior_hip_mobility_limitation_suspected: '' | '否' | '疑似' | '明显'
  pelvis_control_pattern: '' | '正常' | '骨盆前移代偿' | '骨盆控制不足' | '难以判断'
  lphc_control_during_extension: '' | '正常' | '轻度控制不足' | '明显控制不足' | '难以判断'
  left_right_hip_asymmetry: '' | '无明显左右差' | '左侧问题更突出' | '右侧问题更突出' | '难以判断'
  hip_pelvis_note: string

  lumbar_extension_participation: '' | '正常' | '减少' | '明显不足' | '过度代偿'
  thoracic_extension_participation: '' | '正常' | '减少' | '明显不足' | '过度代偿'
  extension_distribution_observation: '' | '分布协调' | '偏腰椎代偿' | '偏胸椎不足' | '整体僵硬' | '难以判断'
  posterior_chain_loading_pattern: '' | '正常' | '代偿明显' | '难以判断'
  spine_thorax_note: string

  shoulder_flexion_contribution: '' | '正常' | '减少' | '明显不足' | '难以判断'
  shoulder_girdle_compensation: '' | '不明显' | '轻度代偿' | '明显代偿' | '难以判断'
  overhead_pattern_limitation_suspected: '' | '否' | '疑似' | '明显'
  shoulder_upper_extremity_note: string

  compensation_patterns: Array<
    | '腰椎过伸代偿'
    | '骨盆前移/前倾代偿'
    | '髋未参与'
    | '膝锁定代偿'
    | '肩带耸肩/代偿'
    | '颈部代偿'
    | '左右偏移'
    | '其他'
  >
  compensation_other_note: string
  pain_dominant_pattern: '' | '否' | '疑似是' | '明显是'
  symptom_irritability: '' | '低' | '中' | '高' | '不明确'
  pain_control_priority_hint: '' | '否' | '是，建议优先疼痛管理' | '是，建议优先人工复核'

  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏髋伸展不足'
    | '更偏胸椎伸展不足'
    | '更偏腰盆控制问题'
    | '更偏肩带/上肢参与不足'
    | '更偏运动控制问题'
    | '更偏疼痛主导'
    | '需结合其他模式综合判断'
  >
  primary_restriction_chain: Array<
    | '足踝-下肢支撑链'
    | '髋前侧-骨盆链'
    | '腰椎-骨盆链'
    | '胸椎-肩带链'
    | '过头模式链'
    | '暂不明确'
  >
  primary_control_deficit_chain: Array<
    | 'LPHC控制不足'
    | '髋主导不足'
    | '伸展模式控制不足'
    | '左右对称控制不足'
    | '躯干控制不足'
    | '暂不明确'
  >
  left_right_asymmetry_focus: '' | '无明显左右差' | '左侧问题更突出' | '右侧问题更突出' | '左右均有但模式不同' | '难以判断'

  breakout_summary_text: string
  clinical_meaning_hint: string
  training_direction_hint: string
  reassessment_priority: 'low' | 'medium' | 'high'
  pause_or_referral_hint: '' | '无需' | '建议优先人工复核' | '建议结合进一步医学评估' | '建议暂缓推进训练'
  mse_analysis: SfmaMseAnalysis
}

export interface SfmaArmsDownSquatBreakout {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped'
  breakout_reason_from_screening: string
  breakout_note: string
  needs_manual_review: boolean

  squat_global_quality: '' | '正常' | '轻度异常' | '中度异常' | '明显异常' | '无法完成'
  squat_depth_level: '' | '深度充分' | '接近平行' | '未达平行' | '明显受限' | '难以判断'
  descent_control: '' | '正常' | '轻度控制不足' | '明显控制不足' | '难以判断'
  ascent_control: '' | '正常' | '轻度控制不足' | '明显控制不足' | '难以判断'
  squat_rhythm_observation: '' | '节奏流畅' | '轻度中断' | '明显中断/犹豫' | '难以判断'
  movement_quality_note: string

  ankle_dorsiflexion_limitation_suspected: '' | '否' | '左侧疑似' | '右侧疑似' | '双侧疑似' | '明显双侧受限'
  heel_rise_pattern: '' | '无' | '左侧明显' | '右侧明显' | '双侧明显'
  foot_pronation_control: '' | '正常' | '左侧塌陷更明显' | '右侧塌陷更明显' | '双侧塌陷' | '难以判断'
  rearfoot_stability: '' | '正常' | '左侧不稳' | '右侧不稳' | '双侧不稳' | '难以判断'
  foot_tripod_or_pressure_strategy: '' | '支撑均衡' | '前足偏重' | '后足偏重' | '内侧偏重' | '外侧偏重' | '难以判断'
  foot_ankle_note: string

  knee_valgus_control: '' | '无' | '左侧明显' | '右侧明显' | '双侧明显'
  knee_varus_or_outward_shift: '' | '无' | '左侧明显' | '右侧明显' | '双侧明显'
  knee_forward_translation_pattern: '' | '正常' | '偏少' | '偏多' | '左右不一致' | '难以判断'
  knee_wobble_or_instability:
    | ''
    | '无'
    | '左侧轻度'
    | '右侧轻度'
    | '左侧明显'
    | '右侧明显'
    | '双侧明显'
  knee_note: string

  hip_flexion_contribution: '' | '正常' | '偏少' | '明显不足' | '难以判断'
  hip_control_asymmetry: '' | '无明显左右差' | '左侧控制较差' | '右侧控制较差' | '双侧均差'
  pelvic_shift_pattern: '' | '无明显偏移' | '向左偏移' | '向右偏移' | '难以判断'
  pelvic_rotation_suspected: '' | '否' | '疑似' | '明显'
  hip_pelvis_dissociation: '' | '正常' | '欠协调' | '明显失衡' | '难以判断'
  hip_pelvis_note: string

  excessive_forward_lean: '' | '无' | '轻度' | '明显'
  lumbar_rounding: '' | '无' | '轻度' | '明显'
  lumbar_extension_or_arching: '' | '无' | '轻度' | '明显'
  lphc_control_observation: '' | '正常' | '轻度不足' | '明显不足' | '难以判断'
  trunk_shift_or_rotation: '' | '无明显异常' | '左偏/左旋更明显' | '右偏/右旋更明显' | '双向异常' | '难以判断'
  trunk_lphc_note: string

  left_right_asymmetry_global: '' | '无明显左右差' | '左侧问题更突出' | '右侧问题更突出' | '双侧均差但模式不同'
  compensation_patterns: Array<
    | '足外八代偿'
    | '足过度旋前代偿'
    | '膝内扣代偿'
    | '髋控制不足代偿'
    | '骨盆侧移代偿'
    | '躯干前倾代偿'
    | '躯干旋转/偏移代偿'
    | 'heel rise 代偿'
    | '其他'
  >
  compensation_other_note: string
  primary_compensation_chain_note: string

  pain_present: boolean
  pain_vas: number | null
  pain_area: Array<'足踝' | '膝' | '髋' | '腰背' | '其他'>
  pain_area_other_note: string
  pain_dominant_pattern: '' | '否' | '疑似是' | '明显是'
  symptom_irritability: '' | '低' | '中' | '高' | '不明确'
  pain_control_priority_hint: '' | '否' | '是，建议优先疼痛管理' | '是，建议优先人工复核'

  breakout_preliminary_direction: Array<
    | '更偏踝活动度限制'
    | '更偏足踝稳定不足'
    | '更偏膝对线/控制问题'
    | '更偏髋控制问题'
    | '更偏髋活动度限制'
    | '更偏骨盆/LPHC控制问题'
    | '更偏躯干控制问题'
    | '更偏左右不对称'
    | '更偏疼痛主导'
    | '需结合其他动作综合判断'
  >
  primary_restriction_chain: Array<'足踝-支撑链' | '膝-下肢对线链' | '髋-骨盆链' | 'LPHC-躯干控制链' | '左右不对称链' | '暂不明确'>
  primary_control_deficit_chain: Array<
    | 'LPHC控制不足'
    | '髋主导不足'
    | '深蹲模式控制不足'
    | '左右对称控制不足'
    | '单侧支撑控制不足'
    | '暂不明确'
  >
  risk_precheck_level: '' | 'low' | 'medium' | 'high'
  risk_tags: Array<
    | 'lower_extremity_alignment_attention'
    | 'lphc_stability_attention'
    | 'asymmetry_attention'
    | 'squat_pattern_attention'
    | 'pain_attention'
    | 'reassessment_attention'
  >

  breakout_summary_text: string
  clinical_meaning_hint: string
  training_direction_hint: string
  reassessment_priority: 'low' | 'medium' | 'high'
  pause_or_referral_hint: '' | '无需' | '建议优先人工复核' | '建议结合进一步医学评估' | '建议暂缓推进训练'
}

export interface SfmaMsrBreakoutSide {
  breakout_status: 'not_started' | 'in_progress' | 'completed' | 'skipped'
  rotation_side: 'left' | 'right'
  breakout_reason_from_top_tier: string
  breakout_note: string
  needs_manual_review: boolean

  active_rotation_global_quality: '' | '正常' | '轻度受限' | '明显受限' | '无法完成'
  active_rotation_pain: boolean
  active_rotation_pain_area: Array<'颈肩' | '胸背' | '腰背' | '髋' | '骨盆周围' | '下肢支撑侧' | '其他'>
  active_rotation_pain_other_note: string
  global_rotation_quality_note: string
  rotation_range_key: number | null

  stance_stability_observation: '' | '稳定' | '轻度不稳' | '明显不稳' | '难以判断'
  ankle_foot_support_influence: '' | '不明显' | '疑似有影响' | '明显有影响' | '未测'
  lower_extremity_loading_asymmetry: '' | '无明显左右差' | '左侧支撑更差' | '右侧支撑更差' | '难以判断'
  knee_control_influence: '' | '不明显' | '疑似有影响' | '明显有影响' | '未测'
  lower_extremity_note: string

  hip_rotation_contribution: '' | '正常' | '减少' | '明显不足' | '难以判断'
  pelvis_rotation_control: '' | '正常' | '控制不足' | '代偿明显' | '难以判断'
  hip_pelvis_dissociation: '' | '正常' | '疑似差' | '明显差' | '未测'
  left_right_hip_rotation_asymmetry: '' | '无明显左右差' | '左侧受限更明显' | '右侧受限更明显' | '难以判断'
  hip_pelvis_note: string

  thoracic_rotation_participation: '' | '正常' | '减少' | '明显不足' | '过度代偿'
  lumbar_rotation_participation: '' | '正常' | '减少' | '过度代偿' | '难以判断'
  rotation_distribution_observation: '' | '分布协调' | '偏腰椎代偿' | '偏胸椎不足' | '整体僵硬' | '难以判断'
  thorax_pelvis_coupling_observation: '' | '协调' | '欠协调' | '明显失衡' | '难以判断'
  spine_thorax_note: string

  shoulder_girdle_participation: '' | '正常' | '减少' | '明显不足' | '难以判断'
  upper_extremity_assist_pattern: '' | '自然' | '代偿明显' | '左右不对称' | '难以判断'
  shoulder_thorax_link_observation: '' | '协调' | '欠协调' | '明显失衡' | '难以判断'
  shoulder_upper_extremity_note: string

  compensation_patterns: Array<
    | '骨盆提前旋转'
    | '腰椎代偿旋转'
    | '胸廓旋转不足'
    | '肩带代偿'
    | '下肢支撑偏移'
    | '左右偏移'
    | '重心转移异常'
    | '颈部代偿'
    | '其他'
  >
  compensation_other_note: string
  pain_dominant_pattern: '' | '否' | '疑似是' | '明显是'
  symptom_irritability: '' | '低' | '中' | '高' | '不明确'
  pain_control_priority_hint: '' | '否' | '是，建议优先疼痛管理' | '是，建议优先人工复核'

  breakout_preliminary_direction: Array<
    | '更偏活动度限制'
    | '更偏髋旋转参与不足'
    | '更偏骨盆旋转控制差'
    | '更偏胸椎旋转不足'
    | '更偏腰椎代偿'
    | '更偏运动控制问题'
    | '更偏疼痛主导'
    | '需结合其他模式综合判断'
  >
  primary_restriction_chain: Array<
    | '足踝-下肢支撑链'
    | '髋-骨盆旋转链'
    | '腰椎-骨盆代偿链'
    | '胸椎-胸廓旋转链'
    | '肩带-胸廓链'
    | '暂不明确'
  >
  primary_control_deficit_chain: Array<
    | 'LPHC控制不足'
    | '旋转控制不足'
    | '左右对称控制不足'
    | '躯干控制不足'
    | '单侧支撑控制不足'
    | '暂不明确'
  >
  side_specific_priority: '' | '左旋问题更突出' | '右旋问题更突出' | '双侧均有但模式不同' | '无明显方向性差异' | '难以判断'
  compare_with_other_side_note: string

  breakout_summary_text: string
  clinical_meaning_hint: string
  training_direction_hint: string
  reassessment_priority: 'low' | 'medium' | 'high'
  pause_or_referral_hint: '' | '无需' | '建议优先人工复核' | '建议结合进一步医学评估' | '建议暂缓推进训练'
}

export interface SfmaMsrBreakout {
  left: SfmaMsrBreakoutSide
  right: SfmaMsrBreakoutSide
  asymmetry_focus: string
  overall_note: string
}

export const SFMA_CLASSIFICATION_OPTIONS = [
  { label: 'FN', value: 'FN' },
  { label: 'FP', value: 'FP' },
  { label: 'DN', value: 'DN' },
  { label: 'DP', value: 'DP' }
]

export const SFMA_REVIEW_PRIORITY_OPTIONS = [
  { label: '低', value: 'low' },
  { label: '普通', value: 'normal' },
  { label: '高', value: 'high' }
]

export const SFMA_TOP_TIER_DEFINITIONS: SfmaTopTierDefinition[] = [
  {
    test_code: 'cervical_flexion',
    test_name_zh: '颈椎屈曲',
    side: 'none',
    group: 'cervical',
    breakout_key: 'cervical_flexion_breakout'
  },
  {
    test_code: 'cervical_extension',
    test_name_zh: '颈椎伸展',
    side: 'none',
    group: 'cervical',
    breakout_key: 'cervical_extension_breakout'
  },
  {
    test_code: 'cervical_rotation_left',
    test_name_zh: '颈椎旋转（左）',
    side: 'left',
    group: 'cervical',
    breakout_key: 'cervical_rotation_breakout'
  },
  {
    test_code: 'cervical_rotation_right',
    test_name_zh: '颈椎旋转（右）',
    side: 'right',
    group: 'cervical',
    breakout_key: 'cervical_rotation_breakout'
  },
  {
    test_code: 'upper_extremity_pattern1_left',
    test_name_zh: '上肢模式1（左）',
    side: 'left',
    group: 'upper_extremity',
    breakout_key: 'upper_extremity_pattern1_breakout'
  },
  {
    test_code: 'upper_extremity_pattern1_right',
    test_name_zh: '上肢模式1（右）',
    side: 'right',
    group: 'upper_extremity',
    breakout_key: 'upper_extremity_pattern1_breakout'
  },
  {
    test_code: 'upper_extremity_pattern2_left',
    test_name_zh: '上肢模式2（左）',
    side: 'left',
    group: 'upper_extremity',
    breakout_key: 'upper_extremity_pattern2_breakout'
  },
  {
    test_code: 'upper_extremity_pattern2_right',
    test_name_zh: '上肢模式2（右）',
    side: 'right',
    group: 'upper_extremity',
    breakout_key: 'upper_extremity_pattern2_breakout'
  },
  {
    test_code: 'multi_segmental_flexion',
    test_name_zh: '多节段屈曲（MSF）',
    side: 'none',
    group: 'multi_segmental',
    breakout_key: 'msf_breakout'
  },
  {
    test_code: 'multi_segmental_extension',
    test_name_zh: '多节段伸展（MSE）',
    side: 'none',
    group: 'multi_segmental',
    breakout_key: 'mse_breakout'
  },
  {
    test_code: 'multi_segmental_rotation_left',
    test_name_zh: '多节段旋转（左）',
    side: 'left',
    group: 'multi_segmental',
    breakout_key: 'msr_breakout'
  },
  {
    test_code: 'multi_segmental_rotation_right',
    test_name_zh: '多节段旋转（右）',
    side: 'right',
    group: 'multi_segmental',
    breakout_key: 'msr_breakout'
  },
  {
    test_code: 'single_leg_stance_left',
    test_name_zh: '单腿站立（左）',
    side: 'left',
    group: 'single_leg_stance',
    breakout_key: 'sls_left'
  },
  {
    test_code: 'single_leg_stance_right',
    test_name_zh: '单腿站立（右）',
    side: 'right',
    group: 'single_leg_stance',
    breakout_key: 'sls_right'
  },
  {
    test_code: 'arms_down_deep_squat',
    test_name_zh: '垂臂下蹲',
    side: 'none',
    group: 'deep_squat',
    breakout_key: 'arms_down_squat_breakout'
  }
]

export const SFMA_GROUP_LABELS = {
  cervical: '颈椎模式',
  upper_extremity: '上肢模式',
  multi_segmental: '多节段模式',
  single_leg_stance: '单腿站立模式',
  deep_squat: '深蹲模式'
} as const

export const SFMA_BREAKOUT_KEYS = [
  'cervical_flexion_breakout',
  'cervical_extension_breakout',
  'cervical_rotation_breakout',
  'upper_extremity_pattern1_breakout',
  'upper_extremity_pattern2_breakout',
  'msf_breakout',
  'mse_breakout',
  'msr_breakout',
  'arms_down_squat_breakout',
  'cervical_pattern',
  'upper_extremity_pattern_left',
  'upper_extremity_pattern_right',
  'msf',
  'mse',
  'msr_left',
  'msr_right',
  'sls_left',
  'sls_right',
  'deep_squat'
] as const

export const SFMA_BREAKOUT_LABELS: Record<string, string> = {
  cervical_flexion_breakout: '颈椎屈曲 Breakout',
  cervical_extension_breakout: '颈椎伸展 Breakout',
  cervical_rotation_breakout: '颈椎旋转 Breakout',
  upper_extremity_pattern1_breakout: '上肢模式1 Breakout（左/右）',
  upper_extremity_pattern2_breakout: '上肢模式2 Breakout（左/右）',
  msf_breakout: '多节段屈曲 Breakout（MSF）',
  mse_breakout: '多节段伸展 Breakout（MSE）',
  msr_breakout: '多节段旋转 Breakout（MSR）',
  arms_down_squat_breakout: '垂臂下蹲分解评估',
  cervical_pattern: '颈椎模式 Breakout',
  upper_extremity_pattern_left: '上肢模式 Breakout（左）',
  upper_extremity_pattern_right: '上肢模式 Breakout（右）',
  msf: 'MSF Breakout',
  mse: 'MSE Breakout（Legacy）',
  msr_left: 'MSR Breakout（左）',
  msr_right: 'MSR Breakout（右）',
  sls_left: 'SLS Breakout（左）',
  sls_right: 'SLS Breakout（右）',
  deep_squat: '垂臂下蹲 Breakout'
}

export const buildDefaultSfmaTopTierRecord = (definition: SfmaTopTierDefinition): SfmaTopTierRecord => ({
  test_code: definition.test_code,
  test_name_zh: definition.test_name_zh,
  side: definition.side,
  classification: '',
  pain_present: false,
  movement_quality_note: '',
  key_observation_note: '',
  rom_key_value: '',
  pain_vas: null,
  needs_breakout_suggestion: false,
  breakout_reason_text: '',
  clinician_note: '',
  review_priority: 'normal',
  caution_text: ''
})

export const buildDefaultSfmaBreakoutRecord = (): SfmaBreakoutRecord => ({
  status: 'not_started',
  findings: '',
  rom_key_values: '',
  pain_present: false,
  pain_vas: null,
  mobility_restriction_signs: '',
  motor_control_signs: '',
  asymmetry_signs: '',
  stop_due_to_pain: false,
  stop_reason: '',
  clinician_note: '',
  method: '',
  scale: '',
  source_id: '',
  date: '',
  sls_time_sec: null
})

const toBooleanLoose = (value: unknown, fallback = false): boolean => {
  if (value === undefined || value === null || value === '') {
    return fallback
  }
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'y', 'on'].includes(normalized)) {
      return true
    }
    if (['false', '0', 'no', 'n', 'off', ''].includes(normalized)) {
      return false
    }
  }
  return Boolean(value)
}

const toNullableNumber = (value: unknown): number | null => {
  if (value === undefined || value === null || value === '') {
    return null
  }
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

export const buildDefaultCervicalFlexionTopTier = (): SfmaCervicalFlexionTopTier => ({
  test_code: 'cervical_flexion',
  test_name_zh: '颈椎屈曲',
  classification: '',
  pain_present: false,
  pain_vas: null,
  top_tier_note: '',
  needs_breakout_suggestion: false,
  breakout_target: '',
  breakout_reason_text: '',
  review_priority: 'low'
})

export const applyCervicalFlexionTopTierRules = (
  input?: Partial<SfmaCervicalFlexionTopTier>
): SfmaCervicalFlexionTopTier => {
  const raw = input || {}
  const base = {
    ...buildDefaultCervicalFlexionTopTier(),
    ...raw
  }
  if (base.classification === 'FN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: false,
      breakout_target: '',
      breakout_reason_text: '',
      review_priority: 'low'
    }
  }
  if (base.classification === 'FP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_flexion_breakout',
      breakout_reason_text: '颈椎屈曲为疼痛性功能模式，建议谨慎进入颈椎屈曲分解评估。',
      review_priority: 'high'
    }
  }
  if (base.classification === 'DN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_flexion_breakout',
      breakout_reason_text: '颈椎屈曲存在功能异常，建议进入颈椎屈曲分解评估。',
      review_priority: 'medium'
    }
  }
  if (base.classification === 'DP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_flexion_breakout',
      breakout_reason_text: '颈椎屈曲存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎屈曲分解评估。',
      review_priority: 'high'
    }
  }
  return base
}

export const buildDefaultCervicalFlexionBreakout = (): SfmaCervicalFlexionBreakout => ({
  breakout_status: 'not_started',
  breakout_note: '',
  active_cervical_flexion_quality: '',
  active_cervical_flexion_pain: false,
  active_cervical_flexion_rom_key: null,
  active_cervical_flexion_end_feel_note: '',
  passive_cervical_flexion_quality: '',
  passive_cervical_flexion_pain: false,
  passive_cervical_flexion_rom_key: null,
  passive_vs_active_difference: '',
  upper_cervical_flexion_observation: '',
  upper_cervical_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  related_region_influence: [],
  breakout_preliminary_direction: [],
  breakout_summary_text: '',
  needs_manual_review: false
})

export const buildDefaultCervicalExtensionTopTier = (): SfmaCervicalExtensionTopTier => ({
  test_code: 'cervical_extension',
  test_name_zh: '颈椎伸展',
  classification: '',
  pain_present: false,
  pain_vas: null,
  top_tier_note: '',
  needs_breakout_suggestion: false,
  breakout_target: '',
  breakout_reason_text: '',
  review_priority: 'low'
})

export const applyCervicalExtensionTopTierRules = (
  input?: Partial<SfmaCervicalExtensionTopTier>
): SfmaCervicalExtensionTopTier => {
  const raw = input || {}
  const base = {
    ...buildDefaultCervicalExtensionTopTier(),
    ...raw
  }
  if (base.classification === 'FN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: false,
      breakout_target: '',
      breakout_reason_text: '',
      review_priority: 'low'
    }
  }
  if (base.classification === 'FP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_extension_breakout',
      breakout_reason_text: '颈椎伸展为疼痛性功能模式，建议谨慎进入颈椎伸展分解评估。',
      review_priority: 'high'
    }
  }
  if (base.classification === 'DN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_extension_breakout',
      breakout_reason_text: '颈椎伸展存在功能异常，建议进入颈椎伸展分解评估。',
      review_priority: 'medium'
    }
  }
  if (base.classification === 'DP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_extension_breakout',
      breakout_reason_text: '颈椎伸展存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎伸展分解评估。',
      review_priority: 'high'
    }
  }
  return base
}

export const buildDefaultCervicalExtensionBreakout = (): SfmaCervicalExtensionBreakout => ({
  breakout_status: 'not_started',
  breakout_note: '',
  active_cervical_extension_quality: '',
  active_cervical_extension_pain: false,
  active_cervical_extension_rom_key: null,
  active_cervical_extension_end_feel_note: '',
  passive_cervical_extension_quality: '',
  passive_cervical_extension_pain: false,
  passive_cervical_extension_rom_key: null,
  passive_vs_active_difference: '',
  upper_cervical_extension_observation: '',
  upper_cervical_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  related_region_influence: [],
  breakout_preliminary_direction: [],
  breakout_summary_text: '',
  needs_manual_review: false
})

const buildDefaultCervicalRotationTopTierSide = (
  side: 'left' | 'right'
): SfmaCervicalRotationTopTierSide => ({
  test_code: side === 'left' ? 'cervical_rotation_left' : 'cervical_rotation_right',
  test_name_zh: side === 'left' ? '颈椎旋转（左）' : '颈椎旋转（右）',
  side,
  classification: '',
  pain_present: false,
  pain_vas: null,
  top_tier_note: '',
  needs_breakout_suggestion: false,
  breakout_target: '',
  breakout_reason_text: '',
  review_priority: 'low'
})

const applyCervicalRotationTopTierSideRules = (
  input: Partial<SfmaCervicalRotationTopTierSide> | undefined,
  side: 'left' | 'right'
): SfmaCervicalRotationTopTierSide => {
  const raw = input || {}
  const base = {
    ...buildDefaultCervicalRotationTopTierSide(side),
    ...raw
  }
  const actionZh = side === 'left' ? '颈椎左旋转' : '颈椎右旋转'
  if (base.classification === 'FN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: false,
      breakout_target: '',
      breakout_reason_text: '',
      review_priority: 'low'
    }
  }
  if (base.classification === 'FP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_rotation_breakout',
      breakout_reason_text: `${actionZh}为疼痛性功能模式，建议谨慎进入颈椎旋转分解评估。`,
      review_priority: 'high'
    }
  }
  if (base.classification === 'DN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_rotation_breakout',
      breakout_reason_text: `${actionZh}存在功能异常，建议进入颈椎旋转分解评估。`,
      review_priority: 'medium'
    }
  }
  if (base.classification === 'DP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'cervical_rotation_breakout',
      breakout_reason_text: `${actionZh}存在功能异常并伴疼痛，建议优先人工复核并谨慎进入颈椎旋转分解评估。`,
      review_priority: 'high'
    }
  }
  return base
}

export const buildDefaultCervicalRotationTopTier = (): SfmaCervicalRotationTopTier => ({
  left: buildDefaultCervicalRotationTopTierSide('left'),
  right: buildDefaultCervicalRotationTopTierSide('right')
})

export const applyCervicalRotationTopTierRules = (
  input?: Partial<SfmaCervicalRotationTopTier>
): SfmaCervicalRotationTopTier => {
  const next = {
    ...buildDefaultCervicalRotationTopTier(),
    ...(input || {})
  }
  return {
    left: applyCervicalRotationTopTierSideRules(next.left, 'left'),
    right: applyCervicalRotationTopTierSideRules(next.right, 'right')
  }
}

const buildDefaultCervicalRotationBreakoutSide = (): SfmaCervicalRotationBreakoutSide => ({
  breakout_status: 'not_started',
  breakout_note: '',
  active_cervical_rotation_quality: '',
  active_cervical_rotation_pain: false,
  active_cervical_rotation_rom_key: null,
  passive_cervical_rotation_quality: '',
  passive_cervical_rotation_pain: false,
  passive_cervical_rotation_rom_key: null,
  passive_vs_active_difference: '',
  upper_cervical_rotation_observation: '',
  upper_cervical_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  related_region_influence: [],
  breakout_preliminary_direction: [],
  breakout_summary_text: '',
  needs_manual_review: false
})

export const buildDefaultCervicalRotationBreakout = (): SfmaCervicalRotationBreakout => ({
  left: buildDefaultCervicalRotationBreakoutSide(),
  right: buildDefaultCervicalRotationBreakoutSide(),
  asymmetry_focus: '',
  overall_note: ''
})

const buildDefaultUpperExtremityPattern1TopTierSide = (
  side: 'left' | 'right'
): SfmaUpperExtremityPattern1TopTierSide => ({
  test_code: side === 'left' ? 'upper_extremity_pattern1_left' : 'upper_extremity_pattern1_right',
  test_name_zh: side === 'left' ? '上肢模式1（左）' : '上肢模式1（右）',
  side,
  classification: '',
  pain_present: false,
  pain_vas: null,
  top_tier_note: '',
  needs_breakout_suggestion: false,
  breakout_target: '',
  breakout_reason_text: '',
  review_priority: 'low'
})

const applyUpperExtremityPattern1TopTierSideRules = (
  input: Partial<SfmaUpperExtremityPattern1TopTierSide> | undefined,
  side: 'left' | 'right'
): SfmaUpperExtremityPattern1TopTierSide => {
  const raw = input || {}
  const base = {
    ...buildDefaultUpperExtremityPattern1TopTierSide(side),
    ...raw
  }
  const actionZh = side === 'left' ? '上肢模式1（左）' : '上肢模式1（右）'
  if (base.classification === 'FN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: false,
      breakout_target: '',
      breakout_reason_text: '',
      review_priority: 'low'
    }
  }
  if (base.classification === 'FP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern1_breakout',
      breakout_reason_text: `${actionZh}为疼痛性功能模式，建议谨慎进入上肢模式1分解评估。`,
      review_priority: 'high'
    }
  }
  if (base.classification === 'DN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern1_breakout',
      breakout_reason_text: `${actionZh}存在功能异常，建议进入上肢模式1分解评估。`,
      review_priority: 'medium'
    }
  }
  if (base.classification === 'DP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern1_breakout',
      breakout_reason_text: `${actionZh}存在功能异常并伴疼痛，建议优先人工复核并谨慎进入上肢模式1分解评估。`,
      review_priority: 'high'
    }
  }
  return base
}

export const buildDefaultUpperExtremityPattern1TopTier = (): SfmaUpperExtremityPattern1TopTier => ({
  left: buildDefaultUpperExtremityPattern1TopTierSide('left'),
  right: buildDefaultUpperExtremityPattern1TopTierSide('right')
})

export const applyUpperExtremityPattern1TopTierRules = (
  input?: Partial<SfmaUpperExtremityPattern1TopTier>
): SfmaUpperExtremityPattern1TopTier => {
  const next = {
    ...buildDefaultUpperExtremityPattern1TopTier(),
    ...(input || {})
  }
  return {
    left: applyUpperExtremityPattern1TopTierSideRules(next.left, 'left'),
    right: applyUpperExtremityPattern1TopTierSideRules(next.right, 'right')
  }
}

const buildDefaultUpperExtremityPattern1BreakoutSide = (): SfmaUpperExtremityPattern1BreakoutSide => ({
  breakout_status: 'not_started',
  breakout_note: '',
  prone_active_result: '',
  prone_active_pain_vas: null,
  prone_active_note: '',
  prone_passive_result: '',
  prone_passive_pain_vas: null,
  prone_passive_note: '',
  supine_interactive_result: '',
  supine_interactive_pain_vas: null,
  supine_interactive_note: '',
  flow_recommendation_text: '',
  local_biomechanics_needed: false,
  stop_and_treat: false,
  active_ue_pattern1_quality: '',
  active_ue_pattern1_pain: false,
  active_ue_pattern1_rom_key: null,
  scapular_control_observation: '',
  thoracic_influence_observation: '',
  glenohumeral_observation: '',
  compensation_patterns: [],
  compensation_other_note: '',
  related_region_influence: [],
  breakout_preliminary_direction: [],
  breakout_summary_text: '',
  needs_manual_review: false
})

export const buildDefaultUpperExtremityPattern1Breakout = (): SfmaUpperExtremityPattern1Breakout => ({
  left: buildDefaultUpperExtremityPattern1BreakoutSide(),
  right: buildDefaultUpperExtremityPattern1BreakoutSide(),
  asymmetry_focus: '',
  overall_note: ''
})

const buildDefaultUpperExtremityPattern2TopTierSide = (
  side: 'left' | 'right'
): SfmaUpperExtremityPattern2TopTierSide => ({
  test_code: side === 'left' ? 'upper_extremity_pattern2_left' : 'upper_extremity_pattern2_right',
  test_name_zh: side === 'left' ? '上肢模式2（左）' : '上肢模式2（右）',
  side,
  classification: '',
  pain_present: false,
  pain_vas: null,
  top_tier_note: '',
  needs_breakout_suggestion: false,
  breakout_target: '',
  breakout_reason_text: '',
  review_priority: 'low'
})

const applyUpperExtremityPattern2TopTierSideRules = (
  input: Partial<SfmaUpperExtremityPattern2TopTierSide> | undefined,
  side: 'left' | 'right'
): SfmaUpperExtremityPattern2TopTierSide => {
  const raw = input || {}
  const base = {
    ...buildDefaultUpperExtremityPattern2TopTierSide(side),
    ...raw
  }
  const actionZh = side === 'left' ? '上肢模式2（左）' : '上肢模式2（右）'
  if (base.classification === 'FN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: false,
      breakout_target: '',
      breakout_reason_text: '',
      review_priority: 'low'
    }
  }
  if (base.classification === 'FP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern2_breakout',
      breakout_reason_text: `${actionZh}为疼痛性功能模式，建议谨慎进入上肢模式2分解评估。`,
      review_priority: 'high'
    }
  }
  if (base.classification === 'DN') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, false),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern2_breakout',
      breakout_reason_text: `${actionZh}存在功能异常，建议进入上肢模式2分解评估。`,
      review_priority: 'medium'
    }
  }
  if (base.classification === 'DP') {
    return {
      ...base,
      pain_present: toBooleanLoose((raw as any).pain_present, true),
      pain_vas: toNullableNumber((raw as any).pain_vas),
      needs_breakout_suggestion: true,
      breakout_target: 'upper_extremity_pattern2_breakout',
      breakout_reason_text: `${actionZh}存在功能异常并伴疼痛，建议优先人工复核并谨慎进入上肢模式2分解评估。`,
      review_priority: 'high'
    }
  }
  return base
}

export const buildDefaultUpperExtremityPattern2TopTier = (): SfmaUpperExtremityPattern2TopTier => ({
  left: buildDefaultUpperExtremityPattern2TopTierSide('left'),
  right: buildDefaultUpperExtremityPattern2TopTierSide('right')
})

export const applyUpperExtremityPattern2TopTierRules = (
  input?: Partial<SfmaUpperExtremityPattern2TopTier>
): SfmaUpperExtremityPattern2TopTier => {
  const next = {
    ...buildDefaultUpperExtremityPattern2TopTier(),
    ...(input || {})
  }
  return {
    left: applyUpperExtremityPattern2TopTierSideRules(next.left, 'left'),
    right: applyUpperExtremityPattern2TopTierSideRules(next.right, 'right')
  }
}

const buildDefaultUpperExtremityPattern2BreakoutSide = (): SfmaUpperExtremityPattern2BreakoutSide => ({
  breakout_status: 'not_started',
  breakout_note: '',
  prone_active_result: '',
  prone_active_pain_vas: null,
  prone_active_note: '',
  prone_passive_result: '',
  prone_passive_pain_vas: null,
  prone_passive_note: '',
  supine_interactive_result: '',
  supine_interactive_pain_vas: null,
  supine_interactive_note: '',
  flow_recommendation_text: '',
  local_biomechanics_needed: false,
  stop_and_treat: false,
  active_ue_pattern2_quality: '',
  active_ue_pattern2_pain: false,
  active_ue_pattern2_rom_key: null,
  scapular_control_observation: '',
  thoracic_influence_observation: '',
  glenohumeral_observation: '',
  compensation_patterns: [],
  compensation_other_note: '',
  related_region_influence: [],
  breakout_preliminary_direction: [],
  breakout_summary_text: '',
  needs_manual_review: false
})

export const buildDefaultUpperExtremityPattern2Breakout = (): SfmaUpperExtremityPattern2Breakout => ({
  left: buildDefaultUpperExtremityPattern2BreakoutSide(),
  right: buildDefaultUpperExtremityPattern2BreakoutSide(),
  asymmetry_focus: '',
  overall_note: ''
})

export const buildDefaultMsfAnalysis = (): SfmaMsfAnalysis => ({
  flow_nodes: {
    single_leg_stance_forward_bend: {
      node_code: 'single_leg_stance_forward_bend',
      node_name_zh: '单腿站立体前屈',
      purpose: '判断体前屈是对称性还是不对称性功能障碍，或作为疼痛诱发策略。',
      instructions: '一侧脚蹬台阶、对侧膝伸直，双手相叠前屈触碰支撑腿同侧足趾，双侧重复比较。',
      clinical_notes: '该节点用于暴露单侧前屈问题；无论结果如何均继续进入长坐位触摸足趾。',
      result_type: '',
      next_step_rules: '所有结果都进入 long_sit_toe_touch',
      stop_if_pain: false,
      result: '',
      left_result: '',
      right_result: '',
      bilateral_summary: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    long_sit_toe_touch: {
      node_code: 'long_sit_toe_touch',
      node_name_zh: '长坐位触摸足趾',
      purpose: '在不负重姿势下鉴别屈曲受限更像后链紧张、髋屈曲受限还是脊柱屈曲障碍。',
      instructions: '长坐位双下肢伸直前屈触趾，记录是否触趾、骶骨角度（80°阈值）与疼痛。',
      clinical_notes:
        'FN且骶骨角正常提示负重髋稳定/协调问题；异常+骶骨角正常偏向负重脊柱稳定或灵活性问题；异常+骶骨角受限偏向髋屈曲或脊柱屈曲受限。',
      result_type: '',
      next_step_rules:
        'fn_and_sacrum_normal→rolling_analysis_result；abnormal_with_sacrum_normal→prone_backward_rocking；abnormal_with_sacrum_limited→active_straight_leg_raise',
      stop_if_pain: false,
      result: '',
      can_touch_toes: false,
      sacral_angle_deg: null,
      sacral_angle_status: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    active_straight_leg_raise: {
      node_code: 'active_straight_leg_raise',
      node_name_zh: '主动直腿抬高（ASLR）',
      purpose: '测试膝伸直状态下髋关节主动屈曲能力。',
      instructions: '仰卧位记录左右抬高角度，正常参考>70°，并记录疼痛。',
      clinical_notes: 'ASLR 与 PSLR 联合用于区分后链TED/髋JMD 与核心稳定或主动屈髋力量问题。',
      result_type: '',
      next_step_rules: 'FN→prone_backward_rocking；DN/FP/DP→passive_straight_leg_raise',
      stop_if_pain: false,
      result: '',
      left_aslr_deg: null,
      right_aslr_deg: null,
      bilateral_summary: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    passive_straight_leg_raise: {
      node_code: 'passive_straight_leg_raise',
      node_name_zh: '被动直腿抬高（PSLR）',
      purpose: '鉴别后链 TED / 髋关节 JMD 与主动控制不足。',
      instructions: '记录左右被动角度，并与 ASLR 比较。',
      clinical_notes: 'PSLR>80°或明显优于ASLR支持核心稳定/主动屈髋控制不足；PSLR<=ASLR更支持后链紧张或髋灵活性不足。',
      result_type: '',
      next_step_rules:
        'fn_gt_80→rolling_analysis_result；fn_gap_gt_10_and_lt_80→supine_double_knees_to_chest；fp_or_dp→停止；dn_pslr_lte_aslr→supine_double_knees_to_chest',
      stop_if_pain: true,
      result: '',
      left_pslr_deg: null,
      right_pslr_deg: null,
      pain_present: false,
      note: '',
      summary_text: ''
    },
    prone_backward_rocking: {
      node_code: 'prone_backward_rocking',
      node_name_zh: '俯卧位向后摆动',
      purpose: '判断不负重姿势下脊柱屈曲能力。',
      instructions: '胸膝位后摆，观察臀部贴近足跟与胸廓触腿情况。',
      clinical_notes: '若膝关节不适可改用仰卧位双膝触胸；FN更支持负重下脊柱稳定/控制问题，DN更支持脊柱JMD/TED。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    supine_double_knees_to_chest: {
      node_code: 'supine_double_knees_to_chest',
      node_name_zh: '仰卧位双膝触胸',
      purpose: '评估不负重姿势下髋关节灵活性。',
      instructions: '双膝抱胸，观察大腿是否可压近胸部及疼痛反应。',
      clinical_notes: 'FN更支持后链TED和/或主动屈髋SMCD，DN更支持髋JMD和/或后链TED。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    rolling_analysis_result: {
      node_code: 'rolling_analysis_result',
      node_name_zh: '滚动解析结果',
      purpose: '区分基础屈曲模式 SMCD 与负重屈曲模式 SMCD。',
      instructions: '可接入滚动模块结果，暂未接入时可手工记录占位结果。',
      clinical_notes: '该节点通常出现在PSLR>80°之后（或已完成旋转动作解析后），用于细化SMCD方向。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    }
  },
  flexion_flow: {
    single_leg_stance_forward_bend: {
      node_code: 'single_leg_stance_forward_bend',
      node_name_zh: '单腿站立体前屈',
      purpose: '判断体前屈是对称性还是不对称性功能障碍，或作为疼痛诱发策略。',
      instructions: '一侧脚蹬台阶、对侧膝伸直，双手相叠前屈触碰支撑腿同侧足趾，双侧重复比较。',
      clinical_notes: '该节点用于暴露单侧前屈问题；无论结果如何均继续进入长坐位触摸足趾。',
      result_type: '',
      next_step_rules: '所有结果都进入 long_sit_toe_touch',
      stop_if_pain: false,
      result: '',
      left_result: '',
      right_result: '',
      bilateral_summary: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    long_sit_toe_touch: {
      node_code: 'long_sit_toe_touch',
      node_name_zh: '长坐位触摸足趾',
      purpose: '在不负重姿势下鉴别屈曲受限更像后链紧张、髋屈曲受限还是脊柱屈曲障碍。',
      instructions: '长坐位双下肢伸直前屈触趾，记录是否触趾、骶骨角度（80°阈值）与疼痛。',
      clinical_notes:
        'FN且骶骨角正常提示负重髋稳定/协调问题；异常+骶骨角正常偏向负重脊柱稳定或灵活性问题；异常+骶骨角受限偏向髋屈曲或脊柱屈曲受限。',
      result_type: '',
      next_step_rules:
        'fn_and_sacrum_normal→rolling_analysis_result；abnormal_with_sacrum_normal→prone_backward_rocking；abnormal_with_sacrum_limited→active_straight_leg_raise',
      stop_if_pain: false,
      result: '',
      can_touch_toes: false,
      sacral_angle_deg: null,
      sacral_angle_status: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    active_straight_leg_raise: {
      node_code: 'active_straight_leg_raise',
      node_name_zh: '主动直腿抬高（ASLR）',
      purpose: '测试膝伸直状态下髋关节主动屈曲能力。',
      instructions: '仰卧位记录左右抬高角度，正常参考>70°，并记录疼痛。',
      clinical_notes: 'ASLR 与 PSLR 联合用于区分后链TED/髋JMD 与核心稳定或主动屈髋力量问题。',
      result_type: '',
      next_step_rules: 'FN→prone_backward_rocking；DN/FP/DP→passive_straight_leg_raise',
      stop_if_pain: false,
      result: '',
      left_aslr_deg: null,
      right_aslr_deg: null,
      bilateral_summary: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    passive_straight_leg_raise: {
      node_code: 'passive_straight_leg_raise',
      node_name_zh: '被动直腿抬高（PSLR）',
      purpose: '鉴别后链 TED / 髋关节 JMD 与主动控制不足。',
      instructions: '记录左右被动角度，并与 ASLR 比较。',
      clinical_notes: 'PSLR>80°或明显优于ASLR支持核心稳定/主动屈髋控制不足；PSLR<=ASLR更支持后链紧张或髋灵活性不足。',
      result_type: '',
      next_step_rules:
        'fn_gt_80→rolling_analysis_result；fn_gap_gt_10_and_lt_80→supine_double_knees_to_chest；fp_or_dp→停止；dn_pslr_lte_aslr→supine_double_knees_to_chest',
      stop_if_pain: true,
      result: '',
      left_pslr_deg: null,
      right_pslr_deg: null,
      pain_present: false,
      note: '',
      summary_text: ''
    },
    prone_backward_rocking: {
      node_code: 'prone_backward_rocking',
      node_name_zh: '俯卧位向后摆动',
      purpose: '判断不负重姿势下脊柱屈曲能力。',
      instructions: '胸膝位后摆，观察臀部贴近足跟与胸廓触腿情况。',
      clinical_notes: '若膝关节不适可改用仰卧位双膝触胸；FN更支持负重下脊柱稳定/控制问题，DN更支持脊柱JMD/TED。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    supine_double_knees_to_chest: {
      node_code: 'supine_double_knees_to_chest',
      node_name_zh: '仰卧位双膝触胸',
      purpose: '评估不负重姿势下髋关节灵活性。',
      instructions: '双膝抱胸，观察大腿是否可压近胸部及疼痛反应。',
      clinical_notes: 'FN更支持后链TED和/或主动屈髋SMCD，DN更支持髋JMD和/或后链TED。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    },
    rolling_analysis_result: {
      node_code: 'rolling_analysis_result',
      node_name_zh: '滚动解析结果',
      purpose: '区分基础屈曲模式 SMCD 与负重屈曲模式 SMCD。',
      instructions: '可接入滚动模块结果，暂未接入时可手工记录占位结果。',
      clinical_notes: '该节点通常出现在PSLR>80°之后（或已完成旋转动作解析后），用于细化SMCD方向。',
      result_type: '',
      next_step_rules: 'FN/DN 输出结果；FP/DP 停止并优先处理疼痛',
      stop_if_pain: true,
      result: '',
      pain_present: false,
      note: '',
      summary_text: ''
    }
  },
  summary: {
    single_vs_bilateral_pattern: '',
    primary_region: '',
    likely_pattern: [],
    hip_flexion_mobility_issue: false,
    posterior_chain_ted_issue: false,
    spinal_flexion_mobility_issue: false,
    loaded_flexion_smcd_issue: false,
    base_flexion_pattern_smcd_issue: false,
    core_or_active_hip_flexion_smcd_issue: false,
    rotation_flow_needed: false,
    stop_and_treat_pain: false,
    manual_review_required: false,
    summary_text: ''
  }
})

const buildDefaultMseFlowNode = (
  node_code: string,
  node_name_zh: string,
  purpose: string,
  instructions: string,
  clinical_notes: string,
  next_step_rules: string,
  stop_if_pain = false
): SfmaMseAnalysisFlowNode => ({
  node_code,
  node_name_zh,
  purpose,
  instructions,
  clinical_notes,
  result_type: '',
  next_step_rules,
  stop_if_pain,
  summary_text: ''
})

export const buildDefaultMseAnalysis = (): SfmaMseAnalysis => ({
  spinal_extension_flow: {
    trunk_extension_without_upper_extremity: buildDefaultMseFlowNode(
      'trunk_extension_without_upper_extremity',
      '无上肢参与的躯体后伸',
      '排除肩关节和肩部肌群参与，观察无上肢参与情况下的脊柱/躯体后伸。',
      '患者站立，双手叉腰，昂首挺胸尽可能向后伸展；限制膝屈曲并观察关键对线。',
      '用于快速区分是否需要进入脊柱伸展分解路径。',
      'FN -> 上半身伸展流程；FP/DP/DN -> 单腿站立躯体后伸。'
    ),
    single_leg_stance_trunk_extension: {
      ...buildDefaultMseFlowNode(
        'single_leg_stance_trunk_extension',
        '单腿站立躯体后伸',
        '区分对称/不对称问题，聚焦单侧负重下躯体后伸能力。',
        '一侧脚放台阶，双手叉腰尽可能后伸，左右侧均记录。',
        '双侧均FN更支持对称性站立位核心稳定/运动控制问题。',
        '双侧FN -> 上半身伸展流程；任一侧FP/DP/DN -> 俯卧撑。'
      ),
      left_result: '',
      right_result: '',
      bilateral_summary: ''
    },
    prone_press_up: {
      ...buildDefaultMseFlowNode(
        'prone_press_up',
        '俯卧撑（俯卧位后伸）',
        '观察非负重姿势下的躯体后伸，区分负重与非负重伸展问题。',
        '患者俯卧，双手置于腋下两侧撑起躯干后伸，必要时可骨盆垫高复测。',
        '若加垫后可完成，仍按FN处理。',
        'FN -> 上半身+下半身伸展流程；FP/DP/DN -> 腰部固定（内旋）主动旋转/伸展。'
      ),
      result: '',
      used_pad: false,
      pain_present: false,
      note: ''
    },
    lumbar_fixed_internal_rotation_active_extension_rotation: {
      ...buildDefaultMseFlowNode(
        'lumbar_fixed_internal_rotation_active_extension_rotation',
        '腰部固定（内旋）主动旋转/伸展',
        '在非负重且肩内旋状态下，观察胸椎伸展与旋转主动能力。',
        '俯卧位向后摆姿势下，左右分别主动向上向后转动肩。',
        '该姿势用于尽量降低腰椎伸展影响，凸显胸椎表现。',
        'FN -> 俯卧位肘支撑旋转/伸展；FP/DP/DN -> 腰部固定（内旋）被动旋转/伸展。'
      ),
      left_result: '',
      right_result: '',
      pain_present: false,
      note: ''
    },
    lumbar_fixed_internal_rotation_passive_extension_rotation: {
      ...buildDefaultMseFlowNode(
        'lumbar_fixed_internal_rotation_passive_extension_rotation',
        '腰部固定（内旋）被动旋转/伸展',
        '观察胸椎在非负重、肩内旋状态下的被动伸展和旋转能力。',
        '同位姿下由治疗师被动向上向后转动肩，比较左右与角度。',
        '用于区分胸椎单侧/双侧问题与疼痛性终止情况。',
        'FP/DP -> 停止并优先疼痛处理；FN/单侧DN/双侧DN -> 上半身+下半身伸展流程。',
        true
      ),
      left_result: '',
      right_result: '',
      pain_present: false,
      note: ''
    },
    prone_elbow_supported_extension_rotation: {
      ...buildDefaultMseFlowNode(
        'prone_elbow_supported_extension_rotation',
        '俯卧位肘支撑旋转/伸展',
        '作为腰椎通过性测试并评估疼痛诱发，观察腰椎相关伸展问题。',
        '患者俯卧位肘支撑，左右分别完成旋转/伸展。',
        '用于判断腰椎单侧或双侧伸展问题倾向。',
        'FP/DP -> 停止并优先疼痛处理；双侧FN/单侧DN/双侧DN -> 上半身+下半身伸展流程。',
        true
      ),
      left_result: '',
      right_result: '',
      pain_present: false,
      note: ''
    }
  },
  lower_body_extension_flow: {
    standing_hip_extension: {
      ...buildDefaultMseFlowNode(
        'standing_hip_extension',
        '站立位髋关节后伸',
        '评估负重位髋伸展与下肢支撑策略。',
        '双侧站立位髋后伸并比较。',
        '双侧伸展>10°通常提示负重控制问题或踝背伸影响。',
        'FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节主动后伸。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    prone_active_hip_extension: {
      ...buildDefaultMseFlowNode(
        'prone_active_hip_extension',
        '俯卧位髋关节主动后伸',
        '在不负重姿势下评估主动髋伸展能力。',
        '俯卧位双侧主动后伸并记录结果。',
        '可区分负重因素与主动伸展控制因素。',
        'FN -> 滚动解析（下半身）；DN/FP/DP -> 俯卧位髋关节被动后伸。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    prone_passive_hip_extension: {
      ...buildDefaultMseFlowNode(
        'prone_passive_hip_extension',
        '俯卧位髋关节被动后伸',
        '比较主动与被动差异，区分控制与灵活性来源。',
        '俯卧位被动后伸并与主动结果比较。',
        '若被动明显优于主动，常提示控制问题为主。',
        'fn_gap_gt_25 -> 滚动解析；FN -> 改良托马斯；DN/FP/DP -> 法伯尔试验。',
        true
      ),
      result: '',
      pain_present: false,
      note: '',
      gap_percent: null
    },
    rolling_analysis_result_lower: {
      ...buildDefaultMseFlowNode(
        'rolling_analysis_result_lower',
        '滚动解析结果（下半身）',
        '区分基础伸展模式问题与负重髋伸展SMCD问题。',
        '接入滚动测试结果或人工记录。',
        'FP/DP 视为疼痛终止分支。',
        'FN/DN -> END；FP/DP -> STOP_PAIN。',
        true
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    faber_test: {
      ...buildDefaultMseFlowNode(
        'faber_test',
        '法伯尔试验',
        '筛查髋/骶髂灵活性与疼痛诱发。',
        '仰卧位执行 FABER 双侧比较并记录结果。',
        'FN/DN 可继续改良托马斯；FP/DP 提示疼痛优先。',
        'FN/DN -> 改良托马斯；FP/DP -> STOP_PAIN。',
        true
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    modified_thomas_test: {
      ...buildDefaultMseFlowNode(
        'modified_thomas_test',
        '改良托马斯试验',
        '区分前链/侧链TED、髋JMD与核心控制问题。',
        '按改良托马斯标准流程记录结果分型。',
        '是下半身伸展流程末端节点。',
        'FN/分型FN/DN -> END；FP/DP -> STOP_PAIN。',
        true
      ),
      result: '',
      pain_present: false,
      note: ''
    }
  },
  upper_body_extension_flow: {
    single_shoulder_extension: {
      ...buildDefaultMseFlowNode(
        'single_shoulder_extension',
        '单肩后伸',
        '识别单侧上半身伸展障碍与疼痛。',
        '单臂上举过头并后伸，左右比较。',
        'FN 可提示复查脊柱/颈椎；异常进入背阔肌拉伸分支。',
        'FN -> 复查建议；DN/FP/DP -> 仰卧位双髋屈曲背阔肌拉伸。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    supine_double_hip_flexion_lat_stretch: {
      ...buildDefaultMseFlowNode(
        'supine_double_hip_flexion_lat_stretch',
        '仰卧位双髋屈曲背阔肌拉伸',
        '评估不负重位背阔肌长度与肩屈曲模式。',
        '仰卧双髋屈曲位，双臂上举接近床面。',
        'FN 常提示负重上肢伸展SMCD；异常进入双髋伸展拉伸。',
        'FN -> END（可提示上肢稳定/控制）；DN/FP/DP -> 仰卧位双髋伸展背阔肌拉伸。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    supine_double_hip_extension_lat_stretch: {
      ...buildDefaultMseFlowNode(
        'supine_double_hip_extension_lat_stretch',
        '仰卧位双髋伸展背阔肌拉伸',
        '区分背阔肌后链问题与胸廓/肩带问题。',
        '仰卧双髋伸展位，记录手臂接近床面的变化。',
        '轻微改善或异常均建议继续腰部固定（外旋）旋转/伸展。',
        '任一结果 -> 腰部固定（外旋）旋转/伸展。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    lumbar_fixed_external_rotation_extension: {
      ...buildDefaultMseFlowNode(
        'lumbar_fixed_external_rotation_extension',
        '腰部固定（外旋）旋转/伸展',
        '降低肩胛稳定要求，观察胸椎伸展旋转表现。',
        '俯卧跪位手外旋头后，左右旋转/伸展。',
        'FN 多提示肩胛/盂肱控制方向；异常进入内旋主动旋转/伸展。',
        'FN -> END（肩胛/盂肱控制方向）；DN/FP/DP -> 腰部固定（内旋）主动旋转/伸展（上半身）。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    lumbar_fixed_internal_rotation_active_extension_rotation_upper: {
      ...buildDefaultMseFlowNode(
        'lumbar_fixed_internal_rotation_active_extension_rotation_upper',
        '腰部固定（内旋）主动旋转/伸展（上半身）',
        '在上半身流程中进一步确认胸椎主动表现。',
        '同内旋主动旋转/伸展方法记录。',
        'FN 可提示肩带JMD/TED方向；异常进入内旋被动。',
        'FN -> END（肩带JMD/TED方向）；DN/FP/DP -> 腰部固定（内旋）被动旋转/伸展（上半身）。'
      ),
      result: '',
      pain_present: false,
      note: ''
    },
    lumbar_fixed_internal_rotation_passive_extension_rotation_upper: {
      ...buildDefaultMseFlowNode(
        'lumbar_fixed_internal_rotation_passive_extension_rotation_upper',
        '腰部固定（内旋）被动旋转/伸展（上半身）',
        '区分胸椎双侧/单侧结构限制与控制问题。',
        '同内旋被动旋转/伸展方法记录。',
        'FP/DP 疼痛终止；FN/单侧DN/双侧DN 可结束本分支。',
        'FP/DP -> STOP_PAIN；FN/单侧DN/双侧DN -> END。',
        true
      ),
      result: '',
      pain_present: false,
      note: ''
    }
  },
  summary: {
    primary_region: '',
    likely_pattern: [],
    thoracic_extension_issue: false,
    lumbar_extension_issue: false,
    weight_bearing_stability_issue: false,
    pain_dominant: false,
    upper_body_extension_flow_needed: false,
    lower_body_extension_flow_needed: false,
    next_flow_targets: [],
    stop_and_treat_pain: false,
    manual_review_required: false,
    summary_text: ''
  }
})

export const buildDefaultMsfBreakout = (): SfmaMsfBreakout => ({
  breakout_status: 'not_started',
  breakout_reason_from_top_tier: '',
  breakout_note: '',
  needs_manual_review: false,
  single_leg_standing_forward_flexion_result: '',
  single_leg_standing_forward_flexion_asymmetry: '',
  single_leg_standing_forward_flexion_note: '',
  long_sit_toe_touch_result: '',
  long_sit_toe_touch_reach_status: '',
  long_sit_sacral_angle_deg: null,
  long_sit_sacral_angle_status: '',
  long_sit_toe_touch_note: '',
  rolling_result: '',
  rolling_note: '',
  aslr_result: '',
  aslr_left_deg: null,
  aslr_right_deg: null,
  aslr_note: '',
  pslr_result: '',
  pslr_left_deg: null,
  pslr_right_deg: null,
  pslr_vs_aslr_interpretation: '',
  pslr_note: '',
  prone_rock_back_result: '',
  prone_rock_back_note: '',
  supine_knees_to_chest_result: '',
  supine_knees_to_chest_note: '',
  flow_next_step: '',
  flow_algorithm_note: '',
  sacral_angle_threshold_ref: 80,
  aslr_threshold_ref: 70,
  pslr_threshold_ref: 80,
  active_flexion_global_quality: '',
  active_flexion_pain: false,
  active_flexion_pain_area: [],
  active_flexion_pain_other_note: '',
  fingertips_to_floor_status: '',
  fingertips_to_floor_distance_cm: null,
  uniform_curve_observation: '',
  movement_quality_note: '',
  hamstring_posterior_chain_tension: '',
  left_right_posterior_chain_asymmetry: '',
  ankle_dorsiflexion_influence: '',
  knee_extension_limitation_influence: '',
  lower_extremity_note: '',
  hip_flexion_contribution: '',
  pelvis_anterior_posterior_control: '',
  left_right_hip_asymmetry: '',
  hip_pelvis_note: '',
  lumbar_flexion_participation: '',
  thoracic_flexion_participation: '',
  segmental_spinal_mobility_observation: '',
  spine_thorax_note: '',
  shoulder_girdle_relaxation: '',
  upper_extremity_hanging_pattern: '',
  shoulder_upper_extremity_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  pain_dominant_pattern: '',
  symptom_irritability: '',
  pain_control_priority_hint: '',
  breakout_preliminary_direction: [],
  primary_restriction_chain: [],
  primary_control_deficit_chain: [],
  left_right_asymmetry_focus: '',
  breakout_summary_text: '',
  clinical_meaning_hint: '',
  training_direction_hint: '',
  reassessment_priority: 'medium',
  pause_or_referral_hint: '',
  msf_analysis: buildDefaultMsfAnalysis()
})

export const buildDefaultMseBreakout = (): SfmaMseBreakout => ({
  breakout_status: 'not_started',
  breakout_reason_from_top_tier: '',
  breakout_note: '',
  needs_manual_review: false,
  active_extension_global_quality: '',
  active_extension_pain: false,
  active_extension_pain_area: [],
  active_extension_pain_other_note: '',
  extension_curve_observation: '',
  center_of_mass_control: '',
  movement_quality_note: '',
  ankle_stability_or_mobility_influence: '',
  knee_extension_or_locking_pattern: '',
  lower_extremity_support_symmetry: '',
  lower_extremity_note: '',
  hip_extension_contribution: '',
  anterior_hip_mobility_limitation_suspected: '',
  pelvis_control_pattern: '',
  lphc_control_during_extension: '',
  left_right_hip_asymmetry: '',
  hip_pelvis_note: '',
  lumbar_extension_participation: '',
  thoracic_extension_participation: '',
  extension_distribution_observation: '',
  posterior_chain_loading_pattern: '',
  spine_thorax_note: '',
  shoulder_flexion_contribution: '',
  shoulder_girdle_compensation: '',
  overhead_pattern_limitation_suspected: '',
  shoulder_upper_extremity_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  pain_dominant_pattern: '',
  symptom_irritability: '',
  pain_control_priority_hint: '',
  breakout_preliminary_direction: [],
  primary_restriction_chain: [],
  primary_control_deficit_chain: [],
  left_right_asymmetry_focus: '',
  breakout_summary_text: '',
  clinical_meaning_hint: '',
  training_direction_hint: '',
  reassessment_priority: 'medium',
  pause_or_referral_hint: '',
  mse_analysis: buildDefaultMseAnalysis()
})

const buildDefaultMsrBreakoutSide = (side: 'left' | 'right'): SfmaMsrBreakoutSide => ({
  breakout_status: 'not_started',
  rotation_side: side,
  breakout_reason_from_top_tier: '',
  breakout_note: '',
  needs_manual_review: false,
  active_rotation_global_quality: '',
  active_rotation_pain: false,
  active_rotation_pain_area: [],
  active_rotation_pain_other_note: '',
  global_rotation_quality_note: '',
  rotation_range_key: null,
  stance_stability_observation: '',
  ankle_foot_support_influence: '',
  lower_extremity_loading_asymmetry: '',
  knee_control_influence: '',
  lower_extremity_note: '',
  hip_rotation_contribution: '',
  pelvis_rotation_control: '',
  hip_pelvis_dissociation: '',
  left_right_hip_rotation_asymmetry: '',
  hip_pelvis_note: '',
  thoracic_rotation_participation: '',
  lumbar_rotation_participation: '',
  rotation_distribution_observation: '',
  thorax_pelvis_coupling_observation: '',
  spine_thorax_note: '',
  shoulder_girdle_participation: '',
  upper_extremity_assist_pattern: '',
  shoulder_thorax_link_observation: '',
  shoulder_upper_extremity_note: '',
  compensation_patterns: [],
  compensation_other_note: '',
  pain_dominant_pattern: '',
  symptom_irritability: '',
  pain_control_priority_hint: '',
  breakout_preliminary_direction: [],
  primary_restriction_chain: [],
  primary_control_deficit_chain: [],
  side_specific_priority: '',
  compare_with_other_side_note: '',
  breakout_summary_text: '',
  clinical_meaning_hint: '',
  training_direction_hint: '',
  reassessment_priority: 'medium',
  pause_or_referral_hint: ''
})

export const buildDefaultMsrBreakout = (): SfmaMsrBreakout => ({
  left: buildDefaultMsrBreakoutSide('left'),
  right: buildDefaultMsrBreakoutSide('right'),
  asymmetry_focus: '',
  overall_note: ''
})

export const buildDefaultArmsDownSquatBreakout = (): SfmaArmsDownSquatBreakout => ({
  breakout_status: 'not_started',
  breakout_reason_from_screening: '',
  breakout_note: '',
  needs_manual_review: false,

  squat_global_quality: '',
  squat_depth_level: '',
  descent_control: '',
  ascent_control: '',
  squat_rhythm_observation: '',
  movement_quality_note: '',

  ankle_dorsiflexion_limitation_suspected: '',
  heel_rise_pattern: '',
  foot_pronation_control: '',
  rearfoot_stability: '',
  foot_tripod_or_pressure_strategy: '',
  foot_ankle_note: '',

  knee_valgus_control: '',
  knee_varus_or_outward_shift: '',
  knee_forward_translation_pattern: '',
  knee_wobble_or_instability: '',
  knee_note: '',

  hip_flexion_contribution: '',
  hip_control_asymmetry: '',
  pelvic_shift_pattern: '',
  pelvic_rotation_suspected: '',
  hip_pelvis_dissociation: '',
  hip_pelvis_note: '',

  excessive_forward_lean: '',
  lumbar_rounding: '',
  lumbar_extension_or_arching: '',
  lphc_control_observation: '',
  trunk_shift_or_rotation: '',
  trunk_lphc_note: '',

  left_right_asymmetry_global: '',
  compensation_patterns: [],
  compensation_other_note: '',
  primary_compensation_chain_note: '',

  pain_present: false,
  pain_vas: null,
  pain_area: [],
  pain_area_other_note: '',
  pain_dominant_pattern: '',
  symptom_irritability: '',
  pain_control_priority_hint: '',

  breakout_preliminary_direction: [],
  primary_restriction_chain: [],
  primary_control_deficit_chain: [],
  risk_precheck_level: '',
  risk_tags: [],

  breakout_summary_text: '',
  clinical_meaning_hint: '',
  training_direction_hint: '',
  reassessment_priority: 'medium',
  pause_or_referral_hint: ''
})

export const buildDefaultSfmaFormData = (): SfmaFormData => {
  const topTier: Record<string, SfmaTopTierRecord> = {}
  SFMA_TOP_TIER_DEFINITIONS.forEach((item) => {
    topTier[item.test_code] = buildDefaultSfmaTopTierRecord(item)
  })

  const breakouts: Record<string, SfmaBreakoutRecord> = {}
  SFMA_BREAKOUT_KEYS.forEach((key) => {
    breakouts[key] = buildDefaultSfmaBreakoutRecord()
  })

  return {
    basic_info: {
      name: '',
      age: null,
      assessment_date: '',
      assessor: '',
      focus: '',
      summary_note: ''
    },
    top_tier: topTier,
    breakout_recommendations: [],
    breakouts,
    book_protocol: buildDefaultSfmaBookProtocolData(),
    cervical_flexion_top_tier: buildDefaultCervicalFlexionTopTier(),
    cervical_flexion_breakout: buildDefaultCervicalFlexionBreakout(),
    cervical_extension_top_tier: buildDefaultCervicalExtensionTopTier(),
    cervical_extension_breakout: buildDefaultCervicalExtensionBreakout(),
    cervical_rotation_top_tier: buildDefaultCervicalRotationTopTier(),
    cervical_rotation_breakout: buildDefaultCervicalRotationBreakout(),
    upper_extremity_pattern1_top_tier: buildDefaultUpperExtremityPattern1TopTier(),
    upper_extremity_pattern1_breakout: buildDefaultUpperExtremityPattern1Breakout(),
    upper_extremity_pattern2_top_tier: buildDefaultUpperExtremityPattern2TopTier(),
    upper_extremity_pattern2_breakout: buildDefaultUpperExtremityPattern2Breakout(),
    msf_breakout: buildDefaultMsfBreakout(),
    mse_breakout: buildDefaultMseBreakout(),
    msr_breakout: buildDefaultMsrBreakout(),
    arms_down_squat_breakout: buildDefaultArmsDownSquatBreakout(),
    analysis_flows: {
      msf_analysis: buildDefaultMsfAnalysis(),
      mse_analysis: buildDefaultMseAnalysis(),
      msr_analysis: {},
      sls_analysis: {},
      cervical_analysis: {},
      upper_extremity_analysis: {},
      arms_down_squat_analysis: {}
    },
    summary: {}
  }
}
