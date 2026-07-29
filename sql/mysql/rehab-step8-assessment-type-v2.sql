-- 康复模块 Step 8：评估类型 v2 迁移（动态表单框架）
-- 说明：
-- 1) 新写入仅允许 v2 类型
-- 2) 历史旧类型统一迁移为 comprehensive_assessment
-- 3) 可重复执行

SET NAMES utf8mb4;
START TRANSACTION;

-- 历史类型迁移为 comprehensive_assessment
UPDATE `rehab_assessment_record`
SET `assessment_type` = 'comprehensive_assessment'
WHERE `deleted` = b'0'
  AND `assessment_type` IN ('initial', 'followup', 'discharge', 'special_retest', 'return_to_sport');

-- 空值兜底
UPDATE `rehab_assessment_record`
SET `assessment_type` = 'comprehensive_assessment'
WHERE `deleted` = b'0'
  AND (`assessment_type` IS NULL OR TRIM(`assessment_type`) = '');

-- 可选：保证综合评估存在对应占位模块（避免旧数据模块为空）
INSERT INTO `rehab_assessment_module_data`
(`assessment_id`, `module_type`, `module_status`, `data_json`, `source_type`, `version`, `note`, `creator`, `updater`, `deleted`)
SELECT r.id,
       'comprehensive',
       'completed',
       '{}',
       'manual',
       'v1',
       'Step8 迁移自动补齐占位模块',
       'script',
       'script',
       b'0'
FROM `rehab_assessment_record` r
LEFT JOIN `rehab_assessment_module_data` m
       ON m.assessment_id = r.id
      AND m.deleted = b'0'
WHERE r.deleted = b'0'
  AND r.assessment_type = 'comprehensive_assessment'
  AND m.id IS NULL;

COMMIT;
