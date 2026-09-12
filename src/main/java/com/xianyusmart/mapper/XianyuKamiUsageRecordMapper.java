package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuKamiUsageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface XianyuKamiUsageRecordMapper extends BaseMapper<XianyuKamiUsageRecord> {

    @Select("<script>"
            + "SELECT * FROM xianyu_kami_usage_record "
            + "WHERE kami_config_id = #{kamiConfigId} "
            + "<if test='orderId != null'>AND order_id LIKE CONCAT('%', #{orderId}, '%') </if>"
            + "<if test='buyerKeyword != null'>AND (buyer_user_name LIKE CONCAT('%', #{buyerKeyword}, '%') "
            + "OR buyer_user_id LIKE CONCAT('%', #{buyerKeyword}, '%')) </if>"
            + "<if test='goodsId != null'>AND xy_goods_id LIKE CONCAT('%', #{goodsId}, '%') </if>"
            + "<if test='deliveryStatus != null'>AND delivery_status = #{deliveryStatus} </if>"
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<XianyuKamiUsageRecord> selectHistoryPage(
            @Param("kamiConfigId") Long kamiConfigId,
            @Param("orderId") String orderId,
            @Param("buyerKeyword") String buyerKeyword,
            @Param("goodsId") String goodsId,
            @Param("deliveryStatus") String deliveryStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM xianyu_kami_usage_record "
            + "WHERE kami_config_id = #{kamiConfigId} "
            + "<if test='orderId != null'>AND order_id LIKE CONCAT('%', #{orderId}, '%') </if>"
            + "<if test='buyerKeyword != null'>AND (buyer_user_name LIKE CONCAT('%', #{buyerKeyword}, '%') "
            + "OR buyer_user_id LIKE CONCAT('%', #{buyerKeyword}, '%')) </if>"
            + "<if test='goodsId != null'>AND xy_goods_id LIKE CONCAT('%', #{goodsId}, '%') </if>"
            + "<if test='deliveryStatus != null'>AND delivery_status = #{deliveryStatus} </if>"
            + "<if test='startTime != null'>AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND create_time &lt;= #{endTime} </if>"
            + "</script>")
    long countHistory(
            @Param("kamiConfigId") Long kamiConfigId,
            @Param("orderId") String orderId,
            @Param("buyerKeyword") String buyerKeyword,
            @Param("goodsId") String goodsId,
            @Param("deliveryStatus") String deliveryStatus,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) FROM xianyu_kami_usage_record WHERE kami_config_id = #{kamiConfigId}")
    long countByConfigId(@Param("kamiConfigId") Long kamiConfigId);

    @Select("SELECT COUNT(*) FROM xianyu_kami_usage_record WHERE xianyu_account_id = #{accountId}")
    long countByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM xianyu_kami_usage_record "
            + "WHERE kami_item_id = #{kamiItemId} "
            + "AND kami_config_id = #{kamiConfigId} "
            + "AND order_id = #{orderId}")
    int countCurrentDeliveryHistory(@Param("kamiItemId") Long kamiItemId,
                                    @Param("kamiConfigId") Long kamiConfigId,
                                    @Param("orderId") String orderId);

    /**
     * 返回当前仍在库存中的 DELIVERED 项，并要求其交付快照完全匹配。
     * 配置行已在服务层锁定，这里的行锁用于执行阶段的最后一次确认。
     */
    @Select("SELECT i.id FROM xianyu_kami_item i "
            + "INNER JOIN xianyu_kami_usage_record u "
            + "ON u.kami_item_id = i.id "
            + "AND u.kami_config_id = i.kami_config_id "
            + "AND u.order_id = i.order_id "
            + "WHERE i.kami_config_id = #{kamiConfigId} "
            + "AND i.status = 1 "
            + "ORDER BY i.id ASC FOR UPDATE")
    List<Long> findArchivableDeliveredItemIds(@Param("kamiConfigId") Long kamiConfigId);

    @Select("SELECT COALESCE(MAX(delivery_index), 0) FROM xianyu_kami_usage_record "
            + "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId}")
    int findMaxDeliveryIndex(@Param("accountId") Long accountId,
                             @Param("orderId") String orderId);
}
