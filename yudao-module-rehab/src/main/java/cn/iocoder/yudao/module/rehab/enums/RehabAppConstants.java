package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Rehab 小程序常量
 */
public interface RehabAppConstants {

    String BIND_TYPE_SELF = "self";
    String BIND_TYPE_CAREGIVER = "caregiver";
    String BIND_TYPE_IMPORTED = "imported";

    String BIND_STATUS_ACTIVE = "active";
    String BIND_STATUS_PENDING = "pending";
    String BIND_STATUS_DISABLED = "disabled";

    String FOLLOWUP_TYPE_FOLLOWUP = "followup";
    String FOLLOWUP_TYPE_REMINDER = "reminder";
    String FOLLOWUP_TYPE_PAIN_FEEDBACK = "pain_feedback";
    String FOLLOWUP_TYPE_ADHERENCE_COMMENT = "adherence_comment";

    String FOLLOWUP_VISIBILITY_INTERNAL = "internal";
    String FOLLOWUP_VISIBILITY_PATIENT = "patient_visible";

    String NOTIFICATION_TASK_REMINDER = "task_reminder";
    String NOTIFICATION_REASSESSMENT_DUE = "reassessment_due";
    String NOTIFICATION_PROGRESS_UPDATE = "progress_update";
    String NOTIFICATION_RISK_NOTICE = "risk_notice";

    String NOTIFICATION_READ = "read";
    String NOTIFICATION_UNREAD = "unread";
    String NOTIFICATION_SENT = "sent";
    String NOTIFICATION_PENDING = "pending";

    List<String> BIND_STATUS_LIST = Arrays.asList(BIND_STATUS_ACTIVE, BIND_STATUS_PENDING, BIND_STATUS_DISABLED);
    List<String> FOLLOWUP_VISIBILITY_LIST = Arrays.asList(FOLLOWUP_VISIBILITY_INTERNAL, FOLLOWUP_VISIBILITY_PATIENT);
}
