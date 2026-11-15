-- 036. Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
-- Successful report generation results only. No clinical body or raw key.
-- Intentionally fail on an unknown pre-existing table; never adopt/overwrite it.
CREATE TABLE rehab_report_generation_request (
    tenant_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    request_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_id BIGINT NOT NULL,
    report_no VARCHAR(64) NOT NULL,
    report_version INT NOT NULL,
    fallback_used BIT(1) NOT NULL DEFAULT b'0',
    fallback_message VARCHAR(256) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, assessment_id, operator_user_id, request_key_hash),
    KEY idx_rehab_generation_report (tenant_id, report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报告成功生成请求幂等账本；无临床正文';
