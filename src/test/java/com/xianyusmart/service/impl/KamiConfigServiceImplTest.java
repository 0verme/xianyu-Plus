package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.KamiArchiveResultDTO;
import com.xianyusmart.controller.dto.KamiConfigDeleteRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiItem;
import com.xianyusmart.entity.XianyuKamiUsageRecord;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import com.xianyusmart.mapper.XianyuKamiUsageRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.NotificationChannelService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KamiConfigServiceImplTest {

    @Mock
    private XianyuKamiConfigMapper kamiConfigMapper;
    @Mock
    private XianyuKamiItemMapper kamiItemMapper;
    @Mock
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;
    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private NotificationChannelService notificationChannelService;
    @InjectMocks
    private KamiConfigServiceImpl service;

    @Test
    void deletesDeliveredItemAndRefreshesInventoryCounts() {
        XianyuKamiItem item = item(11L, 7L, 1);
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        when(kamiItemMapper.selectById(11L)).thenReturn(item);
        when(kamiUsageRecordMapper.countCurrentDeliveryHistory(11L, 7L, null)).thenReturn(1);
        when(kamiItemMapper.deleteIfNotPending(11L)).thenReturn(1);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(3);
        when(kamiItemMapper.countUsed(7L)).thenReturn(1);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);

        ResultObject<Void> result = service.deleteKamiItem(11L);

        assertEquals(200, result.getCode());
        assertEquals(3, config.getTotalCount());
        assertEquals(1, config.getUsedCount());
        verify(kamiConfigMapper).updateById(config);
    }

    @Test
    void refusesToDeleteItemWhoseDeliveryStateChangedConcurrently() {
        when(kamiItemMapper.selectById(11L)).thenReturn(item(11L, 7L, 0));
        when(kamiItemMapper.deleteIfNotPending(11L)).thenReturn(0);

        ResultObject<Void> result = service.deleteKamiItem(11L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("发货处理中"));
        verify(kamiConfigMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesToDeleteDeliveredItemWithoutMatchingUsageHistory() {
        XianyuKamiItem item = item(11L, 7L, 1);
        when(kamiItemMapper.selectById(11L)).thenReturn(item);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config(7L));
        when(kamiUsageRecordMapper.countCurrentDeliveryHistory(11L, 7L, null)).thenReturn(0);

        ResultObject<Void> result = service.deleteKamiItem(11L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("缺少对应历史凭证"));
        verify(kamiItemMapper, never()).deleteIfNotPending(11L);
    }

    @Test
    void refusesToDeleteConfigUntilHistoryDeletionIsConfirmed() {
        when(kamiConfigMapper.lockById(7L)).thenReturn(config(7L));
        when(kamiUsageRecordMapper.countByConfigId(7L)).thenReturn(3L);

        ResultObject<KamiConfigDeleteRespDTO> result = service.deleteConfig(7L, false);

        assertEquals(409, result.getCode());
        assertEquals(3L, result.getData().getHistoryCount());
        assertTrue(result.getData().getHistoryDeletionRequired());
        verify(kamiConfigMapper, never()).deleteById(7L);
    }

    @Test
    void archivesOnlyDeliveredItemsWithMatchingUsageHistory() {
        XianyuKamiConfig config = config(7L);
        when(kamiConfigMapper.lockById(7L)).thenReturn(config);
        when(kamiItemMapper.countByConfigIdAndStatus(7L, 1)).thenReturn(2);
        when(kamiItemMapper.countByConfigIdAndStatus(7L, 2)).thenReturn(1);
        when(kamiItemMapper.countByConfigIdAndStatus(7L, 3)).thenReturn(1);
        when(kamiUsageRecordMapper.findArchivableDeliveredItemIds(7L)).thenReturn(java.util.List.of(11L));
        when(kamiItemMapper.deleteArchivableDelivered(7L, java.util.List.of(11L))).thenReturn(1);

        ResultObject<KamiArchiveResultDTO> result = service.archiveUsedKamiItems(7L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getArchivedCount());
        assertEquals(1, result.getData().getSkippedMissingHistoryCount());
        verify(kamiItemMapper).deleteArchivableDelivered(7L, java.util.List.of(11L));
    }

    @Test
    void resettingDeliveredItemRefreshesInventoryCounts() {
        XianyuKamiItem item = item(11L, 7L, 1);
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        when(kamiItemMapper.selectById(11L)).thenReturn(item);
        when(kamiItemMapper.markUnused(11L)).thenReturn(1);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(4);
        when(kamiItemMapper.countUsed(7L)).thenReturn(0);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);

        ResultObject<Void> result = service.resetKamiItem(11L);

        assertEquals(200, result.getCode());
        assertEquals(0, config.getUsedCount());
        verify(kamiConfigMapper).updateById(config);
    }

    @Test
    void sendsStockAlertOnlyWhenPersistedStateCrossesThreshold() {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        config.setAlertEnabled(1);
        config.setAlertThresholdType(1);
        config.setAlertThresholdValue(3);
        config.setAlertState(0);
        when(kamiConfigMapper.lockById(7L)).thenReturn(config);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(10);
        when(kamiItemMapper.countUsed(7L)).thenReturn(8);
        when(kamiItemMapper.countUnused(7L)).thenReturn(2, 2, 8, 2);

        ReflectionTestUtils.invokeMethod(service, "refreshConfigCounts", 7L);
        ReflectionTestUtils.invokeMethod(service, "refreshConfigCounts", 7L);
        assertEquals(1, config.getAlertState());
        verify(notificationChannelService, times(1))
                .dispatchMessage(eq("KAMI_STOCK_ALERT"), isNull(), any());

        ReflectionTestUtils.invokeMethod(service, "refreshConfigCounts", 7L);
        assertEquals(0, config.getAlertState());
        ReflectionTestUtils.invokeMethod(service, "refreshConfigCounts", 7L);
        verify(notificationChannelService, times(2))
                .dispatchMessage(eq("KAMI_STOCK_ALERT"), isNull(), any());
    }

    @Test
    void stockAlertNotificationFailureDoesNotFailInventoryRefresh() {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        config.setAlertEnabled(1);
        config.setAlertThresholdType(1);
        config.setAlertThresholdValue(3);
        when(kamiConfigMapper.lockById(7L)).thenReturn(config);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(10);
        when(kamiItemMapper.countUsed(7L)).thenReturn(10);
        when(kamiItemMapper.countUnused(7L)).thenReturn(0);
        doThrow(new IllegalStateException("executor unavailable")).when(notificationChannelService)
                .dispatchMessage(eq("KAMI_STOCK_ALERT"), isNull(), any());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "refreshConfigCounts", 7L));
        assertEquals(1, config.getAlertState());
        verify(kamiConfigMapper).updateById(config);
    }

    @Test
    void persistsLowStockStateWhenReservationCannotBeSatisfied() {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        config.setAlertEnabled(1);
        config.setAlertThresholdType(1);
        config.setAlertThresholdValue(3);
        when(kamiConfigMapper.lockById(7L)).thenReturn(config);
        when(kamiItemMapper.lockAvailable(7L, 1)).thenReturn(java.util.List.of());
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(1);
        when(kamiItemMapper.countUsed(7L)).thenReturn(1);
        when(kamiItemMapper.countUnused(7L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.reserveKami(7L, "order-1", 1));

        assertEquals(1, config.getAlertState());
        verify(kamiConfigMapper).updateById(config);
        verify(notificationChannelService).dispatchMessage(eq("KAMI_STOCK_ALERT"), isNull(), any());
    }

    @Test
    void releasingReservationRefreshesAndCanResetRecoveredAlertState() {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        config.setAlertEnabled(1);
        config.setAlertThresholdType(1);
        config.setAlertThresholdValue(3);
        config.setAlertState(1);
        XianyuKamiItem item = item(11L, 7L, 2);
        when(kamiItemMapper.findByOrderAndStatus("order-1", 2)).thenReturn(java.util.List.of(item));
        when(kamiItemMapper.releaseReservation("order-1")).thenReturn(1);
        when(kamiConfigMapper.lockById(7L)).thenReturn(config);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(5);
        when(kamiItemMapper.countUsed(7L)).thenReturn(1);
        when(kamiItemMapper.countUnused(7L)).thenReturn(4);

        service.releaseReservation("order-1");

        assertEquals(0, config.getAlertState());
        verify(kamiItemMapper).releaseReservation("order-1");
        verify(kamiConfigMapper).updateById(config);
    }

    @Test
    void freshRedeliveryCommitsReservationToOriginalBusinessOrder() {
        XianyuKamiItem item = item(11L, 7L, 2);
        item.setKamiContent("NEW-CARD");
        XianyuAccount account = new XianyuAccount();
        account.setId(3L);
        when(accountMapper.lockById(3L)).thenReturn(account);
        when(kamiItemMapper.findByOrderAndStatus("order-1#R#attempt", 2)).thenReturn(java.util.List.of(item));
        when(kamiItemMapper.commitReservation("order-1#R#attempt", "order-1")).thenReturn(1);
        when(kamiUsageRecordMapper.findMaxDeliveryIndex(3L, "order-1")).thenReturn(4);

        service.commitReservation("order-1#R#attempt", "order-1", 3L, "goods-1", "buyer-1", "买家");

        ArgumentCaptor<XianyuKamiUsageRecord> captor = ArgumentCaptor.forClass(XianyuKamiUsageRecord.class);
        verify(kamiUsageRecordMapper).insert(captor.capture());
        assertEquals("order-1", captor.getValue().getOrderId());
        assertEquals("NEW-CARD", captor.getValue().getKamiContent());
        verify(kamiItemMapper).commitReservation("order-1#R#attempt", "order-1");
        assertEquals(5, captor.getValue().getDeliveryIndex());
    }

    @Test
    void allocatesAContiguousDeliveryIndexRangeAfterExistingHistory() {
        XianyuKamiItem first = item(11L, 7L, 2);
        first.setKamiContent("FIRST");
        XianyuKamiItem second = item(12L, 7L, 2);
        second.setKamiContent("SECOND");
        XianyuAccount account = new XianyuAccount();
        account.setId(3L);
        when(accountMapper.lockById(3L)).thenReturn(account);
        when(kamiItemMapper.findByOrderAndStatus("reservation-2", 2))
                .thenReturn(java.util.List.of(first, second));
        when(kamiItemMapper.commitReservation("reservation-2", "order-1")).thenReturn(2);
        when(kamiUsageRecordMapper.findMaxDeliveryIndex(3L, "order-1")).thenReturn(7);

        service.commitReservation("reservation-2", "order-1", 3L, "goods-1", "buyer-1", "买家");

        ArgumentCaptor<XianyuKamiUsageRecord> captor = ArgumentCaptor.forClass(XianyuKamiUsageRecord.class);
        verify(kamiUsageRecordMapper, times(2)).insert(captor.capture());
        assertEquals(java.util.List.of(8, 9), captor.getAllValues().stream()
                .map(XianyuKamiUsageRecord::getDeliveryIndex).toList());
    }

    private XianyuKamiConfig config(Long id) {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(id);
        config.setSourceType(1);
        return config;
    }

    private XianyuKamiItem item(Long id, Long configId, int status) {
        XianyuKamiItem item = new XianyuKamiItem();
        item.setId(id);
        item.setKamiConfigId(configId);
        item.setStatus(status);
        return item;
    }
}
