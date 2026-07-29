-- 康复模块 Step 9：补齐多租户字段（v1）
-- 说明：
-- 1. 芋道框架默认开启多租户拦截器，会为业务表 SQL 自动追加 tenant_id 条件。
-- 2. 本脚本为所有 rehab_ 业务表幂等补齐 tenant_id 与查询索引。
-- 3. 兼容全新部署和已存在的康复数据库。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `rehab_add_tenant_columns`;

DELIMITER $$

CREATE PROCEDURE `rehab_add_tenant_columns`()
BEGIN
    DECLARE finished int DEFAULT 0;
    DECLARE rehab_table_name varchar(128);
    DECLARE rehab_tables CURSOR FOR
        SELECT t.TABLE_NAME
        FROM information_schema.TABLES t
        LEFT JOIN information_schema.COLUMNS c
               ON c.TABLE_SCHEMA = t.TABLE_SCHEMA
              AND c.TABLE_NAME = t.TABLE_NAME
              AND c.COLUMN_NAME = 'tenant_id'
        WHERE t.TABLE_SCHEMA = DATABASE()
          AND t.TABLE_TYPE = 'BASE TABLE'
          AND t.TABLE_NAME LIKE 'rehab\_%'
          AND c.COLUMN_NAME IS NULL
        ORDER BY t.TABLE_NAME;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN rehab_tables;
    add_column_loop: LOOP
        FETCH rehab_tables INTO rehab_table_name;
        IF finished = 1 THEN
            LEAVE add_column_loop;
        END IF;

        SET @rehab_tenant_sql = CONCAT(
            'ALTER TABLE `', rehab_table_name,
            '` ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT ''租户编号'',',
            ' ADD INDEX `idx_rehab_tenant_id` (`tenant_id`)'
        );
        PREPARE rehab_tenant_stmt FROM @rehab_tenant_sql;
        EXECUTE rehab_tenant_stmt;
        DEALLOCATE PREPARE rehab_tenant_stmt;
    END LOOP;
    CLOSE rehab_tables;
END$$

DELIMITER ;

CALL `rehab_add_tenant_columns`();
DROP PROCEDURE IF EXISTS `rehab_add_tenant_columns`;

