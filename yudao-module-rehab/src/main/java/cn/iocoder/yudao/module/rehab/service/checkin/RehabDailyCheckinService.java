package cn.iocoder.yudao.module.rehab.service.checkin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.checkin.vo.*;

import java.util.List;

public interface RehabDailyCheckinService {

    PageResult<RehabDailyCheckinRespVO> getCheckinPage(RehabDailyCheckinPageReqVO reqVO, Long operatorUserId);

    RehabDailyCheckinRespVO getCheckin(Long id, Long operatorUserId);

    Long createCheckin(RehabDailyCheckinCreateReqVO reqVO, Long operatorUserId, boolean manual);

    Long createAttendance(RehabTrainingAttendanceCreateReqVO reqVO, Long operatorUserId);

    List<RehabTaskExecutionRespVO> getTaskExecutionList(Long checkinId, Long operatorUserId);

}
