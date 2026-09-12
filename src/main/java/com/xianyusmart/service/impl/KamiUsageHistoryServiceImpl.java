package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.KamiUsageHistoryDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryPageDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryQueryReqDTO;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiUsageRecord;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiUsageRecordMapper;
import com.xianyusmart.service.KamiUsageHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地卡密使用历史查询实现。
 *
 * <p>查询只读取 usage record 保存的快照字段，不依赖库存项仍然存在。</p>
 */
@Service
public class KamiUsageHistoryServiceImpl implements KamiUsageHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private XianyuKamiUsageRecordMapper usageRecordMapper;

    @Autowired
    private XianyuKamiConfigMapper kamiConfigMapper;

    @Override
    public ResultObject<KamiUsageHistoryPageDTO> query(KamiUsageHistoryQueryReqDTO request) {
        if (request == null || request.getKamiConfigId() == null) {
            return ResultObject.failed("卡券库不能为空");
        }

        XianyuKamiConfig config = kamiConfigMapper.selectById(request.getKamiConfigId());
        if (config == null) {
            return ResultObject.failed("卡券库不存在");
        }
        if (!Integer.valueOf(1).equals(config.getSourceType())) {
            return ResultObject.failed("本地卡密使用历史仅适用于本地库存卡券库");
        }

        int pageNum = request.getPageNum() == null ? 1 : Math.max(request.getPageNum(), 1);
        int pageSize = normalizePageSize(request.getPageSize());
        long offset = (long) (pageNum - 1) * pageSize;
        String orderId = normalize(request.getOrderId());
        String buyerKeyword = normalize(request.getBuyerKeyword());
        String goodsId = normalize(request.getGoodsId());
        String deliveryStatus = normalize(request.getDeliveryStatus());

        long total = usageRecordMapper.countHistory(
                request.getKamiConfigId(), orderId, buyerKeyword, goodsId, deliveryStatus,
                request.getStartTime(), request.getEndTime());
        List<XianyuKamiUsageRecord> records = usageRecordMapper.selectHistoryPage(
                request.getKamiConfigId(), orderId, buyerKeyword, goodsId, deliveryStatus,
                request.getStartTime(), request.getEndTime(), pageSize, offset);

        KamiUsageHistoryPageDTO response = new KamiUsageHistoryPageDTO();
        response.setRecords(records == null ? Collections.emptyList() : records.stream()
                .map(record -> toDTO(record, false))
                .collect(Collectors.toList()));
        response.setTotal(total);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        return ResultObject.success(response);
    }

    @Override
    public ResultObject<KamiUsageHistoryDTO> getDetail(Long id) {
        if (id == null) {
            return ResultObject.failed("历史记录不能为空");
        }
        XianyuKamiUsageRecord record = usageRecordMapper.selectById(id);
        if (record == null) {
            return ResultObject.failed("使用历史不存在");
        }
        XianyuKamiConfig config = kamiConfigMapper.selectById(record.getKamiConfigId());
        if (config == null || !Integer.valueOf(1).equals(config.getSourceType())) {
            return ResultObject.failed("本地卡密使用历史不存在");
        }
        return ResultObject.success(toDTO(record, true));
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private KamiUsageHistoryDTO toDTO(XianyuKamiUsageRecord record, boolean revealContent) {
        KamiUsageHistoryDTO dto = new KamiUsageHistoryDTO();
        dto.setId(record.getId());
        dto.setKamiConfigId(record.getKamiConfigId());
        dto.setKamiItemId(record.getKamiItemId());
        dto.setOrderId(record.getOrderId());
        dto.setBuyerUserId(record.getBuyerUserId());
        dto.setBuyerUserName(record.getBuyerUserName());
        dto.setGoodsId(record.getXyGoodsId());
        dto.setDeliveryIndex(record.getDeliveryIndex());
        dto.setDeliveryStatus(record.getDeliveryStatus());
        dto.setKamiContent(revealContent ? record.getKamiContent() : maskContent(record.getKamiContent()));
        dto.setContentRevealed(revealContent);
        dto.setDeliveryTime(record.getCreateTime());
        return dto;
    }

    static String maskContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int length = content.length();
        if (length <= 4) {
            return "****";
        }
        int visibleLength = Math.min(4, Math.max(1, length / 3));
        if (visibleLength * 2 >= length) {
            return "****";
        }
        return content.substring(0, visibleLength) + "****"
                + content.substring(length - visibleLength);
    }
}
