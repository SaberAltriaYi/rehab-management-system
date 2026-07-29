package cn.iocoder.yudao.module.rehab.service.alert;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventPageReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertEventRespVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertHandleReqVO;
import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertRefreshReqVO;

import java.util.List;

public interface RehabAlertService {

    PageResult<RehabAlertEventRespVO> getAlertPage(RehabAlertEventPageReqVO reqVO, Long operatorUserId);

    RehabAlertEventRespVO getAlert(Long id, Long operatorUserId);

    List<Long> refreshAlerts(RehabAlertRefreshReqVO reqVO, Long operatorUserId);

    void acknowledgeAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId);

    void resolveAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId);

    void ignoreAlert(RehabAlertHandleReqVO reqVO, Long operatorUserId);

    long countActiveHighRiskByVisiblePatients(Long operatorUserId);

}
