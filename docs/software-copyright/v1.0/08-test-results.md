<!-- Copyright (c) 2026 [软件著作权人名称]. -->

# V1.0 测试结果

测试日期：2026-07-31

测试分支：`agent/software-copyright-v1`

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| 仓库敏感材料 | `script/rehab/check-repository-sensitive-materials.sh` | 通过；未发现受 Git 跟踪的真实凭据、私钥、备份、首次登录文件或未标记患者样本 |
| 康复后端测试 | `mvn -B -pl yudao-module-rehab -am test` | JDK 17 下通过；共 723 项，694 项通过、29 项跳过、0 失败、0 错误；其中康复模块 83/83 通过 |
| 后端隔离发布构建 | `deploy/internal/build-server-isolated.sh` | 通过；22 个 Reactor 模块构建成功并生成可执行 JAR |
| 前端锁文件安装 | `pnpm install --frozen-lockfile` | 通过；锁文件未被修改 |
| 前端内部构建 | `pnpm build:internal` | 通过；生成 `dist-internal` |
| 前端生产依赖审计 | `pnpm audit:prod` | 通过；0 个已知漏洞 |
| 迁移文件校验 | `deploy/internal/migrate.sh verify-files` | 通过；001–019 与固定 SHA-256 清单一致 |
| 发布回归 | `script/rehab/internal_release_regression.sh` | 通过；预检、34 张康复表、41 个外键、迁移账本、四服务健康、HTTPS/鉴权和只读冒烟均通过 |
| V1.0 发布包构建 | `script/rehab/build-lan-release.sh 1.0.0` | 通过；生成 `sports-rehab-management-system-1.0.0.tar.gz` 及 SHA-256 文件 |

说明：

- 后端项目兼容测试环境采用 JDK 17。系统默认 JDK 23 下，Mockito inline mock 初始化失败；切换到 JDK 17 后同一测试集全部通过，判定为测试工具链与新版 JVM 的兼容问题，不是本次变更回归。
- 发布回归使用本机已运行的内部 Docker 环境执行，只读检查没有创建、修改或删除患者业务数据。
