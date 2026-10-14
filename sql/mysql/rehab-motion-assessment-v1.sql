-- 024 | Motion assessment (OpenCap kinematics + deterministic rules + AI draft + therapist sign-off).
-- Additive only: eleven new tenant-scoped tables, menus and role grants for the new pages.
-- Deliberately no DROP, TRUNCATE, UPDATE, DELETE or IF NOT EXISTS: a pre-existing incompatible table
-- must fail loudly and be reviewed rather than skipped. Apply with deploy/internal/migrate.sh after a backup.
-- Raw patient data (videos, .mot/.trc, OpenCap metadata) stays in REHAB_STORAGE_PATH/motion, never in SQL.

CREATE TABLE `rehab_motion_assessment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '动作评估编号',
  `patient_id` BIGINT NOT NULL COMMENT '患者编号',
  `episode_id` BIGINT DEFAULT NULL COMMENT '康复周期编号',
  `assessment_record_id` BIGINT DEFAULT NULL COMMENT '关联的康复评估记录',
  `baseline_id` BIGINT DEFAULT NULL COMMENT '对比基线（初评）动作评估编号',
  `visit_type` VARCHAR(16) NOT NULL DEFAULT 'initial' COMMENT 'initial 初评 / followup 复评',
  `protocol_families` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '评估体系：FMS,NASM_CES,YBT_LQ,TUCK_JUMP,LESS',
  `title` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '标题',
  `capture_time` DATETIME DEFAULT NULL COMMENT '采集时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'WAITING_UPLOAD' COMMENT '流程状态',
  `data_source` VARCHAR(16) NOT NULL DEFAULT 'upload' COMMENT 'upload 本地导出 / opencap 接口拉取',
  `opencap_session_id` VARCHAR(64) DEFAULT NULL COMMENT 'OpenCap 会话 UUID',
  `camera_count` INT DEFAULT NULL COMMENT '相机数量',
  `model_name` VARCHAR(64) DEFAULT NULL COMMENT 'OpenSim 模型',
  `video_consent` BIT(1) NOT NULL DEFAULT b'0' COMMENT '患者同意保存视频',
  `ai_allowed` BIT(1) NOT NULL DEFAULT b'1' COMMENT '允许发送去标识化指标给 AI',
  `sex_group` VARCHAR(8) DEFAULT NULL COMMENT '文献参考分组 male/female',
  `limb_length_left_cm` DECIMAL(6,2) DEFAULT NULL COMMENT '左下肢长 cm（ASIS-内踝）',
  `limb_length_right_cm` DECIMAL(6,2) DEFAULT NULL COMMENT '右下肢长 cm（ASIS-内踝）',
  `tja_variant` VARCHAR(32) NOT NULL DEFAULT 'TJA_MODIFIED_0_2' COMMENT 'Tuck Jump 计分版本',
  `manual_inputs_json` LONGTEXT COMMENT '人工输入：清除测试、YBT、TJA、LESS、NASM 观察',
  `input_revision` INT NOT NULL DEFAULT 1 COMMENT '输入修订号',
  `analyzed_revision` INT DEFAULT NULL COMMENT '最近一次分析使用的输入修订号',
  `engine_version` VARCHAR(48) DEFAULT NULL COMMENT '引擎版本',
  `rule_version` VARCHAR(48) DEFAULT NULL COMMENT '规则版本',
  `result_schema_version` VARCHAR(48) DEFAULT NULL COMMENT '结果结构版本',
  `protocol_versions_json` TEXT COMMENT '协议版本与 SHA-256',
  `result_file_id` BIGINT DEFAULT NULL COMMENT '引擎结果 JSON 文件',
  `result_sha256` VARCHAR(64) DEFAULT NULL COMMENT '引擎结果 SHA-256',
  `session_quality_status` VARCHAR(16) DEFAULT NULL COMMENT '会话质控 pass/warn/fail',
  `analyzed_time` DATETIME DEFAULT NULL COMMENT '分析完成时间',
  `review_status` VARCHAR(16) NOT NULL DEFAULT 'not_started' COMMENT 'not_started/in_review/reviewed',
  `therapist_user_id` BIGINT DEFAULT NULL COMMENT '负责治疗师',
  `signed_user_id` BIGINT DEFAULT NULL COMMENT '签署人',
  `signed_time` DATETIME DEFAULT NULL COMMENT '签署时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_assessment_patient` (`tenant_id`, `patient_id`, `deleted`, `id`),
  KEY `idx_motion_assessment_status` (`tenant_id`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估';

CREATE TABLE `rehab_motion_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `task_type` VARCHAR(16) NOT NULL COMMENT 'PIPELINE/RESCORE/AI/PDF',
  `state` VARCHAR(32) NOT NULL COMMENT '任务状态',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
  `step_message` VARCHAR(255) DEFAULT NULL COMMENT '当前步骤说明',
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '当前步骤已重试次数',
  `max_attempts` INT NOT NULL DEFAULT 5 COMMENT '最大自动重试次数',
  `next_run_time` DATETIME DEFAULT NULL COMMENT '下次执行时间',
  `lock_owner` VARCHAR(64) DEFAULT NULL COMMENT '执行节点',
  `lock_until` DATETIME DEFAULT NULL COMMENT '锁过期时间',
  `idempotency_key` VARCHAR(80) NOT NULL COMMENT '幂等键',
  `cancel_requested` BIT(1) NOT NULL DEFAULT b'0' COMMENT '已请求取消',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '已脱敏错误信息',
  `failed_state` VARCHAR(32) DEFAULT NULL COMMENT '失败时所在步骤',
  `started_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `deadline_time` DATETIME DEFAULT NULL COMMENT '等待外部处理的截止时间',
  `requested_by` BIGINT DEFAULT NULL COMMENT '发起人',
  `input_revision` INT DEFAULT NULL COMMENT '发起时的输入修订号',
  `payload_json` TEXT COMMENT '任务参数（不含患者身份）',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_motion_task_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_motion_task_due` (`state`, `next_run_time`, `deleted`),
  KEY `idx_motion_task_assessment` (`tenant_id`, `assessment_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估异步任务';

CREATE TABLE `rehab_motion_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `file_kind` VARCHAR(16) NOT NULL COMMENT 'mot/trc/osim/metadata/video/result/pdf',
  `trial_name` VARCHAR(128) DEFAULT NULL COMMENT 'OpenCap trial 名',
  `camera_key` VARCHAR(16) DEFAULT NULL COMMENT '相机目录 Cam0..',
  `relative_path` VARCHAR(255) NOT NULL COMMENT 'OpenCap 导出内相对路径',
  `storage_path` VARCHAR(512) NOT NULL COMMENT '存储根目录下相对路径',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '字节数',
  `sha256` VARCHAR(64) NOT NULL COMMENT 'SHA-256',
  `content_type` VARCHAR(64) DEFAULT NULL COMMENT 'MIME',
  `source` VARCHAR(16) NOT NULL DEFAULT 'upload' COMMENT 'upload/opencap/engine/system',
  `upload_user_id` BIGINT DEFAULT NULL COMMENT '上传人',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_file_assessment` (`tenant_id`, `assessment_id`, `deleted`, `file_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估文件';

CREATE TABLE `rehab_motion_trial` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Trial 编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `trial_key` VARCHAR(64) NOT NULL COMMENT '评估内唯一键',
  `test_code` VARCHAR(32) NOT NULL COMMENT '测试代码',
  `side` VARCHAR(16) NOT NULL COMMENT 'bilateral/left/right',
  `condition_code` VARCHAR(32) DEFAULT NULL COMMENT '测试条件',
  `attempt_no` INT NOT NULL DEFAULT 1 COMMENT '尝试序号',
  `opencap_trial_name` VARCHAR(128) DEFAULT NULL COMMENT 'OpenCap trial 名',
  `opencap_trial_id` VARCHAR(64) DEFAULT NULL COMMENT 'OpenCap trial UUID',
  `valid` BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否有效',
  `invalid_reason` VARCHAR(200) DEFAULT NULL COMMENT '无效原因',
  `pain` BIT(1) DEFAULT NULL COMMENT '是否疼痛（NULL=未记录）',
  `manual_criteria_json` TEXT COMMENT '人工判定项',
  `manual_values_json` TEXT COMMENT '人工测量值',
  `qc_status` VARCHAR(16) DEFAULT NULL COMMENT 'pass/warn/fail/not_analyzed',
  `qc_issues_json` TEXT COMMENT '质控问题',
  `phases_json` TEXT COMMENT '阶段与关键帧',
  `phase_detection_json` TEXT COMMENT '阶段识别参数',
  `rep_count` INT DEFAULT NULL COMMENT '重复/跳跃次数',
  `duration_s` DECIMAL(10,3) DEFAULT NULL COMMENT '时长秒',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_trial_assessment` (`tenant_id`, `assessment_id`, `deleted`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估 Trial';

CREATE TABLE `rehab_motion_metric` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '指标编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `trial_id` BIGINT DEFAULT NULL COMMENT 'Trial 编号',
  `metric_key` VARCHAR(191) NOT NULL COMMENT '引擎指标 ID',
  `code` VARCHAR(64) NOT NULL COMMENT '指标代码',
  `label` VARCHAR(128) DEFAULT NULL COMMENT '名称',
  `side` VARCHAR(16) DEFAULT NULL COMMENT '侧别',
  `phase` VARCHAR(32) DEFAULT NULL COMMENT '阶段',
  `rep_no` INT DEFAULT NULL COMMENT '重复序号（NULL=全程）',
  `value_num` DECIMAL(18,6) DEFAULT NULL COMMENT '数值（NULL=不可用，不补 0）',
  `unit` VARCHAR(16) DEFAULT NULL COMMENT '单位',
  `classification` VARCHAR(32) DEFAULT NULL COMMENT 'standard_formula/mot_direct/derived/proxy/manual',
  `validation_status` VARCHAR(48) DEFAULT NULL COMMENT '验证状态',
  `unavailable_reason` VARCHAR(128) DEFAULT NULL COMMENT '不可用原因',
  `source_signals` VARCHAR(500) DEFAULT NULL COMMENT '来源信号',
  `method` VARCHAR(500) DEFAULT NULL COMMENT '计算方法',
  `analysis_revision` INT DEFAULT NULL COMMENT '分析修订号',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_metric_assessment` (`tenant_id`, `assessment_id`, `deleted`, `code`),
  KEY `idx_motion_metric_trial` (`trial_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估指标';

CREATE TABLE `rehab_motion_rule_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则结果编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `trial_id` BIGINT DEFAULT NULL COMMENT 'Trial 编号',
  `rule_key` VARCHAR(191) NOT NULL COMMENT '引擎规则结果 ID',
  `rule_id` VARCHAR(32) NOT NULL COMMENT '规则代码',
  `family` VARCHAR(16) NOT NULL COMMENT '体系',
  `test_code` VARCHAR(32) DEFAULT NULL COMMENT '测试代码',
  `side` VARCHAR(16) DEFAULT NULL COMMENT '侧别',
  `label` VARCHAR(255) DEFAULT NULL COMMENT '名称',
  `outcome` VARCHAR(32) DEFAULT NULL COMMENT '最终结论',
  `auto_outcome` VARCHAR(32) DEFAULT NULL COMMENT '自动结论',
  `manual_outcome` VARCHAR(32) DEFAULT NULL COMMENT '人工结论',
  `decided_by` VARCHAR(32) DEFAULT NULL COMMENT 'auto_official/auto_candidate/manual',
  `auto_policy` VARCHAR(32) DEFAULT NULL COMMENT '自动判定策略',
  `threshold_status` VARCHAR(48) DEFAULT NULL COMMENT '阈值来源状态',
  `criterion` VARCHAR(500) DEFAULT NULL COMMENT '判定标准',
  `source` VARCHAR(255) DEFAULT NULL COMMENT '来源',
  `evidence_json` TEXT COMMENT '证据',
  `note` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `analysis_revision` INT DEFAULT NULL COMMENT '分析修订号',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_rule_assessment` (`tenant_id`, `assessment_id`, `deleted`, `test_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估规则结果';

CREATE TABLE `rehab_motion_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评分编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `family` VARCHAR(16) NOT NULL COMMENT '体系',
  `test_code` VARCHAR(32) NOT NULL COMMENT '测试代码',
  `side` VARCHAR(16) NOT NULL DEFAULT 'overall' COMMENT 'overall/left/right',
  `scoring_scheme` VARCHAR(48) NOT NULL COMMENT '计分方案',
  `system_score` DECIMAL(10,3) DEFAULT NULL COMMENT '系统建议分（NULL=不给出）',
  `provisional_score` DECIMAL(10,3) DEFAULT NULL COMMENT '候选分（含研究性判定，仅供参考）',
  `system_status` VARCHAR(48) DEFAULT NULL COMMENT '系统状态',
  `final_score` DECIMAL(10,3) DEFAULT NULL COMMENT '治疗师最终分',
  `final_status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/confirmed/modified/not_applicable/needs_recheck',
  `change_reason` VARCHAR(500) DEFAULT NULL COMMENT '修改原因',
  `reviewed_user_id` BIGINT DEFAULT NULL COMMENT '审核人',
  `reviewed_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `system_score_changed` BIT(1) NOT NULL DEFAULT b'0' COMMENT '审核后系统分发生变化',
  `detail_json` LONGTEXT COMMENT '计分明细',
  `evidence_json` TEXT COMMENT '证据引用',
  `analysis_revision` INT DEFAULT NULL COMMENT '分析修订号',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_score_assessment` (`tenant_id`, `assessment_id`, `deleted`, `test_code`, `side`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估评分（系统分与最终分分列）';

CREATE TABLE `rehab_motion_manual_edit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `target_type` VARCHAR(32) NOT NULL COMMENT 'trial/score/manual_inputs/ai_draft/assessment',
  `target_id` BIGINT DEFAULT NULL COMMENT '目标编号',
  `target_key` VARCHAR(191) DEFAULT NULL COMMENT '目标键',
  `field_name` VARCHAR(64) NOT NULL COMMENT '字段',
  `old_value` TEXT COMMENT '修改前',
  `new_value` TEXT COMMENT '修改后',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '原因',
  `operator_user_id` BIGINT NOT NULL COMMENT '操作人',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_edit_assessment` (`tenant_id`, `assessment_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估人工修改记录';

CREATE TABLE `rehab_motion_ai_draft` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务编号',
  `status` VARCHAR(16) NOT NULL COMMENT 'generated/fallback/accepted/rejected/stale',
  `provider` VARCHAR(32) DEFAULT NULL COMMENT '提供方',
  `model` VARCHAR(64) DEFAULT NULL COMMENT '模型',
  `provider_request_id` VARCHAR(128) DEFAULT NULL COMMENT '提供方请求 ID',
  `latency_ms` BIGINT DEFAULT NULL COMMENT '耗时',
  `token_usage_json` VARCHAR(500) DEFAULT NULL COMMENT 'Token 用量',
  `prompt_version` VARCHAR(32) NOT NULL COMMENT '提示词版本',
  `input_hash` VARCHAR(64) NOT NULL COMMENT '去标识化输入 SHA-256',
  `content_json` LONGTEXT COMMENT '结构化输出（已校验）',
  `rendered_text` LONGTEXT COMMENT '渲染文本',
  `evidence_refs_json` TEXT COMMENT '证据引用',
  `safety_status` VARCHAR(16) DEFAULT NULL COMMENT 'passed/downgraded/blocked',
  `validation_message` VARCHAR(500) DEFAULT NULL COMMENT '校验说明',
  `fallback_reason` VARCHAR(255) DEFAULT NULL COMMENT '降级原因',
  `reviewed_user_id` BIGINT DEFAULT NULL COMMENT '审核人',
  `reviewed_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `edited_text` LONGTEXT COMMENT '治疗师编辑后文本',
  `analysis_revision` INT DEFAULT NULL COMMENT '分析修订号',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_ai_assessment` (`tenant_id`, `assessment_id`, `deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估 AI 草稿';

CREATE TABLE `rehab_motion_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
  `assessment_id` BIGINT NOT NULL COMMENT '动作评估编号',
  `report_type` VARCHAR(16) NOT NULL COMMENT 'therapist/patient',
  `version_no` INT NOT NULL COMMENT '版本',
  `status` VARCHAR(16) NOT NULL COMMENT 'signed/superseded',
  `content_json` LONGTEXT NOT NULL COMMENT '报告 JSON',
  `content_sha256` VARCHAR(64) NOT NULL COMMENT '报告内容 SHA-256',
  `pdf_file_id` BIGINT DEFAULT NULL COMMENT 'PDF 文件',
  `pdf_status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/ready/failed',
  `signed_user_id` BIGINT NOT NULL COMMENT '签署人',
  `signed_time` DATETIME NOT NULL COMMENT '签署时间',
  `signer_name` VARCHAR(64) DEFAULT NULL COMMENT '签署人姓名',
  `ai_draft_id` BIGINT DEFAULT NULL COMMENT '采用的 AI 草稿',
  `engine_version` VARCHAR(48) DEFAULT NULL COMMENT '引擎版本',
  `rule_version` VARCHAR(48) DEFAULT NULL COMMENT '规则版本',
  `prompt_version` VARCHAR(32) DEFAULT NULL COMMENT '提示词版本',
  `ai_model` VARCHAR(64) DEFAULT NULL COMMENT 'AI 模型',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_motion_report_assessment` (`tenant_id`, `assessment_id`, `deleted`, `report_type`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估签署报告';

CREATE TABLE `rehab_motion_protocol_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '编号',
  `protocol_code` VARCHAR(32) NOT NULL COMMENT '协议代码',
  `protocol_id` VARCHAR(64) DEFAULT NULL COMMENT '协议 ID',
  `protocol_version` VARCHAR(64) NOT NULL COMMENT '协议版本',
  `source` VARCHAR(255) DEFAULT NULL COMMENT '来源',
  `file_sha256` VARCHAR(64) NOT NULL COMMENT '协议文件 SHA-256',
  `source_sha256` VARCHAR(64) DEFAULT NULL COMMENT '来源表格 SHA-256',
  `creator` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次使用时间',
  `updater` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_motion_protocol_version` (`tenant_id`, `protocol_code`, `protocol_version`, `file_sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作评估协议版本登记';

-- 菜单：动作评估（页面 9700，按钮 9710-9717）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`)
VALUES
    (9700, '动作评估', 'rehab:motion:query', 2, 5, 9000, 'motion', 'ep:video-camera', 'rehab/motion/index', 'RehabMotion', 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9710, '动作评估查看', 'rehab:motion:query', 3, 1, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9711, '动作评估创建', 'rehab:motion:create', 3, 2, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9712, '动作数据上传', 'rehab:motion:upload', 3, 3, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9713, '动作分析处理', 'rehab:motion:process', 3, 4, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9714, '动作评估审核', 'rehab:motion:review', 3, 5, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9715, '动作评估签署', 'rehab:motion:sign', 3, 6, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9716, '动作报告导出', 'rehab:motion:export', 3, 7, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0'),
    (9717, '动作评估审计', 'rehab:motion:audit', 3, 8, 9700, '', '', '', NULL, 0, b'1', b'1', b'1', 'script', 'script', b'0')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `permission` = VALUES(`permission`),
    `type` = VALUES(`type`),
    `sort` = VALUES(`sort`),
    `parent_id` = VALUES(`parent_id`),
    `path` = VALUES(`path`),
    `icon` = VALUES(`icon`),
    `component` = VALUES(`component`),
    `component_name` = VALUES(`component_name`);

-- 超级管理员、康复治疗师：全部动作评估权限
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', r.tenant_id
FROM `system_role` r
         JOIN (
    SELECT 9700 AS menu_id UNION ALL SELECT 9710 UNION ALL SELECT 9711 UNION ALL SELECT 9712 UNION ALL
    SELECT 9713 UNION ALL SELECT 9714 UNION ALL SELECT 9715 UNION ALL SELECT 9716 UNION ALL SELECT 9717
) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0'
WHERE r.code IN ('super_admin', 'rehab_therapist')
  AND r.deleted = b'0'
  AND rm.id IS NULL;

-- 康复文员：查看、创建、上传与发起处理；不含审核、签署与导出
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.id, t.menu_id, 'script', 'script', b'0', r.tenant_id
FROM `system_role` r
         JOIN (
    SELECT 9700 AS menu_id UNION ALL SELECT 9710 UNION ALL SELECT 9711 UNION ALL SELECT 9712 UNION ALL SELECT 9713
) t
         LEFT JOIN `system_role_menu` rm
                   ON rm.role_id = r.id AND rm.menu_id = t.menu_id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0'
WHERE r.code = 'rehab_clerk'
  AND r.deleted = b'0'
  AND rm.id IS NULL;
