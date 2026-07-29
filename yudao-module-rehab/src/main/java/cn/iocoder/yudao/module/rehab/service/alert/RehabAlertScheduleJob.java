package cn.iocoder.yudao.module.rehab.service.alert;

import cn.iocoder.yudao.module.rehab.controller.admin.alert.vo.RehabAlertRefreshReqVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Step 6：提醒引擎定时刷新任务（每日）
 */
@Component
@Slf4j
public class RehabAlertScheduleJob {

    @Value("${yudao.rehab.alert-job-enabled:true}")
    private Boolean enabled;

    @Value("${yudao.rehab.alert-job-operator-user-id:1}")
    private Long operatorUserId;

    @Resource
    private RehabAlertService alertService;

    @Scheduled(cron = "${yudao.rehab.alert-job-cron:0 0 3 * * ?}")
    public void refreshDaily() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        try {
            alertService.refreshAlerts(new RehabAlertRefreshReqVO(), operatorUserId);
        } catch (Exception ex) {
            log.error("[rehab-alert-job] refresh failed", ex);
        }
    }
}
