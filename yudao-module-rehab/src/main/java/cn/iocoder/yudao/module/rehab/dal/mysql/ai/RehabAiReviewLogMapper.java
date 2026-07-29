package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiReviewLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAiReviewLogMapper extends BaseMapperX<RehabAiReviewLogDO> {

    default List<RehabAiReviewLogDO> selectListByOutputId(Long outputId) {
        return selectList(new LambdaQueryWrapperX<RehabAiReviewLogDO>()
                .eq(RehabAiReviewLogDO::getAiOutputId, outputId)
                .orderByDesc(RehabAiReviewLogDO::getCreateTime)
                .orderByDesc(RehabAiReviewLogDO::getId));
    }
}
