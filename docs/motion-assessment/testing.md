# 测试矩阵与结果

执行日期：2026-09-30，环境：macOS（JDK 17 运行 Java 8 目标代码、Maven 离线、Node 22.15、Python 3.13）。
Docker 守护进程未启动，容器级联调未在本轮执行（见“未覆盖”）。

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
| 引擎测试（含真实样本） | `MOTION_REAL_SAMPLE_DIR=… MOTION_ENGINE_TOKEN=… PYTHONPATH=src:tests python -m pytest -q` | **362 passed**（沙箱无真实样本：361 passed / 1 skipped） |
| 后端 rehab 模块 | `mvn -B -o -pl yudao-module-rehab -am test -Dtest='**/module/rehab/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` | **157/157** ¹ |
| Web starter（AGENTS.md） | `mvn -B -o -pl yudao-framework/yudao-spring-boot-starter-web -am -Dtest=ApiAccessLogInterceptorTest,GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test` | EXIT=0，2 个指定测试类全部通过 |
| 前端 ESLint | `eslint src/views/rehab/motion src/api/rehab/motion …` | 0 error |
| 前端类型检查 | `vue-tsc --noEmit` | 0 error |
| 前端内部构建 | `pnpm build:internal` | EXIT=0（`dist-internal` 不入库） |
| 串行上传队列 | `node --experimental-strip-types --test tests/motion/uploadQueue.test.mjs` | 7/7 |
| 部署脚本 | `python3 -m unittest discover -s deploy/internal -p 'test_*.py'` | 47 OK（3 skipped：真实 MySQL 用例需显式设置 `REHAB_TEST_MYSQL_CONTAINER`，以及清单中无“非纯建表”迁移时的 adopt 拒绝用例） |
| 桌面引导 | `node --test desktop/scripts/runtime-tools.test.mjs` | 4/4 |
| 引擎 wheel | `python -m hatchling build -t wheel` | 包含 `motion/protocols/{fms_v0_2,nasm_ces_v0_2,tuck_jump_items}.json` 与 `motion/service.py` |

¹ 后端 rehab 模块结果：EXIT=0，30 个测试类 157 个测试，0 失败 0 错误 0 跳过（含新增 `trendKeepsYbtSidesApartAndSkipsPerSideFmsRows`）。

## 3. 未覆盖 / 需在目标环境补测

* Docker 守护进程未启动：`docker compose --profile motion up`、引擎容器健康检查、MySQL 实库执行 024 与
  `migrate.sh adopt` 的端到端（逻辑已由 `test_migration_guard.py` 覆盖，实库用例在无 MySQL 时跳过）。
* 真实 OpenCap 云端会话拉取（需要 OpenCap 账号令牌）。
* AI 提供方真实调用（默认关闭；护栏与降级已单测）。
* 浏览器端手工走查：新建 → 上传 → 映射 → 录入 → 处理 → 审核 → 签署 → PDF → 对比/趋势。
