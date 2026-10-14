# SaaS V2 实机执行检查点（2026-10-12）

这是阶段报告，不是 SaaS Beta 交付或验收通过声明。

## 已完成
- P1：复核已有 v1-enterprise-beta 注解标签，解引用为 dcfe0e06041fc1328e54ef4b35026cae81dfdf5f。
- 已有 Enterprise 归档位于 /Users/saber/my-project/rehab/releases/v1-enterprise-beta-dcfe0e0604，保留 jar、DMG、runtime、migration、source 和历史证据，新增 ARCHIVE-README.zh-CN.md。
- P2：复用已有 feature/saas-platform-v2 工作树，起始提交 21e555b9e3；确认继承 Enterprise 基线。没有切换或修改主工作树与 L7 分支。
- P3 部分：修复迁移 027 的 MySQL 8.4 不支持的 ADD COLUMN IF NOT EXISTS，并同步迁移清单 SHA-256。
- 增加无网络、tmpfs 数据目录、随机密码的 MySQL 合成测试脚本；不接受业务数据库连接参数。
- 增加 SaasTenantAccessService 基础服务：当前租户上下文检查、生命周期与订阅有效期检查、事务内配额预留。原 Enterprise 授权代码未删除或停用。

## 已验证
- Enterprise check-runtime.mjs 完整性检查通过，jar、DMG 和源码包摘要已记录在归档说明。
- MySQL 8.4.10 上原迁移第 44 行复现 ERROR 1064；修复后首次执行及重复执行通过，两个合成租户保留。
- 001–027 迁移清单摘要匹配。
- JDK 17 Maven：SaasTenantAccessServiceTest 10 项、TenantLicenseGuardTest 6 项，全部通过，BUILD SUCCESS。
- git diff --check 通过。
- JDK 17 离线定向回归（*Motion*,*Tenant*）：system 73 项（1 跳过），rehab 65 项；共 137 通过、1 跳过、0 失败，BUILD SUCCESS。此数包含上述基础服务测试，不应与其重复累加。

## 未验证 / 未完成
- P3 整体未完成。基础服务尚未接入 HTTP、登录、任务、用户、患者、动作上传和存储写入；不能据此宣称全系统配额或租户隔离已启用。
- 配额单位测试采用 JdbcTemplate mock，不是并发数据库证明。真实并发事务、回滚、套餐变更共享锁、用量校准尚待验证。
- 机构自主注册、平台审批、平台管理员边界、用户单机构唯一性、机构邀请与患者/家长关联授权尚待实现和集成。
- P4 的平台/机构/患者端、桌面 OAuth 登录切换尚未完成。
- P5 新版 Backend/Frontend/Desktop 安装包、SaaS Compose 与安装脚本尚未交付。
- P6 新版安装、启动、合成评估至患者查看的端到端流程尚未执行。
- 本次未重新安装 Enterprise，也未将历史 Java 194 / Python 363 等结果算作本次通过。

## 迁移安全注意
027 原始校验和：0364a12a8bfee3679f7cb627a4bf747c3b729fe142a7fe09ffec55ed470f8ac8。
修复后校验和：c7a519052782f152175fa5dc316f3086ed951d815b6d9f558cdf2397852da56b。
本次只运行全新合成数据库；没有检查或修改业务数据库的迁移账本。
若任何已部署数据库曾登记旧 027，必须先审计其实际结构和账本，不能手工改账或绕过 checksum 保护。既有实例升级兼容性尚未验证。

## 商业上线阻断
1. 完整 SaaS 生命周期、跨租户拒绝、患者对象级权限和后台任务隔离未闭环。
2. SaaS 模式的 License 替代授权链及 Enterprise 模式回归尚未完成。
3. 新版构建、安装、公证/签名状态、升级与回滚、备份恢复、TLS 与部署密钥治理待验收。
4. 安全审计、压力测试、配额并发与全链路合成数据验收待完成。
支付接入不在第一阶段范围内，不因尚未接支付而阻断本阶段功能验收。

## 实机证据位置
/Users/saber/my-project/rehab/validation-evidence/
- saas-v2-access-20261012.log
- saas-v2-schema-20261012.log
- saas-v2-migration-guard-20261012.log
- saas-v2-tenant-motion-20261012.log
后两项以日志终态为准，不能将运行中日志视为通过。
