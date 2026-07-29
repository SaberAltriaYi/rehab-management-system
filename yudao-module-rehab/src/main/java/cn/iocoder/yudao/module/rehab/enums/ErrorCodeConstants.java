package cn.iocoder.yudao.module.rehab.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Rehab 错误码枚举类
 *
 * rehab 模块，使用 1-011-000-000 段
 */
public interface ErrorCodeConstants {

    ErrorCode PATIENT_NOT_EXISTS = new ErrorCode(1_011_000_000, "患者不存在");
    ErrorCode PATIENT_NO_PERMISSION = new ErrorCode(1_011_000_001, "无权限访问该患者");
    ErrorCode PATIENT_CAN_NOT_DELETE = new ErrorCode(1_011_000_002, "患者存在 episode/评估/报告记录，不允许删除，请先归档");
    ErrorCode PATIENT_DUPLICATE_SUSPECTED = new ErrorCode(1_011_000_003, "存在可能重复建档患者");

    ErrorCode CRM_BINDING_NOT_EXISTS = new ErrorCode(1_011_001_000, "CRM 绑定记录不存在");
    ErrorCode CRM_BINDING_CONFLICT = new ErrorCode(1_011_001_001, "CRM 客户已绑定其他患者，发生冲突");

    ErrorCode ASSIGNMENT_PRIMARY_ALREADY_EXISTS = new ErrorCode(1_011_002_000, "该患者已存在主责治疗师");
    ErrorCode ASSIGNMENT_NOT_EXISTS = new ErrorCode(1_011_002_001, "治疗师分配记录不存在");
    ErrorCode ASSIGNMENT_TRANSFER_TARGET_SAME = new ErrorCode(1_011_002_002, "转交目标治疗师不能与当前主责治疗师相同");
    ErrorCode ASSIGNMENT_ROLE_TYPE_INVALID = new ErrorCode(1_011_002_003, "分配角色类型不合法");

    ErrorCode EPISODE_NOT_EXISTS = new ErrorCode(1_011_003_000, "episode 不存在");
    ErrorCode EPISODE_ACTIVE_ALREADY_EXISTS = new ErrorCode(1_011_003_001, "该患者已存在进行中的 episode");
    ErrorCode EPISODE_STAGE_INVALID = new ErrorCode(1_011_003_002, "episode 阶段不合法");

    ErrorCode ASSESSMENT_NOT_EXISTS = new ErrorCode(1_011_004_000, "评估记录不存在");
    ErrorCode ASSESSMENT_PATIENT_EPISODE_MISMATCH = new ErrorCode(1_011_004_001, "评估记录关联的患者与 episode 不匹配");
    ErrorCode ASSESSMENT_ALREADY_ARCHIVED = new ErrorCode(1_011_004_002, "评估已归档，不允许编辑");
    ErrorCode ASSESSMENT_MODULE_TYPE_INVALID = new ErrorCode(1_011_004_003, "评估模块类型不合法");
    ErrorCode ASSESSMENT_CAN_NOT_DELETE = new ErrorCode(1_011_004_004, "评估已关联报告，不允许删除");
    ErrorCode ASSESSMENT_ATTACHMENT_NOT_FOUND = new ErrorCode(1_011_004_005, "评估附件不存在");
    ErrorCode ASSESSMENT_TYPE_INVALID = new ErrorCode(1_011_004_006, "评估类型不合法");
    ErrorCode ASSESSMENT_SFMA_PROTOCOL_INVALID = new ErrorCode(1_011_004_007, "SFMA 原书流程数据不合法：{}");
    ErrorCode ASSESSMENT_ATTACHMENT_TYPE_INVALID = new ErrorCode(1_011_004_008,
            "评估附件类型不支持，仅允许 PDF、图片、Office、CSV 和 TXT 文件");
    ErrorCode ASSESSMENT_ATTACHMENT_SIZE_EXCEEDED = new ErrorCode(1_011_004_009, "评估附件不能超过 16 MB");
    ErrorCode ASSESSMENT_ATTACHMENT_STORE_FAILED = new ErrorCode(1_011_004_010, "评估附件保存失败");

    ErrorCode REPORT_NOT_EXISTS = new ErrorCode(1_011_005_000, "报告不存在");
    ErrorCode REPORT_DOCX_NOT_EXISTS = new ErrorCode(1_011_005_001, "报告 DOCX 文件不存在，请重新生成");
    ErrorCode REPORT_PDF_NOT_EXISTS = new ErrorCode(1_011_005_002, "报告 PDF 文件不存在");
    ErrorCode REPORT_CAN_NOT_APPROVE = new ErrorCode(1_011_005_003, "报告尚未复核，不能审批");
    ErrorCode REPORT_GENERATION_FAILED = new ErrorCode(1_011_005_004, "报告生成失败");
    ErrorCode REPORT_LOCKED = new ErrorCode(1_011_005_005, "报告已锁版，不允许编辑");
    ErrorCode REPORT_CAN_NOT_LOCK = new ErrorCode(1_011_005_006, "当前报告状态不可锁版");
    ErrorCode REPORT_CAN_NOT_UNLOCK = new ErrorCode(1_011_005_007, "当前报告状态不可解锁");
    ErrorCode REPORT_VERSION_NOT_EXISTS = new ErrorCode(1_011_005_008, "报告版本记录不存在");
    ErrorCode REPORT_CAN_NOT_EXPORT = new ErrorCode(1_011_005_009, "报告未审批，不能导出");

    ErrorCode PLAN_NOT_EXISTS = new ErrorCode(1_011_006_000, "训练计划不存在");
    ErrorCode PLAN_PATIENT_EPISODE_MISMATCH = new ErrorCode(1_011_006_001, "计划关联的患者与 episode 不匹配");
    ErrorCode PLAN_ACTIVE_ALREADY_EXISTS = new ErrorCode(1_011_006_002, "该患者当前 episode 已存在执行中的计划");
    ErrorCode PLAN_CAN_NOT_DELETE = new ErrorCode(1_011_006_003, "计划已有执行记录，不允许删除");
    ErrorCode PLAN_STATUS_INVALID = new ErrorCode(1_011_006_004, "计划状态不合法");

    ErrorCode TASK_NOT_EXISTS = new ErrorCode(1_011_007_000, "训练任务不存在");
    ErrorCode TASK_PLAN_MISMATCH = new ErrorCode(1_011_007_001, "任务与计划归属不一致");
    ErrorCode TASK_CAN_NOT_DELETE = new ErrorCode(1_011_007_002, "任务已有执行记录，不允许删除");
    ErrorCode TASK_STATUS_INVALID = new ErrorCode(1_011_007_003, "任务状态不合法");

    ErrorCode CHECKIN_NOT_EXISTS = new ErrorCode(1_011_008_000, "打卡记录不存在");
    ErrorCode CHECKIN_TASK_EXECUTION_EMPTY = new ErrorCode(1_011_008_001, "打卡任务执行记录不能为空");
    ErrorCode CHECKIN_TASK_NOT_BELONG_TO_PLAN = new ErrorCode(1_011_008_002, "存在任务不属于当前计划");

    ErrorCode PROGRESS_NOT_EXISTS = new ErrorCode(1_011_009_000, "进度记录不存在");

    ErrorCode TRIGGER_NOT_EXISTS = new ErrorCode(1_011_010_000, "复评触发记录不存在");
    ErrorCode TRIGGER_STATUS_INVALID = new ErrorCode(1_011_010_001, "复评触发状态不合法");
    ErrorCode TRIGGER_CAN_NOT_HANDLE = new ErrorCode(1_011_010_002, "当前复评触发记录不可处理");

    ErrorCode CLERK_WRITE_FORBIDDEN = new ErrorCode(1_011_011_000, "文员无权执行该临床写入操作");

    ErrorCode APP_ADMIN_LOGIN_FORBIDDEN = new ErrorCode(1_011_012_000, "当前账号无权登录管理端小程序");
    ErrorCode APP_PATIENT_BINDING_REQUIRED = new ErrorCode(1_011_012_001, "当前账号未绑定患者身份，请先完成绑定");
    ErrorCode APP_PATIENT_BINDING_CONFLICT = new ErrorCode(1_011_012_002, "当前账号已绑定其他患者，请先解绑后再操作");
    ErrorCode APP_PATIENT_BIND_PHONE_MISMATCH = new ErrorCode(1_011_012_003, "绑定手机号与患者档案不一致");
    ErrorCode APP_PATIENT_DAILY_CHECKIN_EXISTS = new ErrorCode(1_011_012_004, "今日已提交打卡，请勿重复提交");
    ErrorCode APP_NOTIFICATION_NOT_EXISTS = new ErrorCode(1_011_012_005, "通知不存在或已失效");

    ErrorCode NOTIFICATION_NOT_EXISTS = new ErrorCode(1_011_013_000, "通知不存在");
    ErrorCode NOTIFICATION_NO_PERMISSION = new ErrorCode(1_011_013_001, "无权限操作该通知");
    ErrorCode NOTIFICATION_TARGET_TYPE_INVALID = new ErrorCode(1_011_013_002, "通知目标类型不合法");

    ErrorCode ALERT_RULE_NOT_EXISTS = new ErrorCode(1_011_014_000, "提醒规则不存在");
    ErrorCode ALERT_EVENT_NOT_EXISTS = new ErrorCode(1_011_014_001, "提醒事件不存在");
    ErrorCode ALERT_EVENT_CAN_NOT_HANDLE = new ErrorCode(1_011_014_002, "当前提醒事件不可处理");

    ErrorCode AI_JOB_NOT_EXISTS = new ErrorCode(1_011_015_000, "AI 任务不存在");
    ErrorCode AI_OUTPUT_NOT_EXISTS = new ErrorCode(1_011_015_001, "AI 输出不存在");
    ErrorCode AI_PROMPT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_011_015_002, "AI 提示词模板不存在");
    ErrorCode AI_CONFIG_NOT_EXISTS = new ErrorCode(1_011_015_003, "AI 配置不存在");
    ErrorCode AI_GENERATE_FORBIDDEN = new ErrorCode(1_011_015_004, "无权限发起 AI 生成");
    ErrorCode AI_REVIEW_FORBIDDEN = new ErrorCode(1_011_015_005, "无权限审核 AI 输出");
    ErrorCode AI_PRECONDITION_FAILED = new ErrorCode(1_011_015_006, "AI 生成前置条件不满足");
    ErrorCode AI_SCHEMA_VALIDATION_FAILED = new ErrorCode(1_011_015_007, "AI 输出结构校验失败");
    ErrorCode AI_SAFETY_BLOCKED = new ErrorCode(1_011_015_008, "AI 输出安全策略阻断");
    ErrorCode AI_JOB_STATUS_INVALID = new ErrorCode(1_011_015_009, "AI 任务状态不合法");
    ErrorCode AI_OUTPUT_REVIEW_STATUS_INVALID = new ErrorCode(1_011_015_010, "AI 输出审核状态不合法");
    ErrorCode AI_PROMPT_TEMPLATE_DUPLICATE_DEFAULT = new ErrorCode(1_011_015_011, "同一作用域仅允许一个默认模板");
    ErrorCode AI_PATIENT_VISIBILITY_FORBIDDEN = new ErrorCode(1_011_015_012, "当前 AI 输出不允许对患者可见");
    ErrorCode AI_OPENAI_CONFIG_MISSING = new ErrorCode(1_011_015_013, "OpenAI 配置缺失");

}
