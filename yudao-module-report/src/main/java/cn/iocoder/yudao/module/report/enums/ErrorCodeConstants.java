package cn.iocoder.yudao.module.report.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Report 错误码枚举类
 *
 * report 系统，使用 1-003-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== GoView 模块 1-003-000-000 ==========
    ErrorCode GO_VIEW_PROJECT_NOT_EXISTS = new ErrorCode(1_003_000_000, "GoView 项目不存在");
    ErrorCode GO_VIEW_RAW_SQL_DISABLED = new ErrorCode(1_003_000_001, "报表自定义 SQL 已禁用，请使用经审批的数据接口");
    ErrorCode GO_VIEW_HTTP_SOURCE_NOT_CONFIGURED = new ErrorCode(1_003_000_002, "报表 HTTP 数据源尚未配置");

}
