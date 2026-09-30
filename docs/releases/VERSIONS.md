# 版本清单与历史标签

本文件记录每个历史状态对应的 Git 标签、提交和发布制品。历史项目副本目录已经删除，需要追溯时请直接检出对应标签：

```bash
git fetch --tags
git switch --detach <标签>
```

命名规则见 [README：版本、分支与历史](../../README.md#版本分支与历史)。

## 发布与基线

| 版本 / 状态 | 标签 | 提交 | 日期 | 发布制品 |
| --- | --- | --- | --- | --- |
| 内部生产版 | `rehab-internal-v1.0.0-20260729` | 1131ae1 | 2026-07-29 | —（对应的 `lan-v1.0.0` 语义待确认，暂不创建） |
| 局域网一键部署 RC | `lan-v1.1.0-rc.1`（规范名）/ `rehab-lan-v1.1.0-rc1`（已发布旧名） | e4b3ee0 | 2026-07-30 | `rehab-management-rehab-lan-v1.1.0-rc1.tar.gz`<br>SHA-256 `fa59d39b270dc17836fc6bd23a706e6eeb18291075331b603c35bfdf9c49d1c4` |
| 局域网 v1.0.1 | 暂无（对应提交待确认） | 待确认 | 2026-07-31 | `rehab-management-rehab-lan-v1.0.1-20260731.tar.gz`<br>SHA-256 `2e9019b62a7b4a6aaad3318ba7f17de510529180050906dadf644f42d88e7ca8` |
| 软著基线 | `copyright-v1.0.0` | 799c116 | 2026-07-31 | 见 `docs/software-copyright/v1.0/` |
| 桌面预览 1–4 | `desktop-v1.0.0-preview.1` … `.4` | — | 2026-07-31 至 2026-09-24 | 见 GitHub Releases（preview.4 为 Draft） |

## 历史归档标签（`archive/YYYY-MM-DD/用途`）

| 标签 | 提交 | 原来源 | 说明 |
| --- | --- | --- | --- |
| `archive/2026-07-30/lan-one-click-deployment` | 89aef78 | 分支 `agent/lan-one-click-deployment` | 补丁已等价合入 master |
| `archive/2026-07-31/desktop-packaging-v1` | fed4520 | 分支 `agent/desktop-packaging-v1`（PR #3） | 已合入 master |
| `archive/2026-07-31/integrate-all-v1` | 7b67603 | 分支 `agent/integrate-all-v1`（PR #4） | 已合入 master |
| `archive/2026-08-05/fix-migration-bootstrap-guard` | 9952471 | 分支 `fix/migration-bootstrap-guard` | 已合入 master |
| `archive/2026-09-20/module-enable-work` | bafc441 | 模块启用工作目录 | 已合入 master |
| `archive/2026-09-30/lan-one-click-deployment-wip` | 8560548 | 局域网部署工作目录的未提交工作 | 13 个修改 + 53 个未跟踪文件（SFMA、计划、进度服务，uniapp 原型，可选模块 DDL）。未经评审 |
| `archive/2026-09-30/admin-vue3-standalone-wip` | 1dd3658 | 独立的 Vue3 管理端仓库（上游 yudao-ui-admin-vue3） | 父提交 6ad355dc 为内部管理端交付，本提交为 13 个未提交修改。管理端现在直接纳入 `yudao-ui/yudao-ui-admin-vue3-app/` |
| `archive/2026-09-30/module-enable-work-wip` | b486c4f | 模块启用工作目录的未提交工作 | 7 项，其中 2 项为独有内容 |
| `archive/2026-09-30/desktop-packaging-leftovers` | 19c735b | 桌面打包目录 | Finder 副本 `README 2.md` |
| `archive/2026-09-30/software-copyright-leftovers` | b88ce0c | 软著目录 | Finder 副本 `sql/*/* 2.sql` |

`*-wip` 和 `*-leftovers` 标签只用于追溯，不能直接作为发布或合并来源。需要其中的内容时，请用 cherry-pick 或手动移植到新的 `feature/…` 分支，并正常评审。

## 进行中的分支

| 分支 | 说明 |
| --- | --- |
| `feature/motion-assessment` | 智能动作评估与量化分析（PR #5） |
| `agent/p0-safety-20260927` | P0 安全修复；内容已被 `feature/p0-hardening-and-type-repair` 包含并进一步修改 |
| `feature/p0-hardening-and-type-repair` | 2026-09-27 前后本地未提交的 P0 加固、管理端类型修补、桌面构建完整性检查与相关文档 |
