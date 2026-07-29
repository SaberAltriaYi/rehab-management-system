package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 康复提醒事件常量
 */
public interface RehabAlertConstants {

    String TYPE_REASSESSMENT_DUE = "reassessment_due";
    String TYPE_LOW_ADHERENCE = "low_adherence";
    String TYPE_PAIN_UPGRADE = "pain_upgrade";
    String TYPE_PLAN_DUE = "plan_due";
    String TYPE_REPORT_READY = "report_ready";
    String TYPE_HIGH_RISK_UNRESOLVED = "high_risk_unresolved";

    String SEVERITY_INFO = "info";
    String SEVERITY_WARNING = "warning";
    String SEVERITY_HIGH = "high";

    String STATUS_ACTIVE = "active";
    String STATUS_ACKNOWLEDGED = "acknowledged";
    String STATUS_RESOLVED = "resolved";
    String STATUS_IGNORED = "ignored";

    String CREATED_FROM_AUTO = "auto_engine";
    String CREATED_FROM_MANUAL = "manual";

    List<String> ALERT_TYPES = Arrays.asList(
            TYPE_REASSESSMENT_DUE,
            TYPE_LOW_ADHERENCE,
            TYPE_PAIN_UPGRADE,
            TYPE_PLAN_DUE,
            TYPE_REPORT_READY,
            TYPE_HIGH_RISK_UNRESOLVED
    );

    List<String> ALERT_STATUS_LIST = Arrays.asList(STATUS_ACTIVE, STATUS_ACKNOWLEDGED, STATUS_RESOLVED, STATUS_IGNORED);
}
