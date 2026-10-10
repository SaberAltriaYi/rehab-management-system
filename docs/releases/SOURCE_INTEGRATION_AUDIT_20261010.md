# PR #5 集成准备审阅｜2026-10-10（只读审计，不等于合并批准）

状态：**待代码负责人审核；无 merge、commit、push、reset、clean 或远端变更。**

## 审阅范围

- 主仓：`master@3d778538a3`，`/Users/saber/my-project/rehab/rehab-management-system`
- 动作评估工作树：`feature/motion-assessment@2f7f515973`，`/Users/saber/my-project/rehab/worktrees/motion-assessment`
- 比较两棵 dirty 工作树中 **6 个同路径有改动文件的当前字节内容**；此审阅**不是 PR #5 全部提交/冲突清单**，没有试图自动合并。

| 共享文件 | 两树当前内容 | 合并风险及必须保留的行为 |
| --- | --- | --- |
| `RehabDataPermissionService.java` | 当前文件内容一致，0 行差异 | 本次无内容冲突；整合时仍须复核患者行级权限，不能只看字节一致 |
| `RehabDataPermissionServiceTest.java` | 当前文件内容一致，0 行差异 | 保留既有合成患者权限负例 |
| `desktop/scripts/runtime-tools.mjs` | 动作树相对主线 +92/-12 行 | **高优先级语义整合**：动作树增加迁移清单、校验、020–025 复制及账本范围；主线已经强化公开静态资源副本 0644/0755 及校验。两者功能均须保留，不可直接以任一文件覆盖另一方 |
| `desktop/scripts/runtime-tools.test.mjs` | 动作树相对主线 +67/-2 行 | 合并迁移相关测试与前端权限负例测试，确保改动只影响拷贝，不扩大秘密资源权限 |
| `yudao-ui/yudao-ui-admin-vue3-app/package.json` | 动作树相对主线少 1 行 | 主线有直接依赖 `dompurify@3.4.16`，供报告预览 HTML 净化；**合并时必须保留**，不得通过使用动作树的 package.json 删除安全依赖 |
| `yudao-ui/yudao-ui-admin-vue3-app/pnpm-lock.yaml` | 动作树相对主线少 3 行 | 差异仅为同一个 DOMPurify importer；应与保留该依赖的 package.json 一起处理，之后按冻结锁文件安装 |

## 建议的可审阅整合顺序（未执行）

1. 在获得源码整合授权后，首先生成独立可逆的变更补丁和六个共享文件的 SHA-256 清单，冻结两个工作树的未提交内容供逐项审阅。
2. 以**功能并集**人工整合 `runtime-tools.mjs` 及测试：安全权限副本规范化、严格公开文件读取模式、迁移清单校验、文件一致性和 020–025 复制；禁止未经验证地选择某个分支的整文件。
3. 保留主线 DOMPurify 精确直接依赖及锁文件 importer，并在整合工作树重跑报告预览的 script/iframe/event/javascript URL 净化负例。
4. 复核 Java 权限文件的历史提交差异，而不只依赖当前文件相同这一事实；整合后验证两租户和患者授权路径及附件下载。
5. 在冻结的**同一提交**上重新运行完整 TypeScript/生产构建、Java JDK17、动作专项测试、Node Runtime、Python Engine、隔离 Compose、真实 Chrome 和 Tauri 升级/恢复回归；保留所有失败日志和哈希。

## 现有限制

- 未运行真实 Git 合并、cherry-pick、stash 或 PR #5 冲突重演，也未核对全部历史提交、GitHub PR 远端状态。
- 这里的行数是两份当前文件的字节差异统计，不代表未提交内容已经获得所有权和提交审批。
- 本批 Rust 核心方法合成 E2E 虽真实执行并成功恢复/升级，但 Tauri GUI/签名安装器、并发数据一致性和跨平台仍无正式发行证据；`source_integration`、`desktop_restore_upgrade` 等发布门禁继续 BLOCKED。
