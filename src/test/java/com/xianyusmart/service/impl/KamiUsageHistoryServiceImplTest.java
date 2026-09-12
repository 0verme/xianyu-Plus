package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.KamiUsageHistoryDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryPageDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryQueryReqDTO;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiUsageRecord;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiUsageRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KamiUsageHistoryServiceImplTest {

    @Mock
    private XianyuKamiUsageRecordMapper usageRecordMapper;
    @Mock
    private XianyuKamiConfigMapper kamiConfigMapper;
    @InjectMocks
    private KamiUsageHistoryServiceImpl service;

    @Test
    void listsMaskedContentAndReadsFullContentOnlyInDetail() {
        XianyuKamiConfig config = localConfig(7L);
        XianyuKamiUsageRecord record = record(11L, 7L, "ABCDEFGH");
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);
        when(usageRecordMapper.countHistory(7L, null, null, null, null, null, null))
                .thenReturn(1L);
        when(usageRecordMapper.selectHistoryPage(7L, null, null, null, null, null, null, 20, 0))
                .thenReturn(List.of(record));

        KamiUsageHistoryQueryReqDTO request = new KamiUsageHistoryQueryReqDTO();
        request.setKamiConfigId(7L);
        ResultObject<KamiUsageHistoryPageDTO> pageResult = service.query(request);

        assertEquals(200, pageResult.getCode());
        assertEquals("AB****GH", pageResult.getData().getRecords().getFirst().getKamiContent());
        assertEquals(false, pageResult.getData().getRecords().getFirst().getContentRevealed());

        when(usageRecordMapper.selectById(11L)).thenReturn(record);
        ResultObject<KamiUsageHistoryDTO> detailResult = service.getDetail(11L);

        assertEquals(200, detailResult.getCode());
        assertEquals("ABCDEFGH", detailResult.getData().getKamiContent());
        assertEquals(true, detailResult.getData().getContentRevealed());
    }

    @Test
    void rejectsExternalConfigHistoryQueries() {
        XianyuKamiConfig config = localConfig(7L);
        config.setSourceType(2);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);

        KamiUsageHistoryQueryReqDTO request = new KamiUsageHistoryQueryReqDTO();
        request.setKamiConfigId(7L);

        ResultObject<KamiUsageHistoryPageDTO> result = service.query(request);

        assertEquals(500, result.getCode());
        assertEquals("本地卡密使用历史仅适用于本地库存卡券库", result.getMsg());
    }

    private XianyuKamiConfig localConfig(Long id) {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(id);
        config.setSourceType(1);
        return config;
    }

    private XianyuKamiUsageRecord record(Long id, Long configId, String content) {
        XianyuKamiUsageRecord record = new XianyuKamiUsageRecord();
        record.setId(id);
        record.setKamiConfigId(configId);
        record.setKamiContent(content);
        record.setOrderId("order-1");
        record.setDeliveryIndex(1);
        record.setDeliveryStatus("DELIVERED");
        return record;
    }
}
