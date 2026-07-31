<!-- Copyright (c) 2026 杨玺龙. -->

# 自研代码清单

以下是 V1.0 原创性核对的候选范围，不等同于未经核验的权属结论。申请前应结合 Git 作者、劳动/委托关系和原始设计记录逐项确认。

| 路径 | 内容 | 当前文件量参考 | 权属核对重点 |
| --- | --- | ---: | --- |
| `yudao-module-rehab/` | 康复领域模型、接口、服务、数据访问、评估/报告逻辑和测试 | 314 | 排除复制的上游通用代码与第三方方法文本 |
| `yudao-ui/.../src/views/rehab/` | 康复管理界面、评估表单和交互流程 | 与 API 合计 84 | 核对设计稿、提交作者和评估文本来源 |
| `yudao-ui/.../src/api/rehab/` | 康复前后端接口适配 | 与页面合计 84 | 核对接口与后端自研范围一致 |
| `sql/mysql/rehab-*.sql` | 康复业务表、菜单、权限和迁移 | 16 | 合成演示数据不得作为真实数据提交 |
| `deploy/internal/`、`deploy/lan/` | 内部安全配置、备份恢复、证书与一键部署 | 合计路径统计见 Git | 保留 Docker/第三方镜像声明 |
| `script/rehab/` | 构建、回归、发布和敏感材料检查 | 合计路径统计见 Git | 新脚本作者及日期 |
| `docs/software-copyright/v1.0/` | V1.0 申请准备资料 | 11 | 最终签字版本线下受控保存 |

## 不应计入自研源程序页的范围

- `yudao-framework/`、`yudao-module-system/`、`yudao-module-infra/` 等上游框架通用代码；
- 前端通用布局、组件库、上游业务页面和 `.image/` 宣传素材；
- SFMA、FMS、NASM-CES 方法内容目前无授权或其他使用依据；相关协议构建器、评分/分流规则、表单、配置、测试及说明文字不得选入申请鉴别材料，也不得作为原创方法主张；
- `node_modules`、Maven 缓存、构建产物、压缩包、数据库备份；
- 许可证、第三方生成代码、第三方字体或图片；
- 仅改名但没有原创表达的机械变更。

## 取证建议

1. 使用最终提交执行 `git log --follow -- <path>` 和 `git blame <file>`。
2. 导出最终提交的自研候选文件列表、行数和 SHA-256。
3. 将需求记录、原型、数据库设计、测试记录和发布记录与提交日期对应。
4. 对合作开发、员工职务开发和委托开发分别保存有效的权属文件。
5. 源程序鉴别材料优先选取连续、可读、能体现核心业务逻辑的自研代码。

## 第三方评估方法排除路径

申请源程序和说明书选页时，至少排除以下路径及其直接生成内容：

- `yudao-module-rehab/.../assessment/RehabSfmaBookProtocol.java`
- `yudao-module-rehab/.../assessment/RehabSfmaSummaryBuilder.java`
- `yudao-module-rehab/.../assessment/RehabNasmCesSummaryBuilder.java`
- 对应的 SFMA/NASM-CES 测试类
- `src/views/rehab/assessment/components/forms/sfma/`
- `SfmaForm.vue`、`FmsForm.vue`、`NasmCesForm.vue`
- `sfmaConfig.ts`、`sfmaBookProtocol.ts`、`nasmCesConfig.ts`

这项排除只限定软著鉴别材料和权利主张，不代表已经完成这些功能在实际使用、展示或分发层面的商标、著作权或专业认证合规审查。
