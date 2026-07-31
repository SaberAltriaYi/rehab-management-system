<!-- Copyright (c) 2026 [软件著作权人名称]. This inventory preserves third-party notices and does not replace component licenses. -->

# Third-Party Notices

运动康复评估与业务管理系统 V1.0 includes or depends on open-source and separately licensed components. The component's own license controls in the event of a conflict.

## Upstream application framework

| Component | Role | License / notice |
| --- | --- | --- |
| RuoYi-Vue-Pro（芋道源码） | Backend framework and general administration modules | MIT; copyright notice retained in root `LICENSE` |
| Yudao Vue 3 administration frontend | Web administration framework | MIT; copyright notice retained in frontend `LICENSE` |

## Major backend and runtime components

| Component | Version source | Typical license |
| --- | --- | --- |
| Java / OpenJDK | Dockerfile and build environment | GPLv2 with Classpath Exception |
| Spring Boot / Spring Framework | `yudao-dependencies/pom.xml` | Apache License 2.0 |
| Spring Security | `yudao-dependencies/pom.xml` | Apache License 2.0 |
| MyBatis / MyBatis-Plus | `yudao-dependencies/pom.xml` | Apache License 2.0 |
| Apache PDFBox | `yudao-dependencies/pom.xml` | Apache License 2.0 |
| Apache POI | Maven dependency graph | Apache License 2.0 |
| Jackson | `yudao-dependencies/pom.xml` | Apache License 2.0 |
| MySQL Connector/J | `yudao-dependencies/pom.xml` | GPLv2 with Universal FOSS Exception |
| Nginx | `deploy/internal/Dockerfile.admin` | 2-clause BSD |
| MySQL Community Server | `deploy/internal/docker-compose.yml` | GPLv2; image is pulled at deployment time |
| Redis 7.4 | `deploy/internal/docker-compose.yml` | Redis Source Available License 2.0 / SSPLv1; image is pulled at deployment time |

## Major frontend components

| Component | Version source | Typical license |
| --- | --- | --- |
| Vue | `package.json` / `pnpm-lock.yaml` | MIT |
| Vite | `package.json` / `pnpm-lock.yaml` | MIT |
| TypeScript | `package.json` / `pnpm-lock.yaml` | Apache License 2.0 |
| Element Plus | `package.json` / `pnpm-lock.yaml` | MIT |
| Axios | `package.json` / `pnpm-lock.yaml` | MIT |
| ECharts | `package.json` / `pnpm-lock.yaml` | Apache License 2.0 |
| Pinia | `package.json` / `pnpm-lock.yaml` | MIT |
| vue-router | `package.json` / `pnpm-lock.yaml` | MIT |
| dayjs | `package.json` / `pnpm-lock.yaml` | MIT |

## Authoritative dependency records

- Maven coordinates and pinned versions: `pom.xml`, module POM files and `yudao-dependencies/pom.xml`
- Frontend direct and transitive versions: `package.json` and `pnpm-lock.yaml`
- Container image tags: `deploy/internal/docker-compose.yml` and Dockerfiles
- Upstream source attribution: `NOTICE.md`

This file is an application-preparation inventory, not a legal opinion. Before external distribution, regenerate the complete Maven and pnpm license reports and have the final list reviewed by the rightsholder or counsel.
