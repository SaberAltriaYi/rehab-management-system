# 变更文件清单

主仓库分支 `feature/motion-assessment`（基于 `49b3e40`）。M = 修改，A = 新增。

## 部署 / 迁移

* M `deploy/internal/README.md`
* M `deploy/internal/docker-compose.yml`
* A `deploy/internal/init-incremental-migrations.sh`
* M `deploy/internal/migrate.sh`
* M `deploy/internal/migrations.manifest`
* M `deploy/internal/preflight.sh`
* M `deploy/internal/test_business_schema_contract.py`
* M `deploy/internal/test_migration_guard.py`
* A `deploy/internal/test_motion_engine_compose.py`
* M `desktop/scripts/build-sanitized-bootstrap.mjs`
* M `desktop/scripts/runtime-tools.mjs`
* M `desktop/scripts/runtime-tools.test.mjs`
* A `sql/mysql/rehab-motion-assessment-v1.sql`
* M `yudao-server/src/main/resources/application-internal.yaml`

## 后端 Java（yudao-module-rehab）

* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/RehabMotionController.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionAiReviewReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionAmendReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionAssessmentRespVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionFileRespVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionManualInputsReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionOpencapReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionPageReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionProcessReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionSaveReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionScoreReviewReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionSignReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionTaskRespVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionTrialManualReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/controller/admin/motion/vo/RehabMotionTrialSaveReqVO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionAiDraftDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionAssessmentDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionFileDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionManualEditDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionMetricDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionProtocolVersionDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionReportDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionRuleResultDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionScoreDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionTaskDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/dataobject/motion/RehabMotionTrialDO.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionAiDraftMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionAssessmentMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionFileMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionManualEditMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionMetricMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionProtocolVersionMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionReportMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionRuleResultMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionScoreMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionTaskMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/dal/mysql/motion/RehabMotionTrialMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/enums/RehabMotionConstants.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/enums/RehabMotionErrorCodeConstants.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionEngineRequestBuilder.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionFileRules.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionLabels.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionResultMapper.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionScoreRules.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionStorage.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/MotionZipReader.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionAccess.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionAssessmentService.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionResultWriter.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionReviewService.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/ai/MotionAiFallback.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/ai/MotionAiOutputValidator.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/ai/MotionAiPayloadBuilder.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/ai/MotionAiPrompts.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/ai/RehabMotionAiService.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/engine/HttpMotionEngineClient.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/engine/MotionEngineClient.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/engine/MotionEngineException.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/report/MotionComparator.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/report/MotionPdfRenderer.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/report/MotionReportBuilder.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/report/RehabMotionReportService.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/task/MotionTaskException.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/task/MotionTaskStates.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/task/RehabMotionPipelineSteps.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/task/RehabMotionTaskService.java`
* A `yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/motion/task/RehabMotionWorker.java`

## 后端测试与夹具

* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/MotionFileRulesTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/MotionResultMapperTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/MotionScoreRulesTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/MotionTrialConditionTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionAccessAndTaskTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/RehabMotionReviewServiceTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/ai/MotionAiGuardrailTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/engine/HttpMotionEngineClientTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/report/MotionReportPdfCompareTest.java`
* A `yudao-module-rehab/src/test/java/cn/iocoder/yudao/module/rehab/service/motion/task/MotionTaskStatesTest.java`
* A `yudao-module-rehab/src/test/resources/motion/engine-result-sample.json`
* A `yudao-module-rehab/src/test/resources/motion/upload-policy.json`

## 前端（yudao-ui-admin-vue3-app）

* A `yudao-ui/yudao-ui-admin-vue3-app/src/api/rehab/motion/index.ts`
* M `yudao-ui/yudao-ui-admin-vue3-app/src/router/modules/remaining.ts`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/MotionForm.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/AuditPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/ComparePanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/CurvePanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/EvidenceView.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/LineChart.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/ManualPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/ReportPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/ResultPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/ReviewPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/TaskPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/TrendPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/TrialPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/components/UploadPanel.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/constants.ts`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/detail/index.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/index.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/motion/uploadQueue.ts`
* M `yudao-ui/yudao-ui-admin-vue3-app/src/views/rehab/patient/detail/index.vue`
* A `yudao-ui/yudao-ui-admin-vue3-app/tests/motion/opencap-listing.txt`
* A `yudao-ui/yudao-ui-admin-vue3-app/tests/motion/uploadQueue.test.mjs`

## 文档

* M `CHANGELOG.md`
* A `docs/motion-assessment/README.md`
* A `docs/motion-assessment/official-alignment.md`
* A `docs/motion-assessment/open-questions.md`
* A `docs/motion-assessment/testing.md`
* A `docs/motion-assessment/security.md`
* A `docs/motion-assessment/deployment.md`
* A `docs/motion-assessment/clinical-validation-plan.md`
* A `docs/motion-assessment/changed-files.md`

## 分析引擎仓库 `rehab-biomechanics-engine`（分支 `feature/motion-assessment`，提交 `fc454035`）

* M `src/rehab_biomechanics/io/osim.py`（耦合坐标主/从轴识别，修复膝关节被误判为平移轴）
* A `src/rehab_biomechanics/motion/`（解析、QC、阶段、指标、FMS/NASM/YBT/TJA/LESS 评分、协议 JSON、HTTP 服务、OpenCap 客户端）
* A `tests/motion_synth.py`、`tests/test_motion_engine.py`、`tests/test_motion_scoring.py`、`tests/test_motion_service.py`、`tests/test_motion_real_sample.py`
* A `docs/motion-assessment.md`
