package cn.iocoder.yudao.module.rehab.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 动作评估错误码：1-011-030-xxx
 */
public interface RehabMotionErrorCodeConstants {

    ErrorCode MOTION_NOT_EXISTS = new ErrorCode(1_011_030_000, "动作评估不存在");
    ErrorCode MOTION_SIGNED_LOCKED = new ErrorCode(1_011_030_001, "动作评估已签署，需先发起修订");
    ErrorCode MOTION_FILE_PATH_INVALID = new ErrorCode(1_011_030_002, "文件路径不符合 OpenCap 导出结构或包含不安全字符");
    ErrorCode MOTION_FILE_TYPE_INVALID = new ErrorCode(1_011_030_003, "文件类型或内容不合法");
    ErrorCode MOTION_FILE_TOO_LARGE = new ErrorCode(1_011_030_004, "文件超过大小限制");
    ErrorCode MOTION_FILE_STORE_FAILED = new ErrorCode(1_011_030_005, "文件保存失败");
    ErrorCode MOTION_FILE_NOT_EXISTS = new ErrorCode(1_011_030_006, "文件不存在");
    ErrorCode MOTION_VIDEO_CONSENT_REQUIRED = new ErrorCode(1_011_030_007, "患者未同意保存视频");
    ErrorCode MOTION_TRIAL_INVALID = new ErrorCode(1_011_030_008, "Trial 配置不合法：{}");
    ErrorCode MOTION_KINEMATICS_MISSING = new ErrorCode(1_011_030_009, "缺少运动学数据：{}");
    ErrorCode MOTION_TASK_ACTIVE = new ErrorCode(1_011_030_010, "已有进行中的任务，请等待完成或取消");
    ErrorCode MOTION_TASK_NOT_EXISTS = new ErrorCode(1_011_030_011, "任务不存在");
    ErrorCode MOTION_TASK_STATE_INVALID = new ErrorCode(1_011_030_012, "当前任务状态不允许该操作");
    ErrorCode MOTION_STATE_INVALID = new ErrorCode(1_011_030_013, "当前评估状态不允许该操作：{}");
    ErrorCode MOTION_REASON_REQUIRED = new ErrorCode(1_011_030_014, "修改须填写原因");
    ErrorCode MOTION_SCORE_NOT_EXISTS = new ErrorCode(1_011_030_015, "评分不存在");
    ErrorCode MOTION_SCORE_OUT_OF_RANGE = new ErrorCode(1_011_030_016, "分数超出该计分方案的取值范围");
    ErrorCode MOTION_SIGN_PRECONDITION = new ErrorCode(1_011_030_017, "签署前置条件未满足：{}");
    ErrorCode MOTION_AI_DRAFT_NOT_EXISTS = new ErrorCode(1_011_030_018, "AI 草稿不存在");
    ErrorCode MOTION_REPORT_NOT_EXISTS = new ErrorCode(1_011_030_019, "报告不存在");
    ErrorCode MOTION_PDF_NOT_READY = new ErrorCode(1_011_030_020, "PDF 尚未生成");
    ErrorCode MOTION_COMPARE_INVALID = new ErrorCode(1_011_030_021, "只能对比同一患者的两次动作评估");
    ErrorCode MOTION_OPENCAP_SESSION_REQUIRED = new ErrorCode(1_011_030_022, "未填写 OpenCap 会话 ID");
    ErrorCode MOTION_ENGINE_UNAVAILABLE = new ErrorCode(1_011_030_023, "动作分析引擎不可用：{}");
    ErrorCode MOTION_IDEMPOTENCY_KEY_INVALID = new ErrorCode(1_011_030_024, "幂等键不合法");
    ErrorCode MOTION_MANUAL_INPUT_INVALID = new ErrorCode(1_011_030_025, "人工输入不合法：{}");

}
