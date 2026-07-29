#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$SCRIPT_DIR/.env"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
}

[ -f "$ENV_FILE" ] || fail "缺少 deploy/internal/.env"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps --status running mysql \
  | grep -q mysql || fail "MySQL 容器未运行"

run_sql() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE" --batch --raw --skip-column-names'
}

metrics=$(run_sql <<'SQL'
SELECT 'rehab_tables', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'rehab\_%'
UNION ALL
SELECT 'tables_without_tenant_id', COUNT(*)
FROM information_schema.tables t
WHERE t.table_schema = DATABASE()
  AND t.table_name LIKE 'rehab\_%'
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns c
    WHERE c.table_schema = t.table_schema
      AND c.table_name = t.table_name
      AND c.column_name = 'tenant_id'
  )
UNION ALL
SELECT 'tables_without_index', COUNT(*)
FROM information_schema.tables t
WHERE t.table_schema = DATABASE()
  AND t.table_name LIKE 'rehab\_%'
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics s
    WHERE s.table_schema = t.table_schema AND s.table_name = t.table_name
  )
UNION ALL
SELECT 'non_innodb_tables', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name LIKE 'rehab\_%'
  AND engine <> 'InnoDB'
UNION ALL
SELECT 'non_utf8mb4_tables', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name LIKE 'rehab\_%'
  AND table_collation NOT LIKE 'utf8mb4%'
UNION ALL
SELECT 'foreign_keys', COUNT(*)
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name LIKE 'rehab\_%'
  AND constraint_type = 'FOREIGN KEY'
UNION ALL
SELECT 'ai_config_rows_enabled', COUNT(*)
FROM rehab_ai_config
WHERE deleted = 0
  AND (
    ai_enabled = 1
    OR enable_assessment_interpretation = 1
    OR enable_report_summary = 1
    OR enable_patient_summary = 1
    OR enable_plan_draft = 1
    OR enable_followup_writer = 1
  )
UNION ALL
SELECT 'ai_menus_enabled', COUNT(*)
FROM system_menu
WHERE deleted = 0
  AND status = 0
  AND (
    name LIKE '%AI%'
    OR path = '/ai'
    OR path = 'ai-center'
    OR path = 'ai-config'
    OR path = 'ai-prompt-template'
    OR component LIKE 'ai/%'
    OR component LIKE 'rehab/ai-%'
  )
UNION ALL
SELECT 'oauth_default_clients_enabled', COUNT(*)
FROM system_oauth2_client
WHERE deleted = b'0' AND status = 0 AND client_id = 'default'
UNION ALL
SELECT 'oauth_nondefault_clients_enabled', COUNT(*)
FROM system_oauth2_client
WHERE deleted = b'0' AND status = 0 AND client_id <> 'default'
UNION ALL
SELECT 'oauth_clients_with_weak_secret', COUNT(*)
FROM system_oauth2_client
WHERE deleted = b'0'
  AND (LENGTH(secret) < 32 OR secret IN ('admin123', 'test', 'test2'))
UNION ALL
SELECT 'duplicate_business_keys', COALESCE(SUM(duplicate_count), 0)
FROM (
  SELECT COUNT(*) - 1 AS duplicate_count
  FROM rehab_patient WHERE deleted = 0 GROUP BY tenant_id, patient_no HAVING COUNT(*) > 1
  UNION ALL
  SELECT COUNT(*) - 1
  FROM rehab_episode WHERE deleted = 0 GROUP BY tenant_id, episode_no HAVING COUNT(*) > 1
  UNION ALL
  SELECT COUNT(*) - 1
  FROM rehab_assessment_record WHERE deleted = 0 GROUP BY tenant_id, assessment_no HAVING COUNT(*) > 1
  UNION ALL
  SELECT COUNT(*) - 1
  FROM rehab_care_plan WHERE deleted = 0 GROUP BY tenant_id, plan_no HAVING COUNT(*) > 1
  UNION ALL
  SELECT COUNT(*) - 1
  FROM rehab_exercise_task WHERE deleted = 0 GROUP BY tenant_id, task_no HAVING COUNT(*) > 1
  UNION ALL
  SELECT COUNT(*) - 1
  FROM rehab_report WHERE deleted = 0 GROUP BY tenant_id, report_no HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'orphan_or_cross_tenant_rows', COALESCE(SUM(orphan_count), 0)
FROM (
  SELECT COUNT(*) AS orphan_count
  FROM rehab_episode c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  WHERE c.deleted = 0 AND (p.id IS NULL OR p.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_assessment_record c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_assessment_module_data c
  LEFT JOIN rehab_assessment_record p ON p.id = c.assessment_id AND p.deleted = 0
  WHERE c.deleted = 0 AND (p.id IS NULL OR p.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_assessment_attachment c
  LEFT JOIN rehab_assessment_record p ON p.id = c.assessment_id AND p.deleted = 0
  WHERE c.deleted = 0 AND (p.id IS NULL OR p.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_care_plan c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_exercise_task c
  LEFT JOIN rehab_care_plan p ON p.id = c.plan_id AND p.deleted = 0
  LEFT JOIN rehab_patient r ON r.id = c.patient_id AND r.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR r.id IS NULL OR r.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_daily_checkin c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  LEFT JOIN rehab_care_plan r ON r.id = c.plan_id AND r.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id
      OR r.id IS NULL OR r.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_task_execution c
  LEFT JOIN rehab_daily_checkin p ON p.id = c.checkin_id AND p.deleted = 0
  LEFT JOIN rehab_exercise_task t ON t.id = c.task_id AND t.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR t.id IS NULL OR t.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_report c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  LEFT JOIN rehab_assessment_record a ON a.id = c.assessment_id AND a.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id
      OR (c.assessment_id IS NOT NULL AND (a.id IS NULL OR a.tenant_id <> c.tenant_id)))
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_progress_record c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  LEFT JOIN rehab_care_plan r ON r.id = c.plan_id AND r.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR e.id IS NULL OR e.tenant_id <> c.tenant_id
      OR r.id IS NULL OR r.tenant_id <> c.tenant_id)
  UNION ALL
  SELECT COUNT(*)
  FROM rehab_followup_note c
  LEFT JOIN rehab_patient p ON p.id = c.patient_id AND p.deleted = 0
  LEFT JOIN rehab_episode e ON e.id = c.episode_id AND e.deleted = 0
  WHERE c.deleted = 0
    AND (p.id IS NULL OR p.tenant_id <> c.tenant_id
      OR (c.episode_id IS NOT NULL AND (e.id IS NULL OR e.tenant_id <> c.tenant_id)))
) orphans
UNION ALL
SELECT 'active_patients', COUNT(*) FROM rehab_patient WHERE deleted = 0;
SQL
)

printf '%s\n' "$metrics"

metric_value() {
  printf '%s\n' "$metrics" | awk -F '	' -v metric="$1" '$1 == metric {print $2; exit}'
}

rehab_tables=$(metric_value rehab_tables)
[ "${rehab_tables:-0}" -ge 34 ] || fail "康复表数量不足，期望至少 34，实际 ${rehab_tables:-0}"
[ "$(metric_value tables_without_tenant_id)" -eq 0 ] || fail "存在缺少 tenant_id 的康复表"
[ "$(metric_value tables_without_index)" -eq 0 ] || fail "存在完全没有索引的康复表"
[ "$(metric_value non_innodb_tables)" -eq 0 ] || fail "存在非 InnoDB 康复表"
[ "$(metric_value non_utf8mb4_tables)" -eq 0 ] || fail "存在非 utf8mb4 康复表"
[ "$(metric_value ai_config_rows_enabled)" -eq 0 ] || fail "数据库中仍有启用的康复 AI 配置"
[ "$(metric_value ai_menus_enabled)" -eq 0 ] || fail "数据库中仍有启用的 AI 菜单"
[ "$(metric_value oauth_default_clients_enabled)" -eq 1 ] || fail "内部后台登录客户端状态异常"
[ "$(metric_value oauth_nondefault_clients_enabled)" -eq 0 ] || fail "内部版仍有启用的非登录 OAuth2 客户端"
[ "$(metric_value oauth_clients_with_weak_secret)" -eq 0 ] || fail "OAuth2 客户端仍有弱演示 secret"
[ "$(metric_value duplicate_business_keys)" -eq 0 ] || fail "关键业务编号存在重复"
[ "$(metric_value orphan_or_cross_tenant_rows)" -eq 0 ] || fail "存在孤儿数据或跨租户关联"
[ "$(metric_value foreign_keys)" -ge 41 ] || fail "核心关系外键不足，期望至少 41"

pass "数据库结构、租户隔离、业务编号与核心关联检查通过"
echo "INFO: foreign_keys=$(metric_value foreign_keys)"
echo "INFO: active_patients=$(metric_value active_patients)"
"$SCRIPT_DIR/migrate.sh" status
