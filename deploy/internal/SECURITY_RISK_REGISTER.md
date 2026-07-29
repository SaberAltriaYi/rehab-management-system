# 内部康复版安全风险登记

检查日期：2026-07-29

范围：`rehab-internal-server`、`rehab-internal-admin`、`mysql:8.4.10`、
`redis:7.4.10-alpine`

工具：Trivy `v0.72.0`

## 扫描结论

- 四个镜像操作系统包：High `0`，Critical `0`。
- 后端 Java 库：初始 High `49`、Critical `7`；处置后 High `6`、Critical `2`。
- 已升级或移除：Tomcat、Jackson、Netty、MySQL Connector/J、Logback、SnakeYAML、
  c3p0/mchange、Tika、BPM、CRM。
- 当前没有已确认可从本内部部署边界触达、且缺少控制措施的 High/Critical 条目。

## 剩余条目与控制

| CVE | 扫描级别 | 适用性结论与控制 |
| --- | --- | --- |
| CVE-2016-1000027 | Critical | 项目没有使用或注册 `HttpInvokerServiceExporter`，不存在 Java HTTP Invoker 反序列化入口。 |
| CVE-2024-38816 | High | 后端没有使用 `RouterFunction` 配合 `FileSystemResource` 暴露静态目录；静态资源由 Nginx 固定目录提供。 |
| CVE-2024-38819 | High | 后端没有使用 WebMvc.fn/WebFlux.fn 文件资源路由，条件不成立。 |
| CVE-2025-22228 | High | 所有后台/会员密码校验在服务层拒绝超过 72 字节的输入，公开 DTO 上限为 16；已增加专项测试。 |
| CVE-2025-22235 | High | 项目未使用 `EndpointRequest.to(...)`；外部 `/actuator` 被 Nginx 封堵，只暴露容器内 `health/info`。 |
| CVE-2025-41249 | High | 权限注解位于具体控制器方法，未在泛型父类或泛型接口上声明；条件不成立。 |
| CVE-2026-22732 | Critical | 已按官方兼容方案设置 `HeaderWriterFilter.shouldWriteHeadersEagerly=true`，Nginx 同时统一写入生产安全头。 |
| CVE-2026-40973 | High | 应用使用 `STATELESS`，并显式设置 `server.servlet.session.persistent=false`；容器以非 root 用户运行。 |

## 强制边界

1. 仅限可信工作室局域网，不得映射到公网或开放 MySQL、Redis、后端端口。
2. AI、BPM、CRM、商城、支付、IoT 等未交付模块必须持续关闭。
3. 每次发布运行 `security-scan.sh`；出现不在上表的新 High/Critical 必须阻断发布。
4. 若产品需要公网、第三方接入或不可信文件处理，本风险接受自动失效，必须先迁移至
   JDK 17 / Spring Boot 3 的受支持基线并重新评估。
5. JDK 17 / Spring Boot 3 迁移作为下一技术周期 P1 技术债，由发布负责人登记负责人和计划日期。

参考：

- Spring CVE-2026-22732：https://spring.io/security/cve-2026-22732/
- Spring CVE-2026-40973：https://spring.io/security/cve-2026-40973/
- Spring CVE-2025-22235：https://spring.io/security/cve-2025-22235/
- Spring CVE-2025-41249：https://spring.io/security/cve-2025-41249/
- Trivy Java 扫描说明：https://trivy.dev/docs/v0.72/guide/coverage/language/java/
