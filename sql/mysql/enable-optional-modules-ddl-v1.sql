-- 可选模块启用脚本（增量 DDL）
-- 目标模块：MP、ERP、AI、Mall（product/promotion/trade/statistics）以及 Mall 依赖的 Pay
-- 说明：只创建缺失表，不删除现有数据，可重复执行
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET FOREIGN_KEY_CHECKS = 1;
