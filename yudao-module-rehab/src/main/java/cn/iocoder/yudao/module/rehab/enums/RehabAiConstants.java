package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Rehab AI 常量
 */
public interface RehabAiConstants {

    String JOB_TYPE_ASSESSMENT_INTERPRETATION = "assessment_interpretation";
    String JOB_TYPE_REPORT_SUMMARY = "report_summary";
    String JOB_TYPE_RISK_EXPLANATION = "risk_explanation";
    String JOB_TYPE_PLAN_DRAFT_GENERATION = "plan_draft_generation";
    String JOB_TYPE_FOLLOWUP_MESSAGE_GENERATION = "followup_message_generation";
    String JOB_TYPE_PROGRESS_SUMMARY = "progress_summary";

    String JOB_STATUS_PENDING = "pending";
    String JOB_STATUS_SUCCESS = "success";
    String JOB_STATUS_FAILED = "failed";
    String JOB_STATUS_FALLBACK_USED = "fallback_used";
    String JOB_STATUS_REVIEWED = "reviewed";
    String JOB_STATUS_REJECTED = "rejected";
    String JOB_STATUS_ACCEPTED = "accepted";

    String OUTPUT_TYPE_THERAPIST_SUMMARY = "therapist_summary";
    String OUTPUT_TYPE_PATIENT_SUMMARY = "patient_summary";
    String OUTPUT_TYPE_ADMIN_SUMMARY = "admin_summary";
    String OUTPUT_TYPE_RISK_EXPLANATION = "risk_explanation";
    String OUTPUT_TYPE_PLAN_DRAFT = "plan_draft";
    String OUTPUT_TYPE_FOLLOWUP_MESSAGE = "followup_message";
    String OUTPUT_TYPE_REPORT_SECTION = "report_section";
    String OUTPUT_TYPE_PROGRESS_SUMMARY = "progress_summary";

    String TARGET_OBJECT_ASSESSMENT = "assessment";
    String TARGET_OBJECT_REPORT = "report";
    String TARGET_OBJECT_PLAN = "plan";
    String TARGET_OBJECT_PROGRESS = "progress";
    String TARGET_OBJECT_NOTIFICATION = "notification";
    String TARGET_OBJECT_TRIGGER = "trigger";
    String TARGET_OBJECT_ALERT = "alert";
    String TARGET_OBJECT_PATIENT = "patient";

    String SAFETY_STATUS_PASSED = "passed";
    String SAFETY_STATUS_DOWNGRADED = "downgraded";
    String SAFETY_STATUS_BLOCKED = "blocked";

    String REVIEW_STATUS_PENDING = "pending";
    String REVIEW_STATUS_ACCEPTED = "accepted";
    String REVIEW_STATUS_EDITED = "edited";
    String REVIEW_STATUS_REJECTED = "rejected";

    String BUNDLE_TYPE_INTEGRATED_SUMMARY = "integrated_summary";
    String BUNDLE_TYPE_PLAN_BUNDLE = "plan_bundle";
    String BUNDLE_TYPE_FOLLOWUP_BUNDLE = "followup_bundle";

    String BUNDLE_STATUS_DRAFT = "draft";
    String BUNDLE_STATUS_REVIEWED = "reviewed";
    String BUNDLE_STATUS_ADOPTED = "adopted";
    String BUNDLE_STATUS_DISCARDED = "discarded";

    String TEMPLATE_SCOPE_ASSESSMENT = "assessment";
    String TEMPLATE_SCOPE_REPORT = "report";
    String TEMPLATE_SCOPE_PLAN = "plan";
    String TEMPLATE_SCOPE_FOLLOWUP = "followup";
    String TEMPLATE_SCOPE_PATIENT_SUMMARY = "patient_summary";
    String TEMPLATE_SCOPE_RISK = "risk";
    String TEMPLATE_SCOPE_PROGRESS = "progress";

    String ROLE_SCOPE_THERAPIST = "therapist";
    String ROLE_SCOPE_PATIENT = "patient";
    String ROLE_SCOPE_ADMIN = "admin";

    String CONFIG_SCOPE_GLOBAL = "global";
    String CONFIG_SCOPE_ORGANIZATION = "organization";
    String CONFIG_SCOPE_THERAPIST = "therapist";

    String PROMPT_STYLE_CONCISE = "concise";
    String PROMPT_STYLE_STANDARD = "standard";
    String PROMPT_STYLE_DETAILED = "detailed";

    String SAFETY_MODE_STANDARD = "standard";
    String SAFETY_MODE_STRICT = "strict";

    String REVIEW_ACTION_ACCEPT = "accept";
    String REVIEW_ACTION_EDIT = "edit";
    String REVIEW_ACTION_REJECT = "reject";
    String REVIEW_ACTION_REGENERATE = "regenerate";

    List<String> JOB_TYPE_LIST = Arrays.asList(
            JOB_TYPE_ASSESSMENT_INTERPRETATION,
            JOB_TYPE_REPORT_SUMMARY,
            JOB_TYPE_RISK_EXPLANATION,
            JOB_TYPE_PLAN_DRAFT_GENERATION,
            JOB_TYPE_FOLLOWUP_MESSAGE_GENERATION,
            JOB_TYPE_PROGRESS_SUMMARY
    );

    List<String> JOB_STATUS_LIST = Arrays.asList(
            JOB_STATUS_PENDING, JOB_STATUS_SUCCESS, JOB_STATUS_FAILED,
            JOB_STATUS_FALLBACK_USED, JOB_STATUS_REVIEWED,
            JOB_STATUS_REJECTED, JOB_STATUS_ACCEPTED
    );

    List<String> REVIEW_STATUS_LIST = Arrays.asList(
            REVIEW_STATUS_PENDING, REVIEW_STATUS_ACCEPTED,
            REVIEW_STATUS_EDITED, REVIEW_STATUS_REJECTED
    );

    List<String> SAFETY_STATUS_LIST = Arrays.asList(
            SAFETY_STATUS_PASSED, SAFETY_STATUS_DOWNGRADED, SAFETY_STATUS_BLOCKED
    );
}
