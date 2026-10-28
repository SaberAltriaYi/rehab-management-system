package cn.iocoder.yudao.module.system.dal.mysql.sms;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsCodeDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;

@Mapper
public interface SmsCodeMapper extends BaseMapperX<SmsCodeDO> {

    /**
     * 获得手机号的最后一个手机验证码
     *
     * @param mobile 手机号
     * @param scene 发送场景，选填
     * @param code 验证码 选填
     * @return 手机验证码
     */
    default SmsCodeDO selectLastByMobile(String mobile, String code, Integer scene) {
        return selectOne(new QueryWrapperX<SmsCodeDO>()
                .eq("mobile", mobile)
                .eqIfPresent("scene", scene)
                .eqIfPresent("code", code)
                .orderByDesc("id")
                .limitN(1));
    }

    /** Atomic one-time consumption; a concurrent login may have consumed it after validation. */
    default int consumeIfUnused(Long id, LocalDateTime usedTime, String usedIp, LocalDateTime validAfter) {
        return update(null, new LambdaUpdateWrapper<SmsCodeDO>()
                .eq(SmsCodeDO::getId, id)
                .eq(SmsCodeDO::getUsed, false)
                .gt(SmsCodeDO::getCreateTime, validAfter)
                .set(SmsCodeDO::getUsed, true)
                .set(SmsCodeDO::getUsedTime, usedTime)
                .set(SmsCodeDO::getUsedIp, usedIp));
    }

}
