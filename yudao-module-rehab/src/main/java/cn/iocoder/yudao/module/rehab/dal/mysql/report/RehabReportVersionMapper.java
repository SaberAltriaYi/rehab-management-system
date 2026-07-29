package cn.iocoder.yudao.module.rehab.dal.mysql.report;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.report.vo.RehabReportVersionPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.report.RehabReportVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabReportVersionMapper extends BaseMapperX<RehabReportVersionDO> {

    default PageResult<RehabReportVersionDO> selectPage(RehabReportVersionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RehabReportVersionDO>()
                .eq(RehabReportVersionDO::getReportId, reqVO.getReportId())
                .orderByDesc(RehabReportVersionDO::getVersionNo)
                .orderByDesc(RehabReportVersionDO::getId));
    }

    default RehabReportVersionDO selectLatestByReportId(Long reportId) {
        List<RehabReportVersionDO> list = selectList(new LambdaQueryWrapperX<RehabReportVersionDO>()
                .eq(RehabReportVersionDO::getReportId, reportId)
                .orderByDesc(RehabReportVersionDO::getVersionNo)
                .orderByDesc(RehabReportVersionDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

}
