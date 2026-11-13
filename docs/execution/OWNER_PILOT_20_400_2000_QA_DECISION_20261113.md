# Owner → Core ↔ HEP：内测试点 20/400/2000（QA-only）

用户最新决策：

- 已核准三项额度：**20 用户、400 患者、2,000 次运动评估**。
- **存储尚未选择**。旧 synthetic-beta 的 1 GiB 仅用于隔离数据模拟，不是已核准的真实机构额度。
- Apple Developer ID 选择 **B：暂无证书，只进行 QA-only**。ad-hoc 签名包不得冒充 Apple 公证正式版。
- 此前 B/B/A/B/A 不变：原037管理员10项、治疗师8项的授权范围已批准，3条未匹配Owner ACTIVE机构保持冻结，真实机构仍需本人Owner MFA，5项临床用例仍需真人资质审核。

## 服务端席位限制

Owner 的许可界面可支持无限策略，但不得突破 Core SaaS 的用户上限。修复后的 SaasProvisioningService：

1. 新机构开户使用已启用计划的用户上限；Owner请求超过计划上限即拒绝。
2. 未传 seatLimit 的新机构取套餐用户上限，不再写入 Integer.MAX_VALUE。
3. 续期/扩容重新按当前有效计划、Owner UUID、未删除Core机构及有效订阅取得上限。
4. 未传 seatLimit 的续期保留已批准的现有席位，不自动提权到无限；原席位若已高于新套餐上限则拒绝并需人工核对。
5. 20用户套餐请求21席位、已停用套餐、已取消订阅、旧revision及跨机构请求不得扩大权限。

## 本机隔离验证命令

    node desktop/scripts/internal-pilot-plan-preview.mjs --selected-quota-draft
    node --test desktop/scripts/internal-pilot-plan-preview.test.mjs
    bash desktop/scripts/internal-pilot-selected-plan-isolated.sh --synthetic-only
    mvn -B -pl yudao-module-system -am -Dtest=SaasTenantAccessServiceTest,SaasEntitlementSyncContractTest,SaasProvisioningIdempotencySafetyTest -Dsurefire.failIfNoSpecifiedTests=false test

隔离MySQL脚本仅从正式Core读取 saas_plans 表结构，不复制正式表行、机构、患者或许可证；使用无网络tmpfs MySQL实际检验20/400/2000额度、暂定存储1GiB、激活与停用以及唯一性。**绝不向正式数据库写入。**

## 保留的正式门禁

1. 明确选择存储额度（1 GiB、5 GiB或自定义），形成完整正式套餐。
2. 使用合法最新加密备份及隔离恢复验证，确认035/036→037生产升级、JWT机器身份、HEP联动和Owner维护开关。
3. 本人完成真实Owner MFA，核准一个有授权的新内测机构申请，继续冻结历史3条不匹配机构。
4. 使用合法合成成人测试账号完整验证Owner→Core→独立HEP→Pain/RPE/风险→复评，并完成182项正式逐项验收和5项真人临床审核。
5. 当前Apple签名选项B保持有效；在收到新授权和可验证Developer ID前，只有QA包，不允许正式签名、公证或临床部署。

这份记录仅证明工程候选和用户授权的额度范围，**不代表**已签发真实许可证、临床结论或正式发行许可。
