package com.xianyusmart.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KamiUsageHistoryMapperSqlTest {

    @Test
    void archiveCandidateQueryRequiresTheCurrentItemOrderAndConfigMatch() throws Exception {
        Method method = XianyuKamiUsageRecordMapper.class.getMethod(
                "findArchivableDeliveredItemIds", Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("u.kami_item_id = i.id"));
        assertTrue(sql.contains("u.kami_config_id = i.kami_config_id"));
        assertTrue(sql.contains("u.order_id = i.order_id"));
        assertTrue(sql.contains("i.status = 1"));
        assertTrue(sql.contains("FOR UPDATE"));
    }

    @Test
    void deliveredItemDeletionIsFencedByTheSameUsageHistoryMatch() throws Exception {
        Method method = XianyuKamiItemMapper.class.getMethod(
                "deleteArchivableDelivered", Long.class, java.util.List.class);
        String sql = String.join(" ", method.getAnnotation(Delete.class).value());

        assertTrue(sql.contains("u.kami_item_id = i.id"));
        assertTrue(sql.contains("u.kami_config_id = i.kami_config_id"));
        assertTrue(sql.contains("u.order_id = i.order_id"));
        assertTrue(sql.contains("i.status = 1"));
    }

    @Test
    void historyPageQuerySupportsStablePaginationAndSnapshotFilters() throws Exception {
        Method method = XianyuKamiUsageRecordMapper.class.getMethod(
                "selectHistoryPage", Long.class, String.class, String.class, String.class,
                String.class, java.time.LocalDateTime.class, java.time.LocalDateTime.class,
                int.class, long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("kami_config_id = #{kamiConfigId}"));
        assertTrue(sql.contains("ORDER BY create_time DESC, id DESC"));
        assertTrue(sql.contains("LIMIT #{limit} OFFSET #{offset}"));
        assertTrue(sql.contains("buyer_user_name"));
        assertTrue(sql.contains("delivery_status"));
    }
}
