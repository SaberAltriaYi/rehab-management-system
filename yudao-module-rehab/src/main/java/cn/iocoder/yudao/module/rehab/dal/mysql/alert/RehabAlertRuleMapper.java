package cn.iocoder.yudao.module.rehab.dal.mysql.alert;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.alert.RehabAlertRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAlertRuleMapper extends BaseMapperX<RehabAlertRuleDO> {

    default RehabAlertRuleDO selectByRuleCode(String ruleCode) {
        return selectOne(new LambdaQueryWrapperX<RehabAlertRuleDO>()
                .eq(RehabAlertRuleDO::getRuleCode, ruleCode));
    }

    default List<RehabAlertRuleDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<RehabAlertRuleDO>()
                .eq(RehabAlertRuleDO::getEnabled, true)
                .orderByAsc(RehabAlertRuleDO::getId));
    }
}
