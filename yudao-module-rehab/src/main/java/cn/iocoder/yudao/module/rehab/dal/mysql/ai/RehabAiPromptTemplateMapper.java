package cn.iocoder.yudao.module.rehab.dal.mysql.ai;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.controller.admin.ai.vo.RehabAiPromptTemplatePageReqVO;
import cn.iocoder.yudao.module.rehab.dal.dataobject.ai.RehabAiPromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RehabAiPromptTemplateMapper extends BaseMapperX<RehabAiPromptTemplateDO> {

    default PageResult<RehabAiPromptTemplateDO> selectPage(RehabAiPromptTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RehabAiPromptTemplateDO>()
                .eqIfPresent(RehabAiPromptTemplateDO::getTemplateCode, reqVO.getTemplateCode())
                .eqIfPresent(RehabAiPromptTemplateDO::getModuleScope, reqVO.getModuleScope())
                .eqIfPresent(RehabAiPromptTemplateDO::getRoleScope, reqVO.getRoleScope())
                .eqIfPresent(RehabAiPromptTemplateDO::getEnabled, reqVO.getEnabled())
                .orderByDesc(RehabAiPromptTemplateDO::getUpdateTime)
                .orderByDesc(RehabAiPromptTemplateDO::getId));
    }

    default RehabAiPromptTemplateDO selectDefaultTemplate(String moduleScope, String roleScope) {
        List<RehabAiPromptTemplateDO> list = selectList(new LambdaQueryWrapperX<RehabAiPromptTemplateDO>()
                .eq(RehabAiPromptTemplateDO::getModuleScope, moduleScope)
                .eq(RehabAiPromptTemplateDO::getRoleScope, roleScope)
                .eq(RehabAiPromptTemplateDO::getEnabled, true)
                .eq(RehabAiPromptTemplateDO::getIsDefault, true)
                .orderByDesc(RehabAiPromptTemplateDO::getVersionNo)
                .orderByDesc(RehabAiPromptTemplateDO::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    default void clearDefaultByScope(String moduleScope, String roleScope) {
        update(new RehabAiPromptTemplateDO().setIsDefault(false),
                new LambdaQueryWrapperX<RehabAiPromptTemplateDO>()
                        .eq(RehabAiPromptTemplateDO::getModuleScope, moduleScope)
                        .eq(RehabAiPromptTemplateDO::getRoleScope, roleScope)
                        .eq(RehabAiPromptTemplateDO::getIsDefault, true));
    }
}
