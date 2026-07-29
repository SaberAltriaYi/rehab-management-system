package cn.iocoder.yudao.module.rehab.enums;

/**
 * 患者操作日志类型常量
 */
public interface RehabOperationTypeConstants {

    String CREATE = "create";
    String UPDATE = "update";
    String BIND_CRM = "bind_crm";
    String UNBIND_CRM = "unbind_crm";
    String ASSIGN = "assign";
    String TRANSFER = "transfer";
    String STAGE_CHANGE = "stage_change";
    String ARCHIVE = "archive";
    String ASSESSMENT_CREATE = "assessment_create";
    String ASSESSMENT_UPDATE = "assessment_update";
    String ASSESSMENT_PARSE = "assessment_parse";
    String GENERATE_REPORT = "generate_report";
    String REPORT_REVIEW = "report_review";
    String REPORT_APPROVE = "report_approve";
    String REPORT_EXPORT = "report_export";
    String REPORT_LOCK = "report_lock";
    String REPORT_UNLOCK = "report_unlock";
    String PLAN_CREATE = "plan_create";
    String PLAN_UPDATE = "plan_update";
    String PLAN_ACTIVATE = "plan_activate";
    String PLAN_PAUSE = "plan_pause";
    String PLAN_COMPLETE = "plan_complete";
    String PLAN_COPY = "plan_copy";
    String PLAN_TASK_ADD = "plan_task_add";
    String PLAN_TASK_EDIT = "plan_task_edit";
    String PLAN_TRIGGER_REASSESSMENT = "plan_trigger_reassessment";
    String CHECKIN_CREATE = "checkin_create";
    String PROGRESS_RECALCULATE = "progress_recalculate";
    String TRIGGER_HANDLE = "trigger_handle";
    String ALERT_REFRESH = "alert_refresh";
    String ALERT_ACKNOWLEDGE = "alert_acknowledge";
    String ALERT_RESOLVE = "alert_resolve";
    String ALERT_IGNORE = "alert_ignore";
    String NOTIFICATION_CREATE = "notification_create";
    String NOTIFICATION_READ = "notification_read";
    String NOTIFICATION_READ_ALL = "notification_read_all";
    String NOTIFICATION_DELETE = "notification_delete";

    String AI_GENERATE = "ai_generate";
    String AI_FALLBACK = "ai_fallback";
    String AI_ACCEPT = "ai_accept";
    String AI_EDIT = "ai_edit";
    String AI_REJECT = "ai_reject";
    String AI_REGENERATE = "ai_regenerate";
    String AI_PUBLISH_PATIENT = "ai_publish_patient";
    String AI_CONFIG_UPDATE = "ai_config_update";
    String AI_TEMPLATE_ENABLE = "ai_template_enable";
    String AI_TEMPLATE_SET_DEFAULT = "ai_template_set_default";

}
