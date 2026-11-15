# Owner/Core/HEP 正式身份联动：受控上线及回滚

日期：2026-11-13。当前状态：工程候选，不能视为正式临床部署已经通过。

## 业务边界

Owner 是机构申请、MFA 审批和许可的唯一权威。Core 管理 Tenant、员工、患者、Episode、治疗师归属及临床签发；独立 HEP 根据 Core 签发 JWT/JWKS 和受限机器令牌处理训练执行、Pain/RPE/风险反馈。

禁止未获真实 Owner MFA 就批准、开户、修改许可证；禁止自动补全临床签发、真实患者身份或病历。现有 Core 036 不得单独手动执行 SQL 037。

## 已确认的现状

- 正式 Core HTTPS 为 https://127.0.0.1:18443；正式数据库仍是 036:36，HEP 037 表与菜单均未部署，Core 容器未注入机器密钥及启用开关。
- Owner 原 48080 HTTP 失效目标已修复为当前 HTTPS 18443，并加载当前安装版 Core 的公开 TLS 证书供 Node 进行严格链/主机名校验。重启后的 Owner 健康200、匿名 API401，7条真实机构申请未更改；先前已作新的 AES-256-GCM 加密 Owner 备份并校验。
- Owner 启用开关 REHAB_MAIN_PROVISIONING_ENABLED 目前为 false，防止仅修正 URL 就自动开通机构。
- Owner/Core 私有文件中的开通机器令牌匹配；HEP/Core 私有文件中的桥接机器令牌匹配。但 Core 安装容器未加载这两个令牌；不能为了测试成功而生成新的令牌。
- Core 私有 core.env 曾是旧 HTTP 48080；本轮在建立独立 AES-256-GCM 密文备份后，已更正为与 HEP 私有配置完全一致的 HTTPS 18443 issuer，原机器令牌和其他配置逐字节不变。**正式 Docker 容器尚未加载这个私有文件，不能把配置修正当成正式 Core 已签发 JWT。**
- Core saas_plans 目前无有效计划（status=1），原 Owner 计划编号因而无对应有效记录。实际套餐、席位、期限、配额和所属机构必须依 Owner 商业授权资料核准，不能生成猜测的套餐。
- 037 旧迁移将给当前 tenant1 的 super_admin 新增10条菜单权限，给 rehab_therapist 新增8条。角色影响未获审批；不得自动迁移。

## 受控 Launcher 机器身份通道

源码文件 desktop/launcher/src-tauri/resources/core-identity.override.yml 不包含真实私钥或令牌。下一次使用经独立构建、受控安装的新版 Launcher 时，操作员可按原样将这个覆盖模板放到已安装配置的 .env 同目录，权限0600，然后在仅本地可读的 .env 中配置批准的机器 token、HTTPS issuer 和 REHAB_HEP_JWT_HOST_FILE。Launcher 仅接受与内置源模板字节完全一致的普通文件，拒绝改动、软链接、其他用户可读和超长配置。

私有签名 PEM 只读挂载到容器 /run/rehab-secrets/hep-core-jwt-private.pem；源代码、镜像和 QA 日志中不得出现原始密钥。两项机器启用开关均默认 false。覆盖模板在当前已安装 Launcher 上尚未部署，也不应直接手动安装至旧036版本。

独立合成 docker compose config 已验证：覆盖层只对现有 server 添加机器令牌、精确 issuer 和只读 PEM 挂载，没有额外服务或对外端口；缺少私钥路径则拒绝配置。Rust 测试验证权限0600、内容防篡改及软链接拒绝。

## 正式切换门禁

1. Owner 确定有效计划和套餐、真实机构 Tenant UUID/Core 关联，避免历史已审批机构重复开户；保持 MFA。
2. 机构审核 037 迁移计划新增的18条角色权限。若不允许既有治疗师自动扩权，需要按既定迁移校验机制设计新版本并隔离回归，不能无审查篡改已存的037校验和。
3. 冻结 Core/HEP/Launcher/Runtime 同一版本，先创建正式加密 SQL+附件备份并独立验真和恢复，保存原有033–036迁移账本及滚回方案；然后在受控窗口合并部署037、实际 Java/Core前端、身份/JWT环境和私钥只读挂载。不能只手工修改Docker环境或数据库。
4. Owner 未登录/错误令牌不得开通；真实 Owner MFA后在受控临时表单输入管理员初始凭据，测试同申请幂等、跨租户拒绝、撤销与续期同步。不要把口令写入聊天、日志或源码。
5. Core JWKS签名与独立HEP的TLS、issuer、aud、tenant、roles、sub、exp都要逐项验收；错签名、跨租户/撤销身份须拒绝。用授权合成成人患者完成Core患者→Episode→临床处方人工签发→HEP执行/疼痛/RPE/风险→Core幂等事件→人工复评流程，不自动签发临床结论。
6. 双重正式发行门禁（182项独立验收/5项临床人工审核和12项安装前门禁）均通过之后才可以合法签名、公证、正式发布。

## 独立本机证据

Owner 新加密备份：/Users/saber/my-project/rehab-runtime/backups/owner-identity-https-20261113/

Core 私有 issuer 配置加密备份：/Users/saber/my-project/rehab-runtime/backups/core-hep-issuer-https-20261113/；解密密钥独立存在本机 core-local 私有目录，权限0600。

本轮 QA 目录：/Users/saber/my-project/rehab/validation-evidence/qa-20261113-formal-owner-hep-identity/

本轮确实完成 Owner HTTPS目标/信任修复，Core/HEP Launcher私有身份传递候选及合成负向验证，但正式Core036、套餐与角色批准未解决，不宣称正式机构开通或真实临床业务联动成功。

## 2026-11-13 补充：用户确认 B/B/A/B/A

1. **B 套餐：** 准备正式内测试点方案。草案采用源码现有 synthetic-beta 的 10 用户、100 患者、1,000 次运动评估和 1 GiB 存储作为建议起点；尚未对真实客户承诺配额，也没有创建或启用正式计划。
2. **B 权限：** 用户同意原始 037 中管理员 10 项、治疗师 8 项的授权范围。隔离 MySQL 实测 11 张 HEP 表、10 个菜单、管理员 10 项、治疗师 8 项、其他租户 0 项；治疗师没有动作审核或机构集成管理权限。该决策不是临床签发。
3. **A 历史机构：** 3 条 Owner ACTIVE 但不匹配当前 Core 的历史绑定冻结，不自动重绑或重建机构。
4. **B 维护窗口：** 准备满足所有审批后再执行的维护方案。已修正 V3/V4 维护脚本里过时的“Owner HTTPS 目标存在即拒绝”条件，改为明确要求 Owner 开通开关为 false，并核验严格 HTTPS。V3/V4 在正式 Core 的真实表结构上双次隔离演练通过；旧 Core 加密快照完成解密和隔离恢复至 037。
5. **A 临床审核：** T-107、T-113、T-114、T-129、T-189 留给具备相应资质和授权的用户本人实质审核，目前未签发。

### 新发现的维护前置阻断

最新加密 Core SQL/附件备份工具本次返回 CORE_ENCRYPTED_BACKUP_FAILED，没有生成新备份清单。正式 Core 的四个容器健康，数据库和附件的只读导出各自成功，但工具固定要求的 /Applications/康复管理系统内部版.app/Contents/Resources 当前不存在，/Applications 与 ~/Applications 均未找到正式 App。不能用未经正式发布门禁批准的 QA DMG 替换在用系统。旧快照仍已验证可解密、恢复及隔离升级，但不能充当维护窗口开始时的新备份。

在新正式安装包、最新加密快照的独立恢复、真实 SaaS 商业套餐和 Owner MFA 通过之前，仍然禁止正式 Core 037 迁移和真实机构自动开通。验证细节位于 /Users/saber/my-project/rehab/validation-evidence/qa-20261113-formal-owner-hep-identity/，115 项桌面测试均通过，没有修改正式 036 数据或任何病历。
