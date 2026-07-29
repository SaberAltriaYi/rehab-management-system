package cn.iocoder.yudao.module.rehab.dal.mysql.tag;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.tag.RehabPatientTagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 患者标签 Mapper
 */
@Mapper
public interface RehabPatientTagMapper extends BaseMapperX<RehabPatientTagDO> {
}
