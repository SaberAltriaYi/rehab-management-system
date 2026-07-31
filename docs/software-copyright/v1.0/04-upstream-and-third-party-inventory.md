<!-- Copyright (c) 2026 杨玺龙. -->

# 上游及第三方依赖清单

## 上游边界

V1.0 基于 RuoYi-Vue-Pro（芋道源码）和芋道 Vue3 管理后台二次开发。根目录与前端目录的 MIT License、上游作者标识、项目 URL和代码注释应继续保留，不得在软著申请中写成完全从零开发。

## 依赖记录

- 后端直接和传递依赖：根 POM、各模块 POM、`yudao-dependencies/pom.xml` 与 Maven dependency tree。
- 前端直接和传递依赖：`package.json`、`pnpm-lock.yaml`。
- 运行时容器：`deploy/internal/docker-compose.yml` 与 Dockerfiles。
- 主要许可证和特别事项：根目录 `THIRD_PARTY_NOTICES.md`。

## 特别审查项

1. Redis 7.4 镜像的许可证不是传统 OSI 开源许可证；本发布包只声明镜像标签并在部署时拉取，不把镜像层打入源码材料。
2. MySQL、OpenJDK、Nginx 和第三方 JavaScript/Java 库均按各自许可证使用。
3. 杨玺龙已确认当前没有 SFMA、FMS、NASM-CES 方法内容的授权或其他使用依据。相关协议文字、图表、评分/分流规则、表单和方法说明不进入软著鉴别材料，也不作为自研权利主张。
4. 正式对外分发前重新生成完整依赖许可证报告，并审查字体、图标、图片和评估说明文本的来源。
