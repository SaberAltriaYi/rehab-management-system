package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 康复通知中心常量
 */
public interface RehabNotificationConstants {

    String TARGET_THERAPIST = "therapist";
    String TARGET_PATIENT = "patient";
    String TARGET_ADMIN = "admin";
    String TARGET_SYSTEM = "system";

    String RELATED_PLAN = "plan";
    String RELATED_CHECKIN = "checkin";
    String RELATED_TRIGGER = "trigger";
    String RELATED_REPORT = "report";
    String RELATED_ASSESSMENT = "assessment";
    String RELATED_ALERT = "alert";
    String RELATED_SYSTEM = "system";

    String TYPE_TASK_REMINDER = "task_reminder";
    String TYPE_REASSESSMENT_DUE = "reassessment_due";
    String TYPE_LOW_ADHERENCE = "low_adherence";
    String TYPE_PAIN_ALERT = "pain_alert";
    String TYPE_REPORT_READY = "report_ready";
    String TYPE_PLAN_UPDATED = "plan_updated";
    String TYPE_TRIGGER_CREATED = "trigger_created";
    String TYPE_SYSTEM_NOTICE = "system_notice";

    String SEVERITY_INFO = "info";
    String SEVERITY_WARNING = "warning";
    String SEVERITY_HIGH = "high";

    String DELIVERY_WEB = "web";
    String DELIVERY_APP_ADMIN = "app_admin";
    String DELIVERY_APP_PATIENT = "app_patient";
    String DELIVERY_MULTI = "multi";

    String READ_UNREAD = "unread";
    String READ_READ = "read";

    String SEND_PENDING = "pending";
    String SEND_SENT = "sent";
    String SEND_FAILED = "failed";
    String SEND_CANCELED = "canceled";

    List<String> TARGET_TYPES = Arrays.asList(TARGET_THERAPIST, TARGET_PATIENT, TARGET_ADMIN, TARGET_SYSTEM);
    List<String> READ_STATUS_LIST = Arrays.asList(READ_UNREAD, READ_READ);
    List<String> SEND_STATUS_LIST = Arrays.asList(SEND_PENDING, SEND_SENT, SEND_FAILED, SEND_CANCELED);
}
