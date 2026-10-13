-- SaaS synthetic opt-in ONLY. Not an automatic migration.
-- Back up menu status first. Requires the explicit business backend profile,
-- matching internal frontend, schema/API checks and @saas_business_opt_in=1.
-- Roles, tenant packages, credentials, historical migrations are NOT changed.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE saas_business_pages (id BIGINT PRIMARY KEY);
INSERT INTO saas_business_pages
SELECT id FROM system_menu WHERE deleted=b'0' AND type=2
AND @saas_business_opt_in=1 AND component IN (
  'bpm/category/index',
  'bpm/task/todo/index',
  'bpm/task/done/index',
  'report/goview/index',
  'rehab/motion/index',
  'erp/finance/account/index',
  'erp/finance/payment/index',
  'erp/finance/receipt/index',
  'erp/product/category/index',
  'erp/product/product/index',
  'erp/product/unit/index',
  'erp/purchase/in/index',
  'erp/purchase/order/index',
  'erp/purchase/return/index',
  'erp/purchase/supplier/index',
  'erp/sale/customer/index',
  'erp/sale/order/index',
  'erp/sale/out/index',
  'erp/sale/return/index',
  'erp/stock/check/index',
  'erp/stock/in/index',
  'erp/stock/move/index',
  'erp/stock/out/index',
  'erp/stock/record/index',
  'erp/stock/stock/index',
  'erp/stock/warehouse/index',
  'erp/home/index.vue',
  'crm/backlog/index',
  'crm/business/index',
  'crm/business/status/index',
  'crm/clue/index',
  'crm/contact/index',
  'crm/customer/index',
  'crm/customer/limitConfig/index',
  'crm/customer/pool/index',
  'crm/customer/poolConfig/index',
  'crm/product/category/index',
  'crm/product/index',
  'crm/statistics/funnel/index',
  'crm/statistics/performance/index',
  'crm/statistics/portrait/index',
  'crm/statistics/rank/index',
  'crm/statistics/customer/index.vue',
  'member/config/index',
  'member/group/index',
  'member/level/index',
  'member/point/record/index',
  'member/signin/config/index',
  'member/signin/record/index',
  'member/tag/index',
  'member/user/index'
);
CREATE TEMPORARY TABLE saas_business_ids (id BIGINT PRIMARY KEY);
INSERT INTO saas_business_ids
WITH RECURSIVE ancestors AS (
  SELECT m.id, m.parent_id FROM system_menu m JOIN saas_business_pages p ON p.id=m.id
  UNION DISTINCT
  SELECT m.id,m.parent_id FROM system_menu m JOIN ancestors a ON a.parent_id=m.id
  WHERE m.deleted=b'0'
) SELECT DISTINCT id FROM ancestors;
INSERT IGNORE INTO saas_business_ids
SELECT b.id FROM system_menu b JOIN saas_business_pages p ON b.parent_id=p.id
WHERE b.deleted=b'0' AND b.type=3
AND b.permission NOT IN ('report:go-view-data:get-by-sql','report:go-view-data:get-by-http');
START TRANSACTION;
UPDATE system_menu m JOIN saas_business_ids s ON s.id=m.id
SET m.status=0,m.updater='saas-business-opt-in',m.update_time=NOW();
COMMIT;
SELECT COUNT(*) AS selected_pages FROM saas_business_pages;
DROP TEMPORARY TABLE saas_business_ids;
DROP TEMPORARY TABLE saas_business_pages;
