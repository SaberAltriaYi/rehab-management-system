-- 康复模块 Step 7：AI 增强层（规则引擎优先 + AI 辅助 + 人工可复核）
-- 说明：可重复执行，保持幂等

SET NAMES utf8mb4;
START TRANSACTION;

-- ============================================================
-- 1) Step 7 业务表
-- ============================================================
CREATE TABLE IF NOT EXISTS `rehab_ai_job` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `job_no` varchar(64) DEFAULT NULL,
    `patient_id` bigint DEFAULT NULL,
    `episode_id` bigint DEFAULT NULL,
    `assessment_id` bigint DEFAULT NULL,
    `report_id` bigint DEFAULT NULL,
    `plan_id` bigint DEFAULT NULL,
    `progress_id` bigint DEFAULT NULL,
    `alert_id` bigint DEFAULT NULL,
    `trigger_id` bigint DEFAULT NULL,
    `job_type` varchar(64) NOT NULL COMMENT 'interpret_assessment/summarize_report/generate_plan_draft/generate_followup/generate_patient_summary/risk_explain/progress_summary',
    `model_name` varchar(128) DEFAULT NULL,
    `prompt_name` varchar(128) DEFAULT NULL,
    `input_hash` varchar(128) DEFAULT NULL,
    `output_hash` varchar(128) DEFAULT NULL,
    `request_payload_json` longtext,
    `response_payload_json` longtext,
    `status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed/fallback_used/reviewed/rejected/accepted',
    `fallback_used` bit(1) NOT NULL DEFAULT b'0',
    `latency_ms` bigint DEFAULT NULL,
    `token_usage_json` varchar(2000) DEFAULT NULL,
    `triggered_by_user_id` bigint DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rehab_ai_job_no` (`job_no`),
    KEY `idx_rehab_ai_job_patient` (`patient_id`, `create_time`),
    KEY `idx_rehab_ai_job_target` (`episode_id`, `assessment_id`, `report_id`, `plan_id`),
    KEY `idx_rehab_ai_job_status` (`job_type`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 任务';

CREATE TABLE IF NOT EXISTS `rehab_ai_output` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `ai_job_id` bigint NOT NULL,
    `output_type` varchar(64) NOT NULL COMMENT 'therapist_summary/patient_summary/risk_explanation/plan_draft/followup_message/report_section/progress_summary/admin_summary',
    `target_object_type` varchar(32) NOT NULL COMMENT 'assessment/report/plan/progress/notification/trigger/alert/patient',
    `target_object_id` bigint NOT NULL,
    `schema_name` varchar(128) DEFAULT NULL,
    `content_json` longtext,
    `rendered_text` longtext,
    `evidence_refs_json` varchar(4000) DEFAULT NULL,
    `safety_status` varchar(32) NOT NULL DEFAULT 'passed' COMMENT 'passed/downgraded/blocked',
    `review_status` varchar(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/edited/rejected',
    `patient_visible` bit(1) NOT NULL DEFAULT b'0',
    `reviewed_by` bigint DEFAULT NULL,
    `reviewed_time` datetime DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_ai_output_job` (`ai_job_id`, `create_time`),
    KEY `idx_rehab_ai_output_target` (`target_object_type`, `target_object_id`),
    KEY `idx_rehab_ai_output_review` (`review_status`, `safety_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 输出';

CREATE TABLE IF NOT EXISTS `rehab_ai_prompt_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `template_code` varchar(128) NOT NULL,
    `template_name` varchar(255) NOT NULL,
    `module_scope` varchar(64) NOT NULL COMMENT 'assessment/report/plan/followup/patient_summary/risk/progress',
    `role_scope` varchar(32) NOT NULL COMMENT 'therapist/patient/admin',
    `language` varchar(16) DEFAULT 'zh-CN',
    `version_no` int NOT NULL DEFAULT 1,
    `system_prompt` longtext NOT NULL,
    `user_prompt_template` longtext NOT NULL,
    `output_schema_name` varchar(128) NOT NULL,
    `enabled` bit(1) NOT NULL DEFAULT b'1',
    `is_default` bit(1) NOT NULL DEFAULT b'0',
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_ai_tpl_scope` (`template_code`, `module_scope`, `role_scope`, `version_no`),
    KEY `idx_rehab_ai_tpl_default` (`module_scope`, `role_scope`, `is_default`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 提示词模板';

CREATE TABLE IF NOT EXISTS `rehab_ai_config` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `config_scope` varchar(32) NOT NULL COMMENT 'global/organization/therapist',
    `scope_id` bigint DEFAULT NULL,
    `ai_enabled` bit(1) NOT NULL DEFAULT b'1',
    `enable_assessment_interpretation` bit(1) NOT NULL DEFAULT b'1',
    `enable_report_summary` bit(1) NOT NULL DEFAULT b'1',
    `enable_patient_summary` bit(1) NOT NULL DEFAULT b'1',
    `enable_plan_draft` bit(1) NOT NULL DEFAULT b'1',
    `enable_followup_writer` bit(1) NOT NULL DEFAULT b'1',
    `require_human_review_before_visible` bit(1) NOT NULL DEFAULT b'1',
    `visible_to_patient_after_review_only` bit(1) NOT NULL DEFAULT b'1',
    `preferred_model_name` varchar(128) DEFAULT NULL,
    `prompt_style` varchar(32) DEFAULT 'standard',
    `safety_mode` varchar(32) DEFAULT 'strict',
    `note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_ai_config_scope` (`config_scope`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 配置';

CREATE TABLE IF NOT EXISTS `rehab_ai_review_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `ai_output_id` bigint NOT NULL,
    `reviewer_user_id` bigint NOT NULL,
    `review_action` varchar(32) NOT NULL COMMENT 'accept/edit/reject/regenerate',
    `before_text` longtext,
    `after_text` longtext,
    `review_note` varchar(1000) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_ai_review_output` (`ai_output_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 审核日志';

CREATE TABLE IF NOT EXISTS `rehab_ai_suggestion_bundle` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `patient_id` bigint NOT NULL,
    `episode_id` bigint DEFAULT NULL,
    `source_assessment_id` bigint DEFAULT NULL,
    `source_progress_id` bigint DEFAULT NULL,
    `bundle_type` varchar(64) NOT NULL COMMENT 'integrated_summary/plan_bundle/followup_bundle',
    `summary_json` longtext,
    `status` varchar(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/reviewed/adopted/discarded',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_rehab_ai_bundle_patient` (`patient_id`, `bundle_type`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='康复 AI 建议包';

-- ============================================================
-- 2) 菜单与权限（Step 7）
-- ============================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES
    (9600, 'AI 内容中心', 'rehab:ai:center:view', 2, 12, 9000, 'ai-center', 'ep:cpu', 'rehab/ai-center/index', 'RehabAiCenter', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9601, 'AI 配置中心', 'rehab:ai:config:view', 2, 13, 9000, 'ai-config', 'ep:setting', 'rehab/ai-config/index', 'RehabAiConfig', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9602, '提示词模板中心', 'rehab:ai:prompt-template:view', 2, 14, 9000, 'ai-prompt-template', 'ep:files', 'rehab/ai-prompt-template/index', 'RehabAiPromptTemplate', 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type` = VALUES(`type`),
    `sort` = VALUES(`sort`),
    `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`),
    `icon` = VALUES(`icon`),
    `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`),
    `status` = VALUES(`status`),
    `visible` = VALUES(`visible`),
    `keep_alive` = VALUES(`keep_alive`),
    `always_show` = VALUES(`always_show`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES
    (9610, 'AI任务查看', 'rehab:ai:job:view', 3, 1, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9611, 'AI输出查看', 'rehab:ai:output:view', 3, 2, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9612, 'AI生成', 'rehab:ai:generate', 3, 3, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9613, 'AI审核', 'rehab:ai:review', 3, 4, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9614, 'AI采纳', 'rehab:ai:accept', 3, 5, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9615, 'AI编辑', 'rehab:ai:edit', 3, 6, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9616, 'AI驳回', 'rehab:ai:reject', 3, 7, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9617, 'AI重生成', 'rehab:ai:regenerate', 3, 8, 9600, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9621, 'AI配置更新', 'rehab:ai:config:update', 3, 1, 9601, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9631, '模板创建', 'rehab:ai:prompt-template:create', 3, 1, 9602, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9632, '模板更新', 'rehab:ai:prompt-template:update', 3, 2, 9602, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9633, '模板启停', 'rehab:ai:prompt-template:enable', 3, 3, 9602, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type` = VALUES(`type`),
    `sort` = VALUES(`sort`),
    `parent_id` = VALUES(`parent_id`),
    `updater` = 'script',
    `deleted` = b'0';

-- ============================================================
-- 3) 角色菜单绑定（Step 7）
-- ============================================================
-- 超级管理员：全量 AI 权限
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 1, t.menu_id, 'script', 'script', b'0', 1
FROM (
         SELECT 9600 AS menu_id UNION ALL
         SELECT 9601 UNION ALL
         SELECT 9602 UNION ALL
         SELECT 9610 UNION ALL
         SELECT 9611 UNION ALL
         SELECT 9612 UNION ALL
         SELECT 9613 UNION ALL
         SELECT 9614 UNION ALL
         SELECT 9615 UNION ALL
         SELECT 9616 UNION ALL
         SELECT 9617 UNION ALL
         SELECT 9621 UNION ALL
         SELECT 9631 UNION ALL
         SELECT 9632 UNION ALL
         SELECT 9633
     ) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = 1 AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE rm.id IS NULL;

-- 康复治疗师：AI 内容与审核，不含全局配置修改
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', 1
FROM `system_role` r
         JOIN (
    SELECT 9600 AS menu_id UNION ALL
    SELECT 9610 UNION ALL
    SELECT 9611 UNION ALL
    SELECT 9612 UNION ALL
    SELECT 9613 UNION ALL
    SELECT 9614 UNION ALL
    SELECT 9615 UNION ALL
    SELECT 9616 UNION ALL
    SELECT 9617 UNION ALL
    SELECT 9602
) t
              LEFT JOIN `system_role_menu` rm
                        ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = 1 AND rm.deleted = b'0'
WHERE r.code = 'rehab_therapist'
  AND r.tenant_id = 1
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- ============================================================
-- 4) 初始化 AI 配置 / 模板 / 示例数据
-- ============================================================
INSERT INTO `rehab_ai_config`
(`id`, `config_scope`, `scope_id`, `ai_enabled`, `enable_assessment_interpretation`, `enable_report_summary`, `enable_patient_summary`,
 `enable_plan_draft`, `enable_followup_writer`, `require_human_review_before_visible`, `visible_to_patient_after_review_only`,
 `preferred_model_name`, `prompt_style`, `safety_mode`, `note`, `creator`, `updater`, `deleted`)
VALUES
    (99001, 'global', 0, b'1', b'1', b'1', b'1', b'1', b'1', b'1', b'1', 'gpt-4.1-mini', 'standard', 'strict',
     'Step7 默认全局 AI 配置', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `ai_enabled` = VALUES(`ai_enabled`),
    `enable_assessment_interpretation` = VALUES(`enable_assessment_interpretation`),
    `enable_report_summary` = VALUES(`enable_report_summary`),
    `enable_patient_summary` = VALUES(`enable_patient_summary`),
    `enable_plan_draft` = VALUES(`enable_plan_draft`),
    `enable_followup_writer` = VALUES(`enable_followup_writer`),
    `require_human_review_before_visible` = VALUES(`require_human_review_before_visible`),
    `visible_to_patient_after_review_only` = VALUES(`visible_to_patient_after_review_only`),
    `preferred_model_name` = VALUES(`preferred_model_name`),
    `prompt_style` = VALUES(`prompt_style`),
    `safety_mode` = VALUES(`safety_mode`),
    `note` = VALUES(`note`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_ai_prompt_template`
(`id`, `template_code`, `template_name`, `module_scope`, `role_scope`, `language`, `version_no`, `system_prompt`, `user_prompt_template`,
 `output_schema_name`, `enabled`, `is_default`, `note`, `creator`, `updater`, `deleted`)
VALUES
    (99101, 'assessment_interpretation', '评估解读模板 v1', 'assessment', 'therapist', 'zh-CN', 1,
     '你是运动康复评估辅助系统的一部分，不是医生。严格输出 JSON。',
     '请根据输入生成治疗师摘要，输入：{{input_json}}', 'TherapistSummarySchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99102, 'report_summary', '报告摘要模板 v1', 'report', 'therapist', 'zh-CN', 1,
     '你是运动康复评估辅助系统的一部分，不是医生。严格输出 JSON。',
     '请根据 report_json 生成专业摘要，输入：{{input_json}}', 'TherapistSummarySchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99103, 'report_summary', '报告摘要模板 v1-admin', 'report', 'admin', 'zh-CN', 1,
     '你是康复机构管理分析助手，输出必须可审查。', '请生成管理者摘要，输入：{{input_json}}',
     'AdminSummarySchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99104, 'patient_summary', '患者摘要模板 v1', 'patient_summary', 'patient', 'zh-CN', 1,
     '你是患者沟通助手，不得制造恐慌，不得诊断。', '请生成患者可读摘要，输入：{{input_json}}',
     'PatientSummarySchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99105, 'risk_explanation', '风险解释模板 v1', 'risk', 'therapist', 'zh-CN', 1,
     '你是风险解释助手，不得输出确诊。', '请生成风险解释，输入：{{input_json}}',
     'RiskExplanationSchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99106, 'plan_draft_generation', '计划草案模板 v1', 'plan', 'therapist', 'zh-CN', 1,
     '你是训练计划草案助手，输出仅为草案。', '请生成计划草案和任务草案，输入：{{input_json}}',
     'PlanDraftSchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99107, 'followup_message_generation', '随访模板 v1', 'followup', 'therapist', 'zh-CN', 1,
     '你是随访文案助手，输出须保守。', '请生成随访建议，输入：{{input_json}}',
     'FollowupMessageSchema', b'1', b'1', '默认模板', 'script', 'script', b'0'),
    (99108, 'assessment_interpretation', '评估解读模板 v2', 'assessment', 'therapist', 'zh-CN', 2,
     '你是运动康复评估辅助系统的一部分，不是医生。严格输出 JSON，强调 evidence_refs。', '请生成 v2 风格解读，输入：{{input_json}}',
     'TherapistSummarySchema', b'1', b'0', '演示多版本', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `template_name` = VALUES(`template_name`),
    `module_scope` = VALUES(`module_scope`),
    `role_scope` = VALUES(`role_scope`),
    `version_no` = VALUES(`version_no`),
    `system_prompt` = VALUES(`system_prompt`),
    `user_prompt_template` = VALUES(`user_prompt_template`),
    `output_schema_name` = VALUES(`output_schema_name`),
    `enabled` = VALUES(`enabled`),
    `is_default` = VALUES(`is_default`),
    `note` = VALUES(`note`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_ai_job`
(`id`, `job_no`, `patient_id`, `episode_id`, `assessment_id`, `report_id`, `plan_id`, `progress_id`, `alert_id`, `trigger_id`,
 `job_type`, `model_name`, `prompt_name`, `input_hash`, `output_hash`, `request_payload_json`, `response_payload_json`,
 `status`, `fallback_used`, `latency_ms`, `token_usage_json`, `triggered_by_user_id`, `creator`, `updater`, `deleted`)
VALUES
    (99201, 'AIJ202603100001', 10001, 13001, 20001, NULL, NULL, NULL, NULL, NULL, 'assessment_interpretation',
     'gpt-4.1-mini', 'assessment_interpretation:v1', 'hash-input-1', 'hash-output-1',
     '{"assessment_id":20001}', '{"therapist_summary":{"title":"下肢功能评估摘要"}}', 'success', b'0', 820, '{"total_tokens":986}', 100, 'script', 'script', b'0'),
    (99202, 'AIJ202603100002', 10001, 13001, NULL, 30001, NULL, NULL, NULL, NULL, 'report_summary',
     'gpt-4.1-mini', 'report_summary:v1', 'hash-input-2', 'hash-output-2',
     '{"report_id":30001}', '{"patient_summary":{"headline":"报告摘要已更新"}}', 'success', b'0', 930, '{"total_tokens":1120}', 100, 'script', 'script', b'0'),
    (99203, 'AIJ202603100003', 10001, 13001, NULL, NULL, NULL, NULL, 98103, NULL, 'risk_explanation',
     'gpt-4.1-mini', 'risk_explanation:v1', 'hash-input-3', 'hash-output-3',
     '{"alert_id":98103}', '{"risk_explanation":{"overall_risk_level":"high"}}', 'rejected', b'0', 710, '{"total_tokens":745}', 100, 'script', 'script', b'0'),
    (99204, 'AIJ202603100004', 10001, 13001, 20001, 30001, 40001, 70001, NULL, NULL, 'plan_draft_generation',
     'gpt-4.1-mini', 'plan_draft_generation:v1', 'hash-input-4', 'hash-output-4',
     '{"assessment_id":20001,"report_id":30001,"progress_id":70001}', '{"plan_draft":{"plan_name":"四周恢复草案"}}', 'success', b'0', 1280, '{"total_tokens":1368}', 100, 'script', 'script', b'0'),
    (99205, 'AIJ202603100005', 10001, 13001, NULL, NULL, 40001, 70001, NULL, 80001, 'followup_message_generation',
     'gpt-4.1-mini', 'followup_message_generation:v1', 'hash-input-5', 'hash-output-5',
     '{"progress_id":70001,"trigger_id":80001}', '{"followup_message":{"patient_message":"请按计划继续训练"}}', 'accepted', b'0', 640, '{"total_tokens":682}', 100, 'script', 'script', b'0'),
    (99206, 'AIJ202603100006', 10001, 13001, NULL, NULL, 40001, 70001, NULL, NULL, 'progress_summary',
     'gpt-4.1-mini', 'progress_summary:v1', 'hash-input-6', 'hash-output-6',
     '{"progress_id":70001}', '{"progress_summary":{"summary":"降级模板"}}', 'fallback_used', b'1', 510, '{"total_tokens":0}', 100, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `status` = VALUES(`status`),
    `fallback_used` = VALUES(`fallback_used`),
    `response_payload_json` = VALUES(`response_payload_json`),
    `token_usage_json` = VALUES(`token_usage_json`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_ai_output`
(`id`, `ai_job_id`, `output_type`, `target_object_type`, `target_object_id`, `schema_name`, `content_json`, `rendered_text`,
 `evidence_refs_json`, `safety_status`, `review_status`, `patient_visible`, `reviewed_by`, `reviewed_time`,
 `creator`, `updater`, `deleted`)
VALUES
    (99301, 99201, 'therapist_summary', 'assessment', 20001, 'TherapistSummarySchema',
     '{"title":"下肢功能评估摘要","executive_summary":"提示疑似存在下肢控制不足模式","top_issues":["动态膝内扣","左右差偏大"],"priority_actions":["优先改善单腿控制"],"risk_notes":["再代偿风险中等"],"evidence_refs":["assessment:20001","ybt:module"],"caveats":["需结合人工复核"]}',
     '提示疑似存在下肢控制不足模式，结合当前证据，优先考虑改善单腿控制。',
     '["assessment:20001","ybt:module"]', 'passed', 'accepted', b'0', 100, NOW(), 'script', 'script', b'0'),
    (99302, 99202, 'therapist_summary', 'report', 30001, 'TherapistSummarySchema',
     '{"title":"复测专业摘要","executive_summary":"提示动作质量有改善但疼痛波动仍需关注","top_issues":["疼痛波动","依从性不稳定"],"priority_actions":["维持核心训练","强化随访"],"risk_notes":["中风险"],"evidence_refs":["report:30001","progress:70001"],"caveats":["仅为功能学推测"]}',
     '提示动作质量有改善但疼痛波动仍需关注。',
     '["report:30001","progress:70001"]', 'passed', 'pending', b'0', NULL, NULL, 'script', 'script', b'0'),
    (99303, 99202, 'admin_summary', 'report', 30001, 'AdminSummarySchema',
     '{"title":"机构管理摘要","executive_summary":"该患者处于执行中阶段，需重点跟踪依从性","management_focus":["随访频率","复评节点"],"risk_overview":["低依从性提醒仍 active"],"resource_hint":["建议优先安排复评"],"evidence_refs":["alert:98102"],"caveats":["需结合人工复核"]}',
     '该患者处于执行中阶段，需重点跟踪依从性。',
     '["alert:98102"]', 'passed', 'pending', b'0', NULL, NULL, 'script', 'script', b'0'),
    (99304, 99202, 'patient_summary', 'report', 30001, 'PatientSummarySchema',
     '{"headline":"你的训练方向正在逐步明确","top_3_findings":["动作稳定性仍需加强","近期疼痛有波动","需要保持训练连续性"],"top_3_goals":["降低疼痛波动","提高动作质量","按时完成复评"],"current_focus":"先把本周核心任务完成","what_to_avoid":["疼痛明显加重时硬撑训练"],"when_to_recheck":"建议 1-2 周内复评","supportive_message":"坚持按计划执行，我们会根据反馈持续调整。"}',
     '你的训练方向正在逐步明确，建议先把本周核心任务完成。',
     '[]', 'passed', 'accepted', b'1', 100, NOW(), 'script', 'script', b'0'),
    (99305, 99203, 'risk_explanation', 'alert', 98103, 'RiskExplanationSchema',
     '{"overall_risk_level":"high","explanation":"疼痛反馈较前升高，提示风险增加","likely_contributors":["疼痛 flare","执行质量波动"],"suggested_next_step":["建议尽快人工复核"],"patient_visible_text":"近期建议降低训练负荷并联系治疗师","evidence_refs":["alert:98103"],"caveats":["需结合人工复核"]}',
     '疼痛反馈较前升高，提示风险增加。',
     '["alert:98103"]', 'passed', 'rejected', b'0', 100, NOW(), 'script', 'script', b'0'),
    (99306, 99204, 'plan_draft', 'plan', 40001, 'PlanDraftSchema',
     '{"plan_name":"四周恢复草案","plan_type":"rehab","short_term_goals":["改善下肢控制"],"mid_term_goals":["提升负荷耐受"],"long_term_goals":["稳定回归训练"],"precautions":["疼痛>3/10降阶"],"suggested_tasks":[{"task_name":"单腿控制训练","module_type":"control","target_deficit":"动态稳定不足","suggested_dosage":"3组x8次","suggested_frequency":"每周4次","pain_limit_rule":"疼痛>3/10停止","progression_rule":"动作稳定后加量","regression_rule":"疼痛上升则退阶","home_or_clinic":"both","rationale":"提升下肢控制"}],"progression_strategy":"以质量优先","regression_strategy":"疼痛升高即退阶","review_cycle_days":14,"evidence_refs":["assessment:20001","progress:70001"],"caveats":["草案需人工审核"]}',
     '计划草案已生成，需人工审核后方可生效。',
     '["assessment:20001","progress:70001"]', 'passed', 'pending', b'0', NULL, NULL, 'script', 'script', b'0'),
    (99307, 99205, 'followup_message', 'trigger', 80001, 'FollowupMessageSchema',
     '{"patient_message":"请按本周计划继续训练，疼痛升高请及时反馈","therapist_internal_note":"建议 7 天内随访并核对依从性","recommended_followup_interval_days":7,"recommended_reassessment_needed":true,"trigger_level":"medium","evidence_refs":["trigger:80001","progress:70001"]}',
     '请按本周计划继续训练，疼痛升高请及时反馈。',
     '["trigger:80001","progress:70001"]', 'passed', 'accepted', b'1', 100, NOW(), 'script', 'script', b'0'),
    (99308, 99206, 'progress_summary', 'progress', 70001, 'ProgressSummarySchema',
     '{"progress_status":"insufficient_data","summary":"证据不足，采用降级模板","positive_changes":["已有连续打卡"],"concerning_changes":["疼痛趋势证据不足"],"adherence_comment":"仅为功能学推测","next_action":["补充复测数据"],"evidence_refs":["fallback:template"]}',
     '证据不足，采用降级模板；仅为功能学推测；需结合人工复核。',
     '["fallback:template"]', 'downgraded', 'pending', b'0', NULL, NULL, 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `review_status` = VALUES(`review_status`),
    `patient_visible` = VALUES(`patient_visible`),
    `rendered_text` = VALUES(`rendered_text`),
    `evidence_refs_json` = VALUES(`evidence_refs_json`),
    `safety_status` = VALUES(`safety_status`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_ai_review_log`
(`id`, `ai_output_id`, `reviewer_user_id`, `review_action`, `before_text`, `after_text`, `review_note`, `creator`, `updater`, `deleted`)
VALUES
    (99401, 99304, 100, 'edit', '你的训练方向正在逐步明确，建议先把本周核心任务完成。', '你这阶段最重要的是把核心动作做扎实，有不适及时告诉治疗师。', '编辑后通过', 'script', 'script', b'0'),
    (99402, 99304, 100, 'accept', '你这阶段最重要的是把核心动作做扎实，有不适及时告诉治疗师。', '你这阶段最重要的是把核心动作做扎实，有不适及时告诉治疗师。', '采纳并患者可见', 'script', 'script', b'0'),
    (99403, 99305, 100, 'reject', '疼痛反馈较前升高，提示风险增加。', NULL, '需要补充人工评估后再发布', 'script', 'script', b'0'),
    (99404, 99308, 100, 'regenerate', '证据不足，采用降级模板；仅为功能学推测；需结合人工复核。', NULL, '等待补充数据后重生成', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `review_action` = VALUES(`review_action`),
    `review_note` = VALUES(`review_note`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_ai_suggestion_bundle`
(`id`, `patient_id`, `episode_id`, `source_assessment_id`, `source_progress_id`, `bundle_type`, `summary_json`, `status`, `creator`, `updater`, `deleted`)
VALUES
    (99501, 10001, 13001, 20001, 70001, 'plan_bundle',
     '{"plan_name":"四周恢复草案","review_tip":"需治疗师审核后生效"}', 'draft', 'script', 'script', b'0'),
    (99502, 10001, 13001, 20001, 70001, 'followup_bundle',
     '{"patient_message":"请按计划继续训练","interval_days":7}', 'reviewed', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `summary_json` = VALUES(`summary_json`),
    `status` = VALUES(`status`),
    `updater` = 'script',
    `deleted` = b'0';

INSERT INTO `rehab_audit_log`
(`id`, `module_type`, `module_id`, `operation_type`, `operator_user_id`, `operator_role`, `before_data_json`, `after_data_json`, `ip`, `user_agent`, `result_status`, `remark`, `creator`, `updater`, `deleted`)
VALUES
    (99601, 'ai_job', 99201, 'ai_generate', 100, 'therapist', NULL, '{"status":"success"}', '127.0.0.1', 'script', 'success', '示例 AI 生成', 'script', 'script', b'0'),
    (99602, 'ai_job', 99206, 'ai_fallback', 100, 'therapist', NULL, '{"status":"fallback_used"}', '127.0.0.1', 'script', 'success', '示例 AI 降级', 'script', 'script', b'0'),
    (99603, 'ai_output', 99304, 'ai_accept', 100, 'therapist', '{"review_status":"pending"}', '{"review_status":"accepted"}', '127.0.0.1', 'script', 'success', '示例采纳', 'script', 'script', b'0'),
    (99604, 'ai_output', 99305, 'ai_reject', 100, 'therapist', '{"review_status":"pending"}', '{"review_status":"rejected"}', '127.0.0.1', 'script', 'success', '示例驳回', 'script', 'script', b'0'),
    (99605, 'ai_config', 99001, 'ai_config_update', 1, 'admin', '{"prompt_style":"standard"}', '{"prompt_style":"detailed"}', '127.0.0.1', 'script', 'success', '示例配置修改', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `operation_type` = VALUES(`operation_type`),
    `remark` = VALUES(`remark`),
    `updater` = 'script',
    `deleted` = b'0';

-- 同步生成 1 条患者端可见通知（演示 AI 发布）
INSERT INTO `rehab_notification`
(`id`, `notification_no`, `target_type`, `target_user_id`, `patient_id`, `episode_id`, `related_type`, `related_id`, `notification_type`,
 `title`, `content`, `severity`, `delivery_channel`, `read_status`, `send_status`, `visible_from`, `expire_time`, `action_url`, `action_text`,
 `creator`, `updater`, `deleted`)
VALUES
    (99701, 'NTF202603109701', 'patient', NULL, 10001, 13001, 'system', 99304, 'system_notice',
     'AI 患者摘要已更新', '你的康复阶段摘要已更新，可在首页查看。', 'info', 'app_patient', 'unread', 'sent',
     NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), '/pages/index/index', '查看摘要', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `content` = VALUES(`content`),
    `updater` = 'script',
    `deleted` = b'0';

COMMIT;
