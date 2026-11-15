-- HEP API authorization metadata. Does not grant permissions to any role.
-- Review and assign individual permissions through Core role administration.
-- Only creates menu-button resources below the existing rehab root (9000).
INSERT INTO system_menu
 (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT s.id,s.name,s.permission,3,s.sort,9000,'','','','',0,b'1',b'0',b'0','hep-bridge','hep-bridge',b'0'
FROM (
 SELECT 98801 AS id,'HEP管理身份' AS name,'rehab:hep:integration:admin' AS permission,1 AS sort UNION ALL
 SELECT 98802,'HEP治疗师处方操作','rehab:hep:prescription:create',2 UNION ALL
 SELECT 98803,'HEP患者反馈人工审核','rehab:hep:feedback:review',3 UNION ALL
 SELECT 98804,'HEP查看动作','rehab:hep:exercise:view',4 UNION ALL
 SELECT 98805,'HEP创建动作','rehab:hep:exercise:create',5 UNION ALL
 SELECT 98806,'HEP审核动作','rehab:hep:exercise:review',6 UNION ALL
 SELECT 98807,'HEP查看模板','rehab:hep:template:view',7 UNION ALL
 SELECT 98808,'HEP创建模板','rehab:hep:template:create',8 UNION ALL
 SELECT 98809,'HEP签发处方','rehab:hep:prescription:issue',9
) s
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu m WHERE m.permission=s.permission AND m.deleted=b'0'
);
