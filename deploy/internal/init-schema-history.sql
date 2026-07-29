-- 仅由 MySQL 在全新数据卷完成全部业务 SQL 后执行。
-- 固定值必须与 migrations.manifest 一致；preflight/migrate.sh verify-files 会逐项核对。
CREATE TABLE IF NOT EXISTS `internal_schema_history` (
  `version` VARCHAR(32) NOT NULL COMMENT '迁移版本',
  `checksum` CHAR(64) NOT NULL COMMENT '脚本 SHA-256',
  `script_path` VARCHAR(255) NOT NULL COMMENT '项目内相对路径',
  `description` VARCHAR(255) NOT NULL COMMENT '迁移说明',
  `installed_on` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间',
  `installed_by` VARCHAR(64) NOT NULL DEFAULT 'docker-init' COMMENT '执行主体',
  `baseline` BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否基线登记',
  `execution_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时毫秒',
  PRIMARY KEY (`version`),
  UNIQUE KEY `uk_internal_schema_history_path` (`script_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内部版数据库迁移账本';

INSERT INTO `internal_schema_history`
  (`version`, `checksum`, `script_path`, `description`, `installed_by`, `baseline`, `execution_ms`)
VALUES
  ('001', '8f009c903b056cb22ba805c4ff428a4d542b4c022d4e64e0eabdc5923fe02fc9', 'sql/mysql/rehab-init-v1.sql', '康复基础模型', 'docker-init', b'1', 0),
  ('002', '2f6b3a028166b3aafb704e67563a4ca1bb6f5672950a4ee0e221b658986cfb70', 'sql/mysql/rehab-step2-v1.sql', '评估与计划扩展', 'docker-init', b'1', 0),
  ('003', '7bffdc350bbe1298f24a8cb276e58af57190d641cfdccc06a4b2ec597ea710bf', 'sql/mysql/rehab-step3-v1.sql', '随访与进度扩展', 'docker-init', b'1', 0),
  ('004', 'f05a251291e336d4c3973ce1a149c888689edf9e3be43c8f9d7d5395b82a1a17', 'sql/mysql/rehab-step4-v1.sql', '风险与报告扩展', 'docker-init', b'1', 0),
  ('005', '09066cf87be7a4ff4af9f1ce3b0714860df7d0178e37986aaec176a3dc76f031', 'sql/mysql/rehab-step5-v1.sql', '康复业务增强', 'docker-init', b'1', 0),
  ('006', 'a7a9aa4192b264f70fb910379f3c982f8781543590627574c5824bdbb29dc621', 'sql/mysql/rehab-step6-v1.sql', '运营数据扩展', 'docker-init', b'1', 0),
  ('007', 'c499344e861b1df54d4514a3b2e3fd6eb26cea49ead7b4b065cb49c99e5ac76b', 'sql/mysql/rehab-step7-v1.sql', '通知与审计扩展', 'docker-init', b'1', 0),
  ('008', 'dd5f8e3a0bf20cba116454456347b4722a55072c8764a7f2d5fe81c6af806f76', 'sql/mysql/rehab-step8-assessment-type-v2.sql', '评估类型与 SFMA v2', 'docker-init', b'1', 0),
  ('009', 'f8b4a457371c8208e25bb4dbc979d84781449a1daef94f6e40ec0de7663b392e', 'sql/mysql/rehab-crm-member-bootstrap-v1.sql', 'CRM 会员基础桥接', 'docker-init', b'1', 0),
  ('010', 'e464817073262e4a525b4aa9856260cdacc48bf6ef8f34f453ea8468ad5a0dfa', 'sql/mysql/rehab-crm-member-bridge-v1.sql', 'CRM 患者关系桥接', 'docker-init', b'1', 0),
  ('011', '59cd837f39982a23cb15af10fd30d4796d381a6ed91c2f5554206c9c59552024', 'sql/mysql/rehab-step9-tenant-v1.sql', '全表租户隔离', 'docker-init', b'1', 0),
  ('012', '2337f683166af40465617c7b745405fea0f66761f97fdfb879da955374e07f80', 'sql/mysql/rehab-disable-optional-module-menus-v1.sql', '关闭可选模块菜单', 'docker-init', b'1', 0),
  ('013', '5910663b082cd52853f9648ae48bb5f4caf6dbeb25d76500b2b4738571fa82df', 'deploy/internal/disable-ai.sql', '关闭 AI 配置与菜单', 'docker-init', b'1', 0),
  ('014', 'ef03f638400d8cd73c728060ac1439bac14c0c096b543c7dea4babca7415f406', 'deploy/internal/internal-hardening.sql', '内部部署安全收口', 'docker-init', b'1', 0),
  ('015', 'a616b7d80b6261fed0ecc354f1cf271f1b829f74ef9d884f0f56a54d3460ab64', 'deploy/internal/clean-demo-rehab-data.sql', '清理演示康复数据', 'docker-init', b'1', 0),
  ('016', '08d3d8de2480523a95c1a1062f71494b695f92085db52b8112ef29ea26ddc302', 'sql/mysql/rehab-step10-integrity-v1.sql', '核心关系完整性约束', 'docker-init', b'1', 0),
  ('017', '60cba3977703f1af12f6ee692da4730c5610c3ed4d7c7c69aa05767402b08480', 'sql/mysql/rehab-step11-auth-hardening-v1.sql', '停用并轮换演示 OAuth2 客户端', 'docker-init', b'1', 0),
  ('018', 'e85a527672f5ac2ae82768ea4644bd9a37f33bdf63cd46eff968ecdfab26f948', 'sql/mysql/rehab-step12-internal-login-client-v1.sql', '保留无外部授权能力的内部登录客户端', 'docker-init', b'1', 0)
ON DUPLICATE KEY UPDATE
  `checksum` = VALUES(`checksum`),
  `script_path` = VALUES(`script_path`),
  `description` = VALUES(`description`);
