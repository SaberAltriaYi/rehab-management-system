# S1 邀请生命周期、短信原子消费与依赖安全

日期：2026-10-27。第二批在首批 `954227009a`、同名远程历史合并 `1af2fa738c` 基础上继续。未改数据库结构/迁移账本，未重启业务后端、未创建业务账号、未修改真实患者；AI 关闭。管理端当前是开发服务，依赖重装可能触发开发资源重新加载，不等于已执行生产制品升级。

## 身份生命周期

发放和兑换均按“当前机构患者行 → 精确邀请行”的次序加锁。兑换先做无锁摘要查询以定位患者，加患者锁后再次查询邀请状态并锁定，避免撤销/重新发放与消费竞争，也避免反向锁顺序与外键插入相互等待。并发签发只能留下最新一个待用邀请。新邀请默认 30 分钟、范围 5–1440 分钟，接口及服务同时校验；不静默修改既有超长邀请。

短信验证码原实现为“读出未使用 → 无条件更新已使用”，并发请求可能同时验证成功。现改为按 ID、未使用状态及仍在有效期条件原子更新；受影响行数必须等于 1，否则拒绝。不改变既有短信供应商、验证码格式或患者绑定要求，不发送真实短信。限频/防枚举仍是单独工作包。

本机 JDK 17 `mvn -B -pl yudao-module-rehab -am clean test` 构建成功：康复 239 项，238 通过/1 原有可选真实引擎跳过；其中邀请事务 8 项、严格 mock 身份 18 项；系统短信服务 10 项通过，含真实隔离 H2/MyBatis 并发消费只成功一次、已消费/过期拒绝；Web 日志安全 6 项通过。没有使用 lenient Mockito 或关闭断言。定向测试中曾因过度共用 fixture 引入未使用 stub，已将加锁 stub 仅放入兑换 fixture 并以 clean 全量回归确认，不把失败重跑前的记录算通过。

## CI 发现和最小依赖修补

`1af2fa738c` 的 Internal release CI 后端、可选业务、移动端、部署及 PowerShell 成功，管理前端的生产构建和类型检查也成功；失败点是生产依赖审计。本地相同锁文件复现 10 条公告：1 critical、6 high、1 moderate、2 low。依赖审计命中不证明本应用的每条漏洞利用链实际可达，但不能据此忽略或降低检查级别。

| 包 | 当前修补 | 依据 |
| --- | --- | --- |
| DOMPurify | 3.4.16 → 3.4.17 | [模板安全公告](https://github.com/advisories/GHSA-cg2g-xh64-v4q7)、[IN_PLACE 公告](https://github.com/advisories/GHSA-mrv2-3pqr-j5jr) |
| markdown-it | 14.3.1 → 14.3.3 | [链接引用复杂度公告](https://github.com/advisories/GHSA-gfr2-rv44-r23c) |
| follow-redirects | 1.16.0 → 1.16.1 | [跨域凭据清理公告](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8) |
| undici | 6.29.0 → 6.29.1 | [维护者内容编码公告](https://github.com/nodejs/undici/security/advisories/GHSA-xjj6-f33q-929c) |

固定直接依赖及传递覆盖，pnpm 10.15.1 生成锁文件，使用官方 npm registry；没有删除锁文件重新做全量升级。保留原有不相关的平台 libc 元数据，避免跨平台选择发生额外变化。修补后首次 `pnpm audit:prod` 返回 `No known vulnerabilities found`；再次冻结安装及构建/类型结果由检查点追加，不等同所有 Java/Rust/Python/开发依赖均无风险。pnpm 是本项目 CI 的权威锁文件；未以旧 npm 锁文件作发布依据。

随后 `pnpm install --frozen-lockfile`、再次 `pnpm audit:prod`、`pnpm build:internal`、`pnpm ts:check` 均成功。保留既有 echarts-wordcloud 对 ECharts 5 的 peer 提示和旧 Browserslist 数据提示，不扩大本批依赖升级。CI 互斥按来源仓库/分支分组，避免同一分支 push 与既有 PR 重复跑全套，不跳过任何门槛。

## 发布状态

正式桌面工作流本轮实际失败于生产依赖审计，后续许可闸门尚未执行，未创建安装器或 Release。签名、公证、发布者信任根及正式许可迁移门槛不能放宽；后续 S4 应将无签名工程测试制品与正式发布分开，测试制品仍不得绕过许可/身份或承载真实患者。尚未达到整体 S1 退出或生产上线要求。
