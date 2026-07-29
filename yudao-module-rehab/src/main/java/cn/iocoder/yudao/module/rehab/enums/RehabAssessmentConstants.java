package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估记录常量
 */
public interface RehabAssessmentConstants {

    String TYPE_STATIC_ASSESSMENT = "static_assessment";
    String TYPE_BODY_COMPOSITION = "body_composition";
    String TYPE_NASM_CES = "nasm_ces";
    String TYPE_SFMA = "sfma";
    String TYPE_FMS = "fms";
    String TYPE_YBT = "ybt";
    String TYPE_OPENCAP = "opencap";
    String TYPE_OBSERVATION_ONLY = "observation_only";
    String TYPE_COMPREHENSIVE_ASSESSMENT = "comprehensive_assessment";

    List<String> ASSESSMENT_TYPES_V2 = Arrays.asList(
            TYPE_STATIC_ASSESSMENT,
            TYPE_BODY_COMPOSITION,
            TYPE_NASM_CES,
            TYPE_SFMA,
            TYPE_FMS,
            TYPE_YBT,
            TYPE_OPENCAP,
            TYPE_OBSERVATION_ONLY,
            TYPE_COMPREHENSIVE_ASSESSMENT
    );

    /**
     * 兼容历史数据的旧评估类型（仅用于识别与迁移，不允许新写入）
     */
    String TYPE_INITIAL = "initial";
    String TYPE_FOLLOWUP = "followup";
    String TYPE_DISCHARGE = "discharge";
    String TYPE_SPECIAL_RETEST = "special_retest";
    String TYPE_RETURN_TO_SPORT = "return_to_sport";

    List<String> LEGACY_ASSESSMENT_TYPES = Arrays.asList(
            TYPE_INITIAL,
            TYPE_FOLLOWUP,
            TYPE_DISCHARGE,
            TYPE_SPECIAL_RETEST,
            TYPE_RETURN_TO_SPORT
    );

    String STATUS_DRAFT = "draft";
    String STATUS_COMPLETED = "completed";
    String STATUS_REVIEWED = "reviewed";
    String STATUS_ARCHIVED = "archived";

    String LOCATION_CLINIC = "clinic";
    String LOCATION_REMOTE = "remote";
    String LOCATION_FIELD = "field";

    String RAW_INPUT_COMPLETE = "complete";
    String RAW_INPUT_PARTIAL = "partial";
    String RAW_INPUT_MISSING = "missing_items";

    String QUALITY_A = "A";
    String QUALITY_B = "B";
    String QUALITY_C = "C";
    String QUALITY_D = "D";

    String CONFIDENCE_HIGH = "high";
    String CONFIDENCE_MEDIUM = "medium";
    String CONFIDENCE_LOW = "low";

    String MODULE_STATIC = "static";
    String MODULE_BODY_COMP = "body_comp";
    String MODULE_NASM = "nasm";
    String MODULE_SFMA = "sfma";
    String MODULE_FMS = "fms";
    String MODULE_YBT = "ybt";
    String MODULE_OPENCAP = "opencap";
    String MODULE_OBSERVATION = "observation";
    String MODULE_OUTCOME_SCALE = "outcome_scale";
    String MODULE_COMPREHENSIVE = "comprehensive";

    List<String> MODULE_TYPES = Arrays.asList(
            MODULE_STATIC,
            MODULE_BODY_COMP,
            MODULE_NASM,
            MODULE_SFMA,
            MODULE_FMS,
            MODULE_YBT,
            MODULE_OPENCAP,
            MODULE_OBSERVATION,
            MODULE_OUTCOME_SCALE,
            MODULE_COMPREHENSIVE
    );

    Map<String, String> ASSESSMENT_TYPE_MODULE_TYPE_MAPPING = buildAssessmentTypeModuleTypeMapping();

    String MODULE_STATUS_NOT_STARTED = "not_started";
    String MODULE_STATUS_PARTIAL = "partial";
    String MODULE_STATUS_COMPLETED = "completed";
    String MODULE_STATUS_FAILED_PARSE = "failed_parse";

    String MODULE_SOURCE_MANUAL = "manual";
    String MODULE_SOURCE_UPLOAD = "upload";
    String MODULE_SOURCE_PARSER = "parser";
    String MODULE_SOURCE_AI_ENHANCED = "ai_enhanced";

    static boolean isValidAssessmentTypeV2(String assessmentType) {
        return ASSESSMENT_TYPES_V2.contains(assessmentType);
    }

    static String resolvePrimaryModuleType(String assessmentType) {
        return ASSESSMENT_TYPE_MODULE_TYPE_MAPPING.get(assessmentType);
    }

    static Map<String, String> buildAssessmentTypeModuleTypeMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put(TYPE_STATIC_ASSESSMENT, MODULE_STATIC);
        mapping.put(TYPE_BODY_COMPOSITION, MODULE_BODY_COMP);
        mapping.put(TYPE_NASM_CES, MODULE_NASM);
        mapping.put(TYPE_SFMA, MODULE_SFMA);
        mapping.put(TYPE_FMS, MODULE_FMS);
        mapping.put(TYPE_YBT, MODULE_YBT);
        mapping.put(TYPE_OPENCAP, MODULE_OPENCAP);
        mapping.put(TYPE_OBSERVATION_ONLY, MODULE_OBSERVATION);
        mapping.put(TYPE_COMPREHENSIVE_ASSESSMENT, MODULE_COMPREHENSIVE);
        return Collections.unmodifiableMap(mapping);
    }

}
