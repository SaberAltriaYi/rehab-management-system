-- Additive inbox, no overwrite of clinical records or signed reports.
CREATE TABLE IF NOT EXISTS rehab_motion_workspace_inbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL, assessment_id BIGINT NOT NULL, patient_id BIGINT NOT NULL,
 source_id VARCHAR(32) NOT NULL, source_revision INT NOT NULL, core_revision INT NOT NULL,
 sha256 CHAR(64) NOT NULL, snapshot_json MEDIUMTEXT NOT NULL,
 received_by BIGINT NOT NULL, received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 review_status VARCHAR(24) NOT NULL DEFAULT 'pending', review_note VARCHAR(1000),
 reviewed_by BIGINT, reviewed_at DATETIME,
 UNIQUE KEY uk_workspace_source (tenant_id,assessment_id,source_id,source_revision),
 KEY idx_workspace_patient (tenant_id,patient_id,assessment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
VALUES (9718,'进入运动学工作区','rehab:motion:workspace',3,9,9700,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0'),
       (9719,'接收运动学结果','rehab:motion:sync',3,10,9700,'','','',NULL,0,b'1',b'1',b'1','script','script',b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission);
-- New rights are deliberately NOT granted to every role; an administrator
-- must explicitly assign workspace / sync to authorized clinical members.
