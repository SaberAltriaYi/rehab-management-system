# Owner MFA 后的真实系统授权准备检查点（2026-11-14）

## 适用范围与授权边界

- 采用已获确认的 A 路线：在既有 Mac1 Owner/Core/HEP 服务上检查，不额外部署一套正式系统。
- 用户已在对话中确认其本人完成 Owner MFA 登录；本轮 **未读取浏览器 Cookie、TOTP 或 Owner 私钥**，因此不把用户确认误记为服务端审计已确认的特定审批事件。
- 本次仅执行读操作及本地纯测试；未批准申请、未创建 SaaS 套餐、未签发许可、未启用 Owner/Core 机器令牌、未迁移 Core、未安装或替换 App。

## 实际现网只读检查结果

| 检查项目 | 2026-11-14 现场结果 | 限制 |
| --- | --- | --- |
| Owner 控制台与 Core HTTPS | 返回 HTTP 200，TLS 验证通过 | 不代表已登录会话授权通过 |
| Owner 申请状态计数 | 7 条：APPROVED 5、PENDING 2 | 未列出名称、申请编号或身份 |
| Owner 开通状态计数 | ACTIVE 3、FAILED 2、NOT_CONFIGURED 2 | 不把旧 ACTIVE 当作当前 Core 的有效映射 |
| Owner ACTIVE ↔ 当前 Core | 3 条未匹配、匹配 0 条 | 按先前 A 决策继续冻结，不自动重绑 |
| Core 正式 SaaS 套餐 | 总数 0、激活 0 | 已确认额度不等于创建计划 |
| Core 未删除权限套餐 | 1 条 | 尚未获准将其指定给某条真实机构 |
| 当前正式 Core 迁移 | 036，共 36 条，HEP 表/菜单均 0 | 037 仅为候选，未部署 |
| Owner→Core 机器开通 | `ready=false`，Owner 开关和 Core 开关关闭、机器令牌未验证、计划套餐未验证 | 不得为通过预检直接打开开关 |
| Core↔HEP 正式身份 | `ready=false`，bridge/JWT/037 未就绪 | 不能签发真实 HEP 临床业务许可 |
| 已安装 Mac App 授信 | 有 App，但 `installedLauncherTrustConfigured=false` | 发行公钥 0 个、Owner 授权根地址缺失 |
| Apple Developer ID | 有效代码签名身份 0 个 | 用户此前选 B：只允许 QA-only |
| 正式业务验收 | 182 项未签核 | 5 项临床真人审查仍需本人签发 |

试点额度沿用已确认的 **20 用户 / 400 患者 / 2,000 次评估 / 5 GiB**，草案状态仍为 `DRAFT_NOT_CREATED`；不重新讨论额度。037 的既定权限影响为管理员新增 10 项、治疗师新增 8 项，正式迁移仍须维护与备份门禁。

## 本轮实际测试

- 执行了现有真实系统的 `owner-core-provisioning-preflight.mjs --live`、`hep-release-preflight.mjs --live`、`owner-core-link-audit.mjs --live`、`hep-037-permission-diff.mjs --live`。
- `node --test` 针对 Owner/Core、历史关联、试点门禁、037 权限与许可证明的 **32 项测试全部通过**；这些是纯测试，不替代现网真实审批。
- 之前提交 `535513dfc2` 的静态发行信任校验在实际安装 App 上正确返回 `installedLauncherTrustConfigured=false`，不会将 App 存在等同授权就绪。
- 仅发现先前日期的备份清单元数据；**没有在本轮生成正式环境最新加密备份，也没有复验完整恢复**。正式迁移/写入前必须重新执行受控备份与恢复验真。

## 下一次继续的精确入口

1. 用户本人在 Owner 控制台中确认 **2 条 PENDING 中哪一条属于已合法授权的新试点申请**；不得把历史 3 条 ACTIVE 机构自动关联到当前 Core，也不得自动批准待定申请。
2. 由发行者确认正式 Owner 授权 HTTPS 域名以及可信发行公钥的合法来源。`127.0.0.1:9444` 只适用于本机管理后台，不作为其他工作室的发行授权站点。禁止在聊天中提交私钥、TOTP 或密码。
3. 获得用户对真实套餐创建、指定权限套餐、受控 Core 037 部署及授权审批的逐项许可后，先执行新鲜加密备份、独立恢复验证、权限最小化审核和一致版本检查；再在维护窗口实施。
4. 完成 182 项正式验收、5 项临床真人签核及 Apple 正式签名/公证要求之前，维持 **NO-GO、QA-only**。用户已选 Apple B，不能擅自改成 A。

### 重启后的安全复核命令（全部只读，前置路径为本仓库）

```bash
node desktop/scripts/owner-core-provisioning-preflight.mjs --live
node desktop/scripts/hep-release-preflight.mjs --live
node desktop/scripts/owner-core-link-audit.mjs --live
node desktop/scripts/approved-pilot-rollout-preflight.mjs --live-readonly
```

预检返回状态码 2 和 `ready=false` 表示**发行被安全阻断**，不是测试执行工具故障。后续不得为了改变返回值而修改生产数据或信任配置。
