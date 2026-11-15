package cn.iocoder.yudao.module.rehab.enums;

/** Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT. */
public final class RehabPatientDataOrigin {
    public static final String REAL = "REAL";
    public static final String SYNTHETIC = "SYNTHETIC";
    public static final String ARCHIVED = "SYNTHETIC_ARCHIVED";
    public static final String PERMISSION = "rehab:patient:data-governance";

    private RehabPatientDataOrigin() {
    }

    public static boolean isSynthetic(String value) {
        return SYNTHETIC.equals(value) || ARCHIVED.equals(value);
    }
}
