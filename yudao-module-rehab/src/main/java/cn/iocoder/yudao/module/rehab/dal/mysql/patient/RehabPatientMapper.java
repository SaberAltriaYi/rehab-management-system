package cn.iocoder.yudao.module.rehab.dal.mysql.patient;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.patient.vo.RehabPatientPageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.patient.RehabPatientDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 患者主档案 Mapper
 */
@Mapper
public interface RehabPatientMapper extends BaseMapperX<RehabPatientDO> {

    default PageResult<RehabPatientDO> selectPage(RehabPatientPageReqVO reqVO,
                                                  Collection<Long> visiblePatientIds,
                                                  Collection<Long> crmFilteredPatientIds) {
        if (visiblePatientIds != null && visiblePatientIds.isEmpty()) {
            return PageResult.empty(0L);
        }
        if (crmFilteredPatientIds != null && crmFilteredPatientIds.isEmpty()) {
            return PageResult.empty(0L);
        }

        LambdaQueryWrapperX<RehabPatientDO> query = new LambdaQueryWrapperX<RehabPatientDO>()
                .eqIfPresent(RehabPatientDO::getCurrentTherapistUserId, reqVO.getCurrentTherapistUserId())
                .eqIfPresent(RehabPatientDO::getCurrentStage, reqVO.getCurrentStage())
                .eqIfPresent(RehabPatientDO::getGender, reqVO.getGender())
                .eqIfPresent(RehabPatientDO::getSourceChannel, reqVO.getSourceChannel())
                .betweenIfPresent(RehabPatientDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RehabPatientDO::getUpdateTime);

        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(wrapper -> wrapper.like(RehabPatientDO::getName, reqVO.getKeyword())
                    .or().like(RehabPatientDO::getPhone, reqVO.getKeyword())
                    .or().like(RehabPatientDO::getPatientNo, reqVO.getKeyword()));
        }
        if (visiblePatientIds != null) {
            query.in(RehabPatientDO::getId, visiblePatientIds);
        }
        if (crmFilteredPatientIds != null) {
            query.in(RehabPatientDO::getId, crmFilteredPatientIds);
        }
        return selectPage(reqVO, query);
    }

    default RehabPatientDO selectByPatientNo(String patientNo) {
        return selectOne(RehabPatientDO::getPatientNo, patientNo);
    }

    default List<RehabPatientDO> selectListByNameAndPhone(String name, String phone) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(phone)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<RehabPatientDO>()
                .eq(RehabPatientDO::getName, name)
                .eq(RehabPatientDO::getPhone, phone)
                .orderByDesc(RehabPatientDO::getUpdateTime));
    }

}
