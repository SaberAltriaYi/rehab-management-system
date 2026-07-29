package cn.iocoder.yudao.module.rehab.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 康复阶段常量
 */
public interface RehabStageConstants {

    String INTAKE = "初诊建档";
    String PENDING_ASSESSMENT = "待评估";
    String ASSESSING = "评估中";
    String IN_PROGRESS = "执行中";
    String REASSESSING = "复评中";
    String CLOSED = "已结案";
    String PAUSED = "已暂停";
    String REFERRED_OUT = "已转诊";

    List<String> ALL = Arrays.asList(
            INTAKE,
            PENDING_ASSESSMENT,
            ASSESSING,
            IN_PROGRESS,
            REASSESSING,
            CLOSED,
            PAUSED,
            REFERRED_OUT
    );

}
