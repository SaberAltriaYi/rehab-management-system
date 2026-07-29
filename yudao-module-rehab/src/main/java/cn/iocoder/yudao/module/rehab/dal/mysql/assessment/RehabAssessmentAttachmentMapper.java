package cn.iocoder.yudao.module.rehab.dal.mysql.assessment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.assessment.RehabAssessmentAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评估附件 Mapper
 */
@Mapper
public interface RehabAssessmentAttachmentMapper extends BaseMapperX<RehabAssessmentAttachmentDO> {

    default List<RehabAssessmentAttachmentDO> selectListByAssessmentId(Long assessmentId) {
        return selectList(new LambdaQueryWrapperX<RehabAssessmentAttachmentDO>()
                .eq(RehabAssessmentAttachmentDO::getAssessmentId, assessmentId)
                .orderByDesc(RehabAssessmentAttachmentDO::getId));
    }

}
