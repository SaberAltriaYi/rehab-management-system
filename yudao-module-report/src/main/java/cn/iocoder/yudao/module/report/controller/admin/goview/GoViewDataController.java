package cn.iocoder.yudao.module.report.controller.admin.goview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.report.controller.admin.goview.vo.data.GoViewDataGetBySqlReqVO;
import cn.iocoder.yudao.module.report.controller.admin.goview.vo.data.GoViewDataRespVO;
import cn.iocoder.yudao.module.report.service.goview.GoViewDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.report.enums.ErrorCodeConstants.GO_VIEW_HTTP_SOURCE_NOT_CONFIGURED;

@Tag(name = "管理后台 - GoView 数据", description = "数据源需通过专门审核的接口接入")
@RestController
@RequestMapping("/report/go-view/data")
@Validated
public class GoViewDataController {

    @Resource
    private GoViewDataService goViewDataService;

    @RequestMapping("/get-by-sql")
    @Operation(summary = "使用 SQL 查询数据（安全关闭）")
    @PreAuthorize("@ss.hasPermission('report:go-view-data:get-by-sql')")
    public CommonResult<GoViewDataRespVO> getDataBySQL(@Valid @RequestBody GoViewDataGetBySqlReqVO reqVO) {
        return success(goViewDataService.getDataBySQL(reqVO.getSql()));
    }

    @RequestMapping("/get-by-http")
    @Operation(summary = "使用 HTTP 查询数据（未配置）")
    @PreAuthorize("@ss.hasPermission('report:go-view-data:get-by-http')")
    public CommonResult<GoViewDataRespVO> getDataByHttp(
            @RequestParam(required = false) Map<String, String> params,
            @RequestBody(required = false) String body) {
        // Never return fabricated PV/UV as real institutional operational data.
        throw exception(GO_VIEW_HTTP_SOURCE_NOT_CONFIGURED);
    }

}
