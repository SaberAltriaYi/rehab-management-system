-- 025 智能动作评估：审计列与框架约定对齐（允许后台任务写入）
-- 背景：024 将 creator/updater 定义为 NOT NULL。框架 DefaultDBFieldHandler 在无登录用户的后台线程
-- （动作评估异步任务 worker）中会把 creator/updater 写为 NULL，导致 INSERT/UPDATE 失败（容器联调发现）。
-- 框架基础表（ruoyi-vue-pro.sql）统一为 `varchar(64) NULL DEFAULT ''`，此处对齐。
-- 仅放宽约束，不删除、不改写任何数据；可重复执行。
SET NAMES utf8mb4;

ALTER TABLE `rehab_motion_assessment`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_task`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_file`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_trial`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_metric`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_rule_result`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_score`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_manual_edit`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_ai_draft`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_report`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';

ALTER TABLE `rehab_motion_protocol_version`
  MODIFY COLUMN `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  MODIFY COLUMN `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者';
