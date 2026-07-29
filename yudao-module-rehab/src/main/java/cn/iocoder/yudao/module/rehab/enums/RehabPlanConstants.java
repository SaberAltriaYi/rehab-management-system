package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 计划执行闭环常量
 */
public interface RehabPlanConstants {

    String PLAN_TYPE_REHAB = "rehab";
    String PLAN_TYPE_MAINTENANCE = "maintenance";
    String PLAN_TYPE_RETURN_TO_SPORT = "return_to_sport";
    String PLAN_TYPE_HOME_PROGRAM = "home_program";

    String PLAN_STATUS_DRAFT = "draft";
    String PLAN_STATUS_ACTIVE = "active";
    String PLAN_STATUS_PAUSED = "paused";
    String PLAN_STATUS_COMPLETED = "completed";
    String PLAN_STATUS_CLOSED = "closed";

    String INTENSITY_LOW = "low";
    String INTENSITY_MEDIUM = "medium";
    String INTENSITY_HIGH = "high";

    String TASK_STATUS_ACTIVE = "active";
    String TASK_STATUS_PAUSED = "paused";
    String TASK_STATUS_COMPLETED = "completed";
    String TASK_STATUS_DISABLED = "disabled";

    String TASK_MODULE_MOBILITY = "mobility";
    String TASK_MODULE_STABILITY = "stability";
    String TASK_MODULE_CONTROL = "control";
    String TASK_MODULE_INTEGRATION = "integration";
    String TASK_MODULE_LOAD = "load";
    String TASK_MODULE_BREATHING = "breathing";
    String TASK_MODULE_BALANCE = "balance";

    String EXECUTION_HOME = "home";
    String EXECUTION_CLINIC = "clinic";
    String EXECUTION_BOTH = "both";

    String SCHEDULE_DAILY = "daily";
    String SCHEDULE_WEEKLY = "weekly";
    String SCHEDULE_CUSTOM = "custom";

    String CHECKIN_ROLE_PATIENT = "patient";
    String CHECKIN_ROLE_THERAPIST = "therapist";
    String CHECKIN_ROLE_CLERK = "clerk";

    String COMPLETION_COMPLETED = "completed";
    String COMPLETION_PARTIAL = "partial";
    String COMPLETION_SKIPPED = "skipped";
    String COMPLETION_PAIN_STOP = "pain_stop";

    String PAIN_TREND_IMPROVED = "improved";
    String PAIN_TREND_STABLE = "stable";
    String PAIN_TREND_WORSENED = "worsened";
    String PAIN_TREND_INSUFFICIENT = "insufficient_data";

    String PROGRESS_IMPROVED = "improved";
    String PROGRESS_SLIGHTLY_IMPROVED = "slightly_improved";
    String PROGRESS_STABLE = "stable";
    String PROGRESS_WORSENED = "worsened";
    String PROGRESS_INSUFFICIENT = "insufficient_data";

    String TRIGGER_TIME_DUE = "time_due";
    String TRIGGER_PAIN_UPGRADE = "pain_upgrade";
    String TRIGGER_LOW_ADHERENCE = "low_adherence";
    String TRIGGER_STAGE_END = "stage_end";
    String TRIGGER_TARGET_NOT_MET = "target_not_met";
    String TRIGGER_TARGET_MET = "target_met";

    String TRIGGER_LEVEL_LOW = "low";
    String TRIGGER_LEVEL_MEDIUM = "medium";
    String TRIGGER_LEVEL_HIGH = "high";

    String TRIGGER_STATUS_PENDING = "pending";
    String TRIGGER_STATUS_ACKNOWLEDGED = "acknowledged";
    String TRIGGER_STATUS_CONVERTED = "converted_to_reassessment";
    String TRIGGER_STATUS_DISMISSED = "dismissed";

    List<String> TASK_STATUS_LIST = Arrays.asList(TASK_STATUS_ACTIVE, TASK_STATUS_PAUSED, TASK_STATUS_COMPLETED, TASK_STATUS_DISABLED);
    List<String> PLAN_STATUS_LIST = Arrays.asList(PLAN_STATUS_DRAFT, PLAN_STATUS_ACTIVE, PLAN_STATUS_PAUSED, PLAN_STATUS_COMPLETED, PLAN_STATUS_CLOSED);
    List<String> TRIGGER_STATUS_LIST = Arrays.asList(TRIGGER_STATUS_PENDING, TRIGGER_STATUS_ACKNOWLEDGED, TRIGGER_STATUS_CONVERTED, TRIGGER_STATUS_DISMISSED);
    List<String> TRIGGER_TYPES = Arrays.asList(TRIGGER_TIME_DUE, TRIGGER_PAIN_UPGRADE, TRIGGER_LOW_ADHERENCE, TRIGGER_STAGE_END, TRIGGER_TARGET_NOT_MET, TRIGGER_TARGET_MET);

}
