package cn.iocoder.yudao.module.rehab.dal.mysql.motion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rehab.dal.dataobject.motion.RehabMotionProtocolVersionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动作评估协议版本 Mapper
 */
@Mapper
public interface RehabMotionProtocolVersionMapper extends BaseMapperX<RehabMotionProtocolVersionDO> {

    default RehabMotionProtocolVersionDO selectByKey(String code, String version, String fileSha256) {
        return selectOne(new LambdaQueryWrapperX<RehabMotionProtocolVersionDO>()
                .eq(RehabMotionProtocolVersionDO::getProtocolCode, code)
                .eq(RehabMotionProtocolVersionDO::getProtocolVersion, version)
                .eq(RehabMotionProtocolVersionDO::getFileSha256, fileSha256));
    }
}
