-- Integrated HEP menu under Rehab. Explicit staff permissions only.
-- Back up DB before applying. The migration is idempotent.
START TRANSACTION;
INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,
  status,visible,keep_alive,always_show,creator,updater,deleted)
VALUES (98900,'家庭康复 HEP','',2,14,9000,'hep','ep:files','rehab/hep-console/index',
 'RehabHepWorkspace',0,b'1',b'1',b'0','hep-native','hep-native',b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),
  parent_id=VALUES(parent_id),updater='hep-native',deleted=b'0';

-- Therapist role: assigned patients, issuing prescriptions, exercises and reviews.
-- Tenant administrators have separate privileged HEP management capabilities.
INSERT INTO system_role_menu(role_id,menu_id,tenant_id,creator,updater,deleted)
SELECT r.id,m.id,r.tenant_id,'hep-native','hep-native',b'0'
FROM system_role r JOIN system_menu m
ON (m.id=98900 OR
  (r.code='rehab_therapist' AND m.permission IN
    ('rehab:hep:prescription:create','rehab:hep:prescription:issue',
     'rehab:hep:exercise:view','rehab:hep:exercise:create',
     'rehab:hep:template:view','rehab:hep:template:create',
     'rehab:hep:feedback:review'))
  OR (r.code IN ('tenant_admin','super_admin') AND
      m.permission LIKE 'rehab:hep:%'))
LEFT JOIN system_role_menu existing
 ON existing.role_id=r.id AND existing.menu_id=m.id AND existing.tenant_id=r.tenant_id
 AND existing.deleted=b'0'
WHERE r.deleted=b'0' AND r.status=0 AND
 r.code IN ('rehab_therapist','tenant_admin','super_admin') AND
 m.deleted=b'0' AND existing.id IS NULL;
COMMIT;
