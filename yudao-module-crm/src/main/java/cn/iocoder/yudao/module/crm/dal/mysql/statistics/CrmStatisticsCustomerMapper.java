package cn.iocoder.yudao.module.crm.dal.mysql.statistics;

import cn.iocoder.yudao.module.crm.controller.admin.statistics.vo.customer.*;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * CRM 客户分析 Mapper
 *
 * @author dhb52
 */
@Mapper
public interface CrmStatisticsCustomerMapper {

    /**
     * 新建客户数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByDateRespVO> selectCustomerCreateCountGroupByDate(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 成交客户数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByDateRespVO> selectCustomerDealCountGroupByDate(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 新建客户数(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByUserRespVO> selectCustomerCreateCountGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 成交客户数(按用户)
     *
     * @param reqVO 请求参数@param reqVO 请求参数@param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByUserRespVO> selectCustomerDealCountGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 合同总金额(按用户)
     *
     * @return 统计数据@return 统计数据@param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByUserRespVO> selectContractPriceGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 合同回款金额(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerSummaryByUserRespVO> selectReceivablePriceGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 跟进次数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsFollowUpSummaryByDateRespVO> selectFollowUpRecordCountGroupByDate(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 跟进客户数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsFollowUpSummaryByDateRespVO> selectFollowUpCustomerCountGroupByDate(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 跟进次数(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsFollowUpSummaryByUserRespVO> selectFollowUpRecordCountGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 跟进客户数(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsFollowUpSummaryByUserRespVO> selectFollowUpCustomerCountGroupByUser(CrmStatisticsCustomerReqVO reqVO);


    /**
     * 首次合同、回款信息(用于【客户转化率】页面)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerContractSummaryRespVO> selectContractSummary(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 跟进次数(按类型)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsFollowUpSummaryByTypeRespVO> selectFollowUpRecordCountGroupByType(CrmStatisticsCustomerReqVO reqVO);


    /**
     * 进入公海客户数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    // 迁移 031 起只统计真实事件；历史没有审计事件，不能补造。
    default List<CrmStatisticsPoolSummaryByDateRespVO> selectPoolCustomerPutCountByDate(CrmStatisticsCustomerReqVO reqVO) {
        return selectPoolEventsByDate(reqVO, "PUT");
    }

    /**
     * 公海领取客户数(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    default List<CrmStatisticsPoolSummaryByDateRespVO> selectPoolCustomerTakeCountByDate(CrmStatisticsCustomerReqVO reqVO) {
        return selectPoolEventsByDate(reqVO, "TAKE");
    }

    /**
     * 进入公海客户数(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    default List<CrmStatisticsPoolSummaryByUserRespVO> selectPoolCustomerPutCountByUser(CrmStatisticsCustomerReqVO reqVO) {
        return selectPoolEventsByUser(reqVO, "PUT");
    }

    /**
     * 公海领取客户数(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    default List<CrmStatisticsPoolSummaryByUserRespVO> selectPoolCustomerTakeCountByUser(CrmStatisticsCustomerReqVO reqVO) {
        return selectPoolEventsByUser(reqVO, "TAKE");
    }

    static List<CrmStatisticsPoolSummaryByDateRespVO> selectPoolEventsByDate(CrmStatisticsCustomerReqVO reqVO, String type) {
        if (reqVO.getTimes() == null || reqVO.getTimes().length != 2 || reqVO.getUserIds() == null
                || reqVO.getUserIds().isEmpty()) {
            return Collections.emptyList();
        }
        String users = String.join(",", Collections.nCopies(reqVO.getUserIds().size(), "?"));
        List<Object> args = poolEventArgs(reqVO, type);
        return SpringUtil.getBean(JdbcTemplate.class).query(
                "SELECT DATE_FORMAT(event_time,'%Y-%m-%d') day, COUNT(*) total FROM crm_customer_pool_event "
                        + "WHERE tenant_id=? AND event_type=? AND event_time BETWEEN ? AND ? "
                        + "AND owner_user_id IN (" + users + ") GROUP BY DATE(event_time) ORDER BY day",
                (rs, rowNum) -> {
                    CrmStatisticsPoolSummaryByDateRespVO vo = new CrmStatisticsPoolSummaryByDateRespVO();
                    vo.setTime(rs.getString(1));
                    vo.setCustomerPutCount("PUT".equals(type) ? rs.getInt(2) : 0);
                    vo.setCustomerTakeCount("TAKE".equals(type) ? rs.getInt(2) : 0);
                    return vo;
                }, args.toArray());
    }

    static List<CrmStatisticsPoolSummaryByUserRespVO> selectPoolEventsByUser(CrmStatisticsCustomerReqVO reqVO, String type) {
        if (reqVO.getTimes() == null || reqVO.getTimes().length != 2 || reqVO.getUserIds() == null
                || reqVO.getUserIds().isEmpty()) {
            return Collections.emptyList();
        }
        String users = String.join(",", Collections.nCopies(reqVO.getUserIds().size(), "?"));
        List<Object> args = poolEventArgs(reqVO, type);
        return SpringUtil.getBean(JdbcTemplate.class).query(
                "SELECT owner_user_id,COUNT(*) FROM crm_customer_pool_event "
                        + "WHERE tenant_id=? AND event_type=? AND event_time BETWEEN ? AND ? "
                        + "AND owner_user_id IN (" + users + ") GROUP BY owner_user_id",
                (rs, rowNum) -> {
                    CrmStatisticsPoolSummaryByUserRespVO vo = new CrmStatisticsPoolSummaryByUserRespVO();
                    vo.setOwnerUserId(rs.getLong(1));
                    vo.setCustomerPutCount("PUT".equals(type) ? rs.getInt(2) : 0);
                    vo.setCustomerTakeCount("TAKE".equals(type) ? rs.getInt(2) : 0);
                    return vo;
                }, args.toArray());
    }

    static List<Object> poolEventArgs(CrmStatisticsCustomerReqVO reqVO, String type) {
        List<Object> args = new ArrayList<>();
        args.add(TenantContextHolder.getRequiredTenantId());
        args.add(type);
        args.add(reqVO.getTimes()[0]);
        args.add(reqVO.getTimes()[1]);
        args.addAll(reqVO.getUserIds());
        return args;
    }

    /**
     * 客户成交周期(按日期)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerDealCycleByDateRespVO> selectCustomerDealCycleGroupByDate(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 客户成交周期(按用户)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerDealCycleByUserRespVO> selectCustomerDealCycleGroupByUser(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 客户成交周期(按区域)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerDealCycleByAreaRespVO> selectCustomerDealCycleGroupByAreaId(CrmStatisticsCustomerReqVO reqVO);

    /**
     * 客户成交周期(按产品)
     *
     * @param reqVO 请求参数
     * @return 统计数据
     */
    List<CrmStatisticsCustomerDealCycleByProductRespVO> selectCustomerDealCycleGroupByProductId(CrmStatisticsCustomerReqVO reqVO);

}
