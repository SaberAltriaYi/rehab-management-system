package cn.iocoder.yudao.module.rehab.controller.admin.episode;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rehab.controller.admin.episode.vo.*;
import cn.iocoder.yudao.module.rehab.service.episode.RehabEpisodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 康复 Episode")
@RestController
@RequestMapping("/rehab/episode")
@Validated
public class RehabEpisodeController {

    @Resource
    private RehabEpisodeService episodeService;

    @PostMapping("/create")
    @Operation(summary = "创建 episode")
    @PreAuthorize("@ss.hasPermission('rehab:episode:create')")
    public CommonResult<Long> createEpisode(@Valid @RequestBody RehabEpisodeCreateReqVO reqVO) {
        return success(episodeService.createEpisode(reqVO, getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 episode 分页")
    @PreAuthorize("@ss.hasPermission('rehab:episode:view')")
    public CommonResult<PageResult<RehabEpisodeRespVO>> getEpisodePage(@Valid RehabEpisodePageReqVO pageReqVO) {
        return success(episodeService.getEpisodePage(pageReqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 episode 详情")
    @Parameter(name = "id", description = "episode 编号", required = true)
    @PreAuthorize("@ss.hasPermission('rehab:episode:view')")
    public CommonResult<RehabEpisodeRespVO> getEpisode(@RequestParam("id") Long id) {
        return success(episodeService.getEpisode(id, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 episode")
    @PreAuthorize("@ss.hasPermission('rehab:episode:update')")
    public CommonResult<Boolean> updateEpisode(@Valid @RequestBody RehabEpisodeUpdateReqVO reqVO) {
        episodeService.updateEpisode(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/change-stage")
    @Operation(summary = "修改 episode 阶段")
    @PreAuthorize("@ss.hasPermission('rehab:episode:update')")
    public CommonResult<Boolean> changeStage(@Valid @RequestBody RehabEpisodeChangeStageReqVO reqVO) {
        episodeService.changeStage(reqVO, getLoginUserId());
        return success(true);
    }

}
