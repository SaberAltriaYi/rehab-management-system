package cn.iocoder.yudao.module.rehab.service.motion.engine;

import java.util.List;
import java.util.Map;

/**
 * 动作分析引擎（rehab-biomechanics-engine motion service）客户端。引擎无状态：
 * Java 侧负责存储、租户、权限与任务编排，引擎只做确定性计算。
 */
public interface MotionEngineClient {

    boolean isConfigured();

    /** @param zip application/zip：根目录 request.json + OpenCap 目录结构 */
    byte[] analyze(byte[] zip);

    Map<String, Object> protocols();

    Map<String, Object> opencapTrialStatus(String sessionId, List<String> trialIds);

    byte[] opencapDownload(String sessionId, List<String> trialIds);

}
