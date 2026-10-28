package cn.iocoder.yudao.module.rehab.controller.admin.motion;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rehab.enums.RehabMotionErrorCodeConstants.MOTION_WORKSPACE_FAILED;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.rehab.service.motion.workspace.MotionWorkspaceService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/rehab/motion/workspace")
public class RehabMotionWorkspaceController {
    @Resource private MotionWorkspaceService service;
    private <T> CommonResult<T> safe(String instruction,java.util.function.Supplier<T> action) {
        try{return success(action.get());}
        catch(IllegalStateException | IllegalArgumentException rejected){throw exception(MOTION_WORKSPACE_FAILED,instruction);}
    }

    @PostMapping("/launch")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('rehab:motion:workspace')")
    public CommonResult<Map<String, Object>> launch(@RequestParam Long assessmentId) {
        return safe("无法进入，请检查服务地址、令牌、HTTPS证书及绑定修订",()->service.launch(assessmentId, getLoginUserId()));
    }

    @PostMapping("/receive")
    @PreAuthorize("@ss.hasPermission('rehab:motion:sync')")
    public CommonResult<Map<String, Object>> receive(@RequestParam Long assessmentId) {
        return safe("无法接收，请先提交最新修订并检查来源哈希、患者及主系统输入修订",()->service.receive(assessmentId, getLoginUserId()));
    }

    @GetMapping("/history")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('rehab:motion:workspace')")
    public CommonResult<List<Map<String, Object>>> history(@RequestParam Long assessmentId) {
        return safe("无法读取历史，请检查迁移及患者权限",()->service.history(assessmentId, getLoginUserId()));
    }

    @PostMapping("/review")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('rehab:motion:review')")
    public CommonResult<Boolean> review(
            @RequestParam Long assessmentId,
            @RequestParam Long inboxId,
            @RequestBody Map<String, String> body) {
        return safe("无法复核，请填写结论并检查是否已复核或输入已变化",()->{service.review(assessmentId, inboxId, body.get("note"), getLoginUserId());return true;});
    }
}
