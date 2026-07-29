package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiSuggestionBundleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAiSuggestionBundleMapper extends BaseMapperX<RehabAiSuggestionBundleDO> {

    default RehabAiSuggestionBundleDO selectLatestByTypeAndPatient(String bundleType, Long patientId) {
        List<RehabAiSuggestionBundleDO> list = selectList(new LambdaQueryWrapperX<RehabAiSuggestionBundleDO>()
                .eq(RehabAiSuggestionBundleDO::getBundleType, bundleType)
                .eq(RehabAiSuggestionBundleDO::getPatientId, patientId)
                .orderByDesc(RehabAiSuggestionBundleDO::getUpdateTime)
                .orderByDesc(RehabAiSuggestionBundleDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }
}
