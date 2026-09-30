# 测试矩阵与结果

执行日期：2026-09-30，环境：macOS（JDK 17 运行 Java 8 目标代码、Maven 离线、Node 22.15、Python 3.13）。
容器联调、真实 OpenCap 云端拉取与浏览器真实路径走查已完成（见第 4 节），期间发现并修复 7 个缺陷。

## 1. 需求 → 测试映射

| 需求 | 测试 |
|---|---|
| FMS 疼痛 → 0 / 疼痛缺失不给建议 | 引擎 `test_motion_scoring`（pain / clearing）；后端 `painZeroCannotBeOverriddenToNonZero`、`missingPainBlocksReview` |
| FMS 取低侧、最多 3 次取最佳、无效不计 1 | 引擎 `test_motion_scoring`（lower side / best of three / invalid reason） |
| FMS 总分仅 7 项审核后 | `fmsTotalOnlyWhenAllSevenReviewed`、`patientViewShowsOnlyReviewedFinalScoresAndNoTotalUntilAllSeven` |
| FMS 官方对齐（非分级条目、ASLR/肩边界、分级条件） | 引擎 official-alignment 3 项 + `test_fms_tier_conditions_match_backend_contract`；后端 `MotionTrialConditionTest` |
| YBT 公式与不对称 | 引擎 YBT 测试（归一化/复合/不对称，4 cm 只提示） |
| TJA 两种版本 | 引擎 tuck jump 测试；后端 `lessAndTuckJumpMapWithTheirOwnSchemes`、`validateFinalRangesPerScheme` |
| LESS 3 次均值 | 引擎 LESS 测试（17 项、3 次均值、试次不足不出分） |
| 解析鲁棒性（列顺序、缺列、缺帧、单位） | `test_column_order_does_not_change_metrics`、`test_missing_column_is_unavailable_not_zero`、`test_gaps_stay_missing_and_are_reported`、`test_unknown_angle_unit_is_unavailable`；后端 `missingFieldsDegradeToNullNeverZero` |
| 阶段 / 重复 / 着地 | `test_repetitions_and_known_geometry`、`test_no_movement_means_no_phase`、`test_truncated_repetition_is_not_counted`、`test_landings_detected_for_tuck_jumps` |
| NASM 步态时空参数（含双支撑） | `test_nasm_gait_spatiotemporal_metrics_match_synthetic_walk` |
| 坐标库起始值 / 峰值时序（表 14） | `test_coordinate_metrics_include_start_value_and_time_to_peak` |
| 真实 OpenCap 样本端到端 | `test_real_sample_end_to_end`（需 `MOTION_REAL_SAMPLE_DIR`，数据不入库） |
| OpenCap / 引擎超时、重试、幂等 | `HttpMotionEngineClientTest`（5xx/429/超时可重试、4xx 致命）；`MotionTaskStatesTest`（状态顺序、从失败步骤续跑、指数退避）；`sameIdempotencyKeyReturnsExistingTask`、`concurrentDuplicateInsertResolvesToExistingTask` |
| AI Schema / 不能改分 / 去标识 / 关闭可用 | `MotionAiGuardrailTest` 4 项；`aiDraftReviewNeverTouchesScores`、`staleAiDraftCannotBeAccepted` |
| 租户隔离与权限 | `everyMotionTableIsTenantScoped`、`otherTenantRowIsInvisibleAndOtherPatientIsForbidden`、`clerkCannotPerformClinicalActions`、`clerkAndOtherTenantUsersCannotReview` |
| 上传安全 | `MotionFileRulesTest` 5 项；引擎 zip 路径/符号链接/炸弹/大小；前端 `uploadQueue.test.mjs` 7 项（真实目录 181 → 65） |
| 日志脱敏 | `timeoutIsRetryableAndMessageHasNoSecrets`、`test_metadata_parser_keeps_technical_fields_only` |
| PDF / 报告 | `pdfRendersChineseWhenFontAvailable`、`therapistViewCarriesVersionsSystemAndFinalScores`、`wrapKeepsCjkWidthAndDropsControlChars` |
| 初评/复评对比与趋势 | `comparisonUsesFinalScoresAndMeasurementErrorOnly`、`compareRejectsDifferentPatients`、`compareUsesFinalWhenReviewedOtherwiseMarksUnreviewed`、`trendKeepsYbtSidesApartAndSkipsPerSideFmsRows` |
| 签署与更正 | `signBlockersCoverStaleActiveUnreviewedAndUnhandledDraft`、`signCreatesReportsLocksAndQueuesPdf`、`amendRequiresSignedStateAndReasonAndSupersedes` |
| 迁移缺口 / 部署 | `deploy/internal/test_*.py`（迁移守卫 adopt/baseline、合同、compose 引擎入口、bash 3.2 可移植性）；桌面 `runtime-tools.test.mjs` |

## 2. 执行命令与结果

| 范围 | 命令 | 结果 |
|---|---|---|
| 引擎格式/静态检查 | `ruff format --check src tests && ruff check src tests` | 通过 |
| 引擎测试（含真实样本） | `MOTION_REAL_SAMPLE_DIR=… MOTION_ENGINE_TOKEN=… PYTHONPATH=src:tests python -m pytest -q` | **364 passed**（含真实样本；未设置 `MOTION_REAL_SAMPLE_DIR` 时 363 passed / 1 skipped） |
| 后端 rehab 模块 | `mvn -B -o -pl yudao-module-rehab -am test -Dtest='**/module/rehab/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` | **158/158** ¹ |
| Web starter（AGENTS.md） | `mvn -B -o -pl yudao-framework/yudao-spring-boot-starter-web -am -Dtest=ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` | EXIT=0，2 个指定测试类全部通过 |
| 前端 ESLint | `eslint src/views/rehab/motion src/api/rehab/motion …` | 0 error |
| 前端类型检查 | `pnpm ts:check`（`vue-tsc --noEmit`，8 GB 堆） | 动作评估相关文件（`views/rehab/motion`、`api/rehab/motion`、`router/modules`）**0 error**；全仓另有 1127 个既有错误（如 `views/system/sms`），不在本次改动范围 |
| 前端内部构建 | `pnpm build:internal` | EXIT=0（`dist-internal` 不入库） |
| 串行上传队列 + 侧别规则 | `node --experimental-strip-types --test tests/motion/*.test.mjs` | 10/10（uploadQueue 7 + sides 3） |
| 部署脚本 | `python3 -m unittest discover -s deploy/internal -p 'test_*.py'` | 57 OK（新增 Spring Bean 名冲突、admin 镜像权限、内部路由、initdb 顺序、025 守卫；3 skipped：真实 MySQL 用例需显式设置 `REHAB_TEST_MYSQL_CONTAINER`，以及清单中无“非纯建表”迁移时的 adopt 拒绝用例） |
| 桌面引导 | `node --test desktop/scripts/runtime-tools.test.mjs` | 4/4 |
| 引擎 wheel | `python -m hatchling build -t wheel` | 包含 `motion/protocols/{fms_v0_2,nasm_ces_v0_2,tuck_jump_items}.json` 与 `motion/service.py` |

¹ 后端 rehab 模块结果：EXIT=0，158 个测试，0 失败 0 错误 0 跳过（含 `trendKeepsYbtSidesApartAndSkipsPerSideFmsRows` 与侧别校验用例）。

## 3. 未覆盖 / 需在目标环境补测

* AI 提供方真实调用（默认关闭；本轮走查全程使用“模板草稿”，护栏与降级已单测）。
* 生产端口 8443 上的部署：联调使用隔离 Compose 项目和 18080/18443（本机 8443 已被其他容器占用），
  因此 preflight 的 `TLS_PORT` 检查与 smoke 的 HTTP→HTTPS 跳转检查按预期失败，其余全部通过。
* 视频授权路径（上传视频、授权后播放）：走查样本未授权视频，只验证了“未授权不上传、不展示”。
* LESS 3 次试次的均值评分：真实样本只有 1 次双脚落地，系统按协议给出 pending，未验证满 3 次的实数据。
* FMS 7 项完整评估与总分：真实样本没有 FMS 动作，FMS 规则只有单元/合成数据覆盖。
* 修订流程（签署后“发起修订”）与报告历史版本、多租户/多机构越权的浏览器级验证（已有后端单测）。
* 桌面版 `desktop/scripts/*.mjs` 同步迁移清单与 adopt 流程。
* bash 3.2（macOS）兼容：`$var` 后紧跟中文/全角字符会被当成变量名一部分，统一写 `${var}`，
  `ShellPortabilityTests` 扫描 `deploy/internal/*.sh` 防回归。

## 4. 容器联调与浏览器走查（2026-09-30）

环境：Docker 28.1.1，隔离 Compose 项目（MySQL/Redis/server/admin/motion-engine），`127.0.0.1:18080/18443`；
引擎镜像 `rehab-motion-engine:1.0.0` 由引擎分支构建；OpenCap 研究账号仅用于换取 API 令牌（只在本机 `.env`）。
浏览器：Chrome + Playwright（CDP），登录含滑块验证码，全部通过界面操作。

| 步骤 | 结果 |
|---|---|
| 迁移 020–025、`check-database.sh`、`smoke-test.sh` | 通过（smoke 跳转检查因端口非 8443 失败，属预期） |
| 新建患者 → 初评 A（上传 OpenCap 导出包） | 自动筛选 181 → 17 个文件串行上传（6.6 MB），164 个跳过并显示原因 |
| Trial 映射 | 单腿蹲（NASM_SLS 左）、双脚落地（LESS 双侧）、Y-Balance（YBT 左，支撑腿）；YBT 选“双侧”被拦截 |
| 处理 / 重新计算 | 会话与 3 个 Trial 质控通过；单腿蹲 2 次重复（驱动信号 pelvis_ty），曲线与分期正常 |
| 人工录入 | 疼痛、YBT 18 次伸够、LESS 17 项、NASM 观察；已分析后修改必须填原因并留痕 |
| 评分 | YBT 综合 左 92.03 / 右 95.08（人工复算一致）；LESS 1 次试次 → pending（当前1）；NASM 仅证据 |
| 审核与签署 | 逐项确认 / 不适用（带原因）→ 接受模板草稿 → 签署；签署前阻断项正确列出 |
| 报告 | 治疗师版与患者版 v1、PDF（中文字体正常）、JSON、SHA-256、签署人与时间 |
| 留痕与审计 | 55 条：上传、映射、疼痛、人工录入前后值、评分审核、草稿接受、签署、PDF 导出、报告查看 |
| 复评 B（数据来源 = OpenCap 会话，基线 A） | 读取云端 9 个 Trial → 生成映射 → 任务经 OpenCap处理中 → 下载结果 → 已完成（约 20 秒）→ 签署 |
| 对比与趋势 | 同一会话两条路径（上传 vs 云端直连）单腿蹲全部运动学指标差值为 0；显示测量误差说明 |

### 走查中发现并修复的缺陷

| # | 现象 | 根因 | 修复与防回归 |
|---|---|---|---|
| 1 | 云端拉取缺模型/元数据 | 真实会话中 `.osim` 与 `sessionMetadata.yaml` 挂在 neutral Trial | 引擎按会话清单补齐；单测 |
| 2 | server 启动崩溃循环 | Spring Bean 名 `fileMapper` 与既有模块冲突 | 改名 `motionFileMapper`；`test_spring_bean_names.py` 扫描全仓 Bean 名 |
| 3 | admin 页面 403 | 构建机 umask 导致静态文件 600/700 | Dockerfile.admin 统一权限；`test_admin_image.py` |
| 4 | 评估详情 404 | 内部构建只加载 `remaining.internal.ts`，缺少详情路由 | 补路由；`test_internal_routes.py` 要求与 `remaining.ts` 的动作评估路由一致 |
| 5 | 处理任务卡在“解析” | 024 的 `creator/updater` NOT NULL，后台线程自动填充写 NULL | 新迁移 025 改为可空（不改已执行的 024）；迁移守卫测试 |
| 6 | YBT 选“双侧”可保存，引擎在规则阶段才拒绝 | 主系统未按测试校验侧别 | 后端保存时校验 + 前端只给合法选项；Java/TS/引擎三方侧别表一致性测试 |
| 7 | 结果协议版本缺 YBT_LQ / LESS | 协议版本只来自协议文件 | 引擎按请求体系输出，YBT/LESS 标注 `tests_registry` 来源；单测 |

另修复：Trial 面板 OpenCap 字段映射、initdb 脚本排序（`zz-` 前缀）、单脚落地不再建议 LESS、
模板草稿患者摘要措辞（被接受后原样进入已签署报告，原文“审核确认后为您解读”在签署后不成立）。
