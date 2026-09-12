package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuBuyerBlacklist;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuBuyerBlacklistMapper extends BaseMapper<XianyuBuyerBlacklist> {

    /** 删除账号专属黑名单，全局黑名单的账号 ID 为 NULL，应予保留。 */
    @Delete("DELETE FROM xianyu_buyer_blacklist WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(1) FROM xianyu_buyer_blacklist " +
            "WHERE buyer_user_id = #{buyerUserId} AND enabled = 1 " +
            "AND (xianyu_account_id IS NULL OR xianyu_account_id = #{accountId})")
    int countActive(@Param("accountId") Long accountId, @Param("buyerUserId") String buyerUserId);

    @Select("<script>SELECT b.id, b.xianyu_account_id, b.buyer_user_id, b.buyer_user_name, " +
            "b.reason, b.enabled, b.create_time, b.update_time, a.account_note " +
            "FROM xianyu_buyer_blacklist b LEFT JOIN xianyu_account a ON a.id = b.xianyu_account_id " +
            "WHERE 1=1 " +
            "<if test='accountId != null'>AND (b.xianyu_account_id IS NULL OR b.xianyu_account_id = #{accountId}) </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (b.buyer_user_id LIKE CONCAT('%',#{keyword},'%') " +
            "OR b.buyer_user_name LIKE CONCAT('%',#{keyword},'%') OR b.reason LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "ORDER BY b.enabled DESC, b.update_time DESC</script>")
    List<XianyuBuyerBlacklist> findAll(@Param("accountId") Long accountId, @Param("keyword") String keyword);

    @Select("SELECT * FROM xianyu_buyer_blacklist WHERE buyer_user_id = #{buyerUserId} AND enabled = 1 " +
            "AND (xianyu_account_id IS NULL OR xianyu_account_id = #{accountId}) " +
            "ORDER BY xianyu_account_id IS NULL DESC LIMIT 1")
    XianyuBuyerBlacklist findActive(@Param("accountId") Long accountId, @Param("buyerUserId") String buyerUserId);
}
