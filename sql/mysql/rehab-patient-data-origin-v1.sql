-- 035. Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
-- Additive only. Existing rows stay business data; NEVER infer origin by name.
-- No role grants, patient updates/deletes, or clinical-state changes.
SET @origin_column_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='rehab_patient' AND column_name='data_origin');
SET @origin_ddl = IF(@origin_column_exists=0,
    'ALTER TABLE rehab_patient ADD COLUMN data_origin VARCHAR(32) NOT NULL DEFAULT ''REAL'' COMMENT ''REAL/SYNTHETIC/SYNTHETIC_ARCHIVED''',
    'SELECT 1');
PREPARE origin_statement FROM @origin_ddl;
EXECUTE origin_statement;
DEALLOCATE PREPARE origin_statement;

DROP PROCEDURE IF EXISTS verify_rehab_patient_origin;
DELIMITER $$
CREATE PROCEDURE verify_rehab_patient_origin()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='rehab_patient' AND column_name='data_origin'
        AND data_type='varchar' AND character_maximum_length=32 AND is_nullable='NO' AND column_default='REAL') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible data_origin column; audit the upgrade copy before continuing';
    END IF;
END$$
DELIMITER ;
CALL verify_rehab_patient_origin();
DROP PROCEDURE IF EXISTS verify_rehab_patient_origin;

SET @origin_index_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='rehab_patient' AND index_name='idx_rehab_patient_origin');
SET @origin_ddl = IF(@origin_index_exists=0,
    'ALTER TABLE rehab_patient ADD INDEX idx_rehab_patient_origin (tenant_id,data_origin,deleted)', 'SELECT 1');
PREPARE origin_statement FROM @origin_ddl;
EXECUTE origin_statement;
DEALLOCATE PREPARE origin_statement;

SET @origin_check_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name='rehab_patient' AND constraint_name='chk_rehab_patient_origin');
SET @origin_ddl = IF(@origin_check_exists=0,
    'ALTER TABLE rehab_patient ADD CONSTRAINT chk_rehab_patient_origin CHECK (data_origin IN (''REAL'',''SYNTHETIC'',''SYNTHETIC_ARCHIVED''))', 'SELECT 1');
PREPARE origin_statement FROM @origin_ddl;
EXECUTE origin_statement;
DEALLOCATE PREPARE origin_statement;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 9895,'模拟资料治理','rehab:patient:data-governance',3,98,9002,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='rehab:patient:data-governance');
