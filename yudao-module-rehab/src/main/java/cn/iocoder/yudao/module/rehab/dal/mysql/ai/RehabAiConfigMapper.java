package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAiConfigMapper extends BaseMapperX<RehabAiConfigDO> {

    default RehabAiConfigDO selectGlobalConfig() {
        List<RehabAiConfigDO> list = selectList(new LambdaQueryWrapperX<RehabAiConfigDO>()
                .eq(RehabAiConfigDO::getConfigScope, "global")
                .orderByDesc(RehabAiConfigDO::getUpdateTime)
                .orderByDesc(RehabAiConfigDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }
}
