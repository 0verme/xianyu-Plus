package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.*;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsAutoDeliveryConfig;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiItem;
import com.xianyusmart.entity.XianyuKamiUsageRecord;
import com.xianyusmart.enums.KamiStatus;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import com.xianyusmart.mapper.XianyuKamiUsageRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.service.KamiConfigService;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.service.ApiKamiDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KamiConfigServiceImpl implements KamiConfigService {

    @Autowired
    private XianyuKamiConfigMapper kamiConfigMapper;

    @Autowired
    private XianyuKamiItemMapper kamiItemMapper;

    @Autowired
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;

    @Autowired(required = false)
    private NotificationChannelService notificationChannelService;

    @Autowired
    private ApiKamiDeliveryService apiKamiDeliveryService;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    private static final String STOCK_ALERT_EVENT = "KAMI_STOCK_ALERT";

    private void markTransactionRollbackOnly() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public ResultObject<KamiConfigRespDTO> createOrUpdateConfig(KamiConfigReqDTO reqDTO) {
        try {
            XianyuKamiConfig config;
            if (reqDTO.getId() != null) {
                config = lockConfig(reqDTO.getId());
                if (config == null) {
                    return ResultObject.failed("卡密配置不存在");
                }
            } else {
                config = new XianyuKamiConfig();
                // 新建卡券库不再绑定账号；历史记录中的账号字段仅为兼容旧数据和备份而保留。
                config.setXianyuAccountId(null);
                config.setTotalCount(0);
                config.setUsedCount(0);
            }
            Integer previousSourceType = config.getSourceType();
            Integer previousAlertEnabled = config.getAlertEnabled();
            Integer previousThresholdType = config.getAlertThresholdType();
            Integer previousThresholdValue = config.getAlertThresholdValue();
            if (reqDTO.getAliasName() != null) {
                config.setAliasName(reqDTO.getAliasName());
            }
            if (reqDTO.getSourceType() != null) {
                if (reqDTO.getSourceType() != 1 && reqDTO.getSourceType() != 2 && reqDTO.getSourceType() != 3) {
                    return ResultObject.failed("卡券来源类型不正确");
                }
                config.setSourceType(reqDTO.getSourceType());
            }
            if (reqDTO.getFixedContent() != null) {
                config.setFixedContent(reqDTO.getFixedContent());
            }
            if (reqDTO.getDeliveryTemplate() != null) {
                String deliveryTemplate = reqDTO.getDeliveryTemplate().trim();
                if (!deliveryTemplate.isEmpty()
                        && !deliveryTemplate.contains("{DELIVERY_CONTENT}")
                        && !deliveryTemplate.contains("{kmKey}")) {
                    return ResultObject.failed("发货消息模板必须包含 {DELIVERY_CONTENT} 变量");
                }
                if (deliveryTemplate.length() > 2000) {
                    return ResultObject.failed("发货消息模板不能超过 2000 个字符");
                }
                config.setDeliveryTemplate(reqDTO.getDeliveryTemplate());
            }
            if (reqDTO.getApiUrl() != null) {
                config.setApiUrl(reqDTO.getApiUrl());
            }
            if (reqDTO.getApiMethod() != null) {
                config.setApiMethod(reqDTO.getApiMethod());
            }
            if (reqDTO.getApiHeaders() != null) {
                config.setApiHeaders(reqDTO.getApiHeaders());
            }
            if (reqDTO.getApiRequestTemplate() != null) {
                config.setApiRequestTemplate(reqDTO.getApiRequestTemplate());
            }
            if (reqDTO.getApiResultPath() != null) {
                config.setApiResultPath(reqDTO.getApiResultPath());
            }
            if (reqDTO.getApiTimeoutSeconds() != null) {
                config.setApiTimeoutSeconds(reqDTO.getApiTimeoutSeconds());
            }
            if (reqDTO.getAlertEnabled() != null) {
                config.setAlertEnabled(reqDTO.getAlertEnabled());
                if (!Integer.valueOf(1).equals(reqDTO.getAlertEnabled())) {
                    config.setAlertState(0);
                }
            }
            if (reqDTO.getAlertThresholdType() != null) {
                config.setAlertThresholdType(reqDTO.getAlertThresholdType());
            }
            if (reqDTO.getAlertThresholdValue() != null) {
                config.setAlertThresholdValue(reqDTO.getAlertThresholdValue());
            }
            if (reqDTO.getAlertEmail() != null) {
                config.setAlertEmail(reqDTO.getAlertEmail());
            }

            // 来源或预警边界变更后，下一次库存刷新应重新认领阈值跨越。
            if (!java.util.Objects.equals(previousSourceType, config.getSourceType())
                    || !java.util.Objects.equals(previousAlertEnabled, config.getAlertEnabled())
                    || !java.util.Objects.equals(previousThresholdType, config.getAlertThresholdType())
                    || !java.util.Objects.equals(previousThresholdValue, config.getAlertThresholdValue())) {
                config.setAlertState(0);
            }

            if (Integer.valueOf(2).equals(config.getSourceType())) {
                KamiApiTestReqDTO apiConfig = new KamiApiTestReqDTO();
                apiConfig.setApiUrl(config.getApiUrl());
                apiConfig.setApiMethod(config.getApiMethod());
                apiConfig.setApiHeaders(config.getApiHeaders());
                apiConfig.setApiRequestTemplate(config.getApiRequestTemplate());
                apiConfig.setApiResultPath(config.getApiResultPath());
                apiConfig.setApiTimeoutSeconds(config.getApiTimeoutSeconds());
                // 复用测试入口的校验逻辑；不会实际请求外部接口。
                validateApiConfig(apiConfig);
            } else if (Integer.valueOf(3).equals(config.getSourceType())) {
                validateFixedContent(config.getFixedContent());
            }
            if (reqDTO.getId() != null) {
                kamiConfigMapper.updateById(config);
            } else {
                kamiConfigMapper.insert(config);
            }
            return ResultObject.success(toConfigRespDTO(config));
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("创建/更新卡密配置失败", e);
            return ResultObject.failed("创建/更新卡密配置失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<KamiConfigRespDTO>> getConfigs() {
        try {
            List<XianyuKamiConfig> configs = kamiConfigMapper.findAllByCreateTime();
            List<KamiConfigRespDTO> result = configs.stream()
                    .map(this::toConfigRespDTO)
                    .collect(Collectors.toList());
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询卡密配置列表失败", e);
            return ResultObject.failed("查询卡密配置列表失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<KamiConfigRespDTO> getConfigById(Long id) {
        try {
            XianyuKamiConfig config = kamiConfigMapper.selectById(id);
            if (config == null) {
                return ResultObject.failed("卡密配置不存在");
            }
            return ResultObject.success(toConfigRespDTO(config));
        } catch (Exception e) {
            log.error("查询卡密配置失败", e);
            return ResultObject.failed("查询卡密配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Void> deleteConfig(Long id) {
        ResultObject<KamiConfigDeleteRespDTO> result = deleteConfig(id, false);
        return new ResultObject<>(result.getCode(), result.getMsg(), null);
    }

    @Override
    @Transactional
    public ResultObject<KamiConfigDeleteRespDTO> deleteConfig(Long id, boolean confirmHistoryDeletion) {
        try {
            XianyuKamiConfig config = lockConfig(id);
            if (config == null) {
                return ResultObject.failed("卡密配置不存在");
            }

            long historyCount = kamiUsageRecordMapper.countByConfigId(id);
            if (historyCount > 0 && !confirmHistoryDeletion) {
                KamiConfigDeleteRespDTO response = new KamiConfigDeleteRespDTO();
                response.setHistoryCount(historyCount);
                response.setHistoryDeletionRequired(true);
                String message = "该卡券库存在 " + historyCount
                        + " 条本地卡密使用历史。继续删除将同时永久删除这些历史记录，"
                        + "且当前 Web 逻辑备份无法恢复。请明确确认后重试。";
                response.setMessage(message);
                return ResultObject.failed(409, message, response);
            }

            detachConfigFromRelatedGoods(id);
            List<XianyuKamiItem> items = kamiItemMapper.findByConfigId(id);
            for (XianyuKamiItem item : items) {
                kamiItemMapper.deleteById(item.getId());
            }
            kamiConfigMapper.deleteById(id);

            KamiConfigDeleteRespDTO response = new KamiConfigDeleteRespDTO();
            response.setMessage("卡券库删除成功");
            response.setHistoryCount(historyCount);
            response.setHistoryDeletionRequired(false);
            return ResultObject.success(response);
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("删除卡密配置失败, configId={}", id, e);
            return ResultObject.failed("删除卡密配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<KamiItemRespDTO> addKamiItem(KamiItemReqDTO reqDTO) {
        try {
            XianyuKamiConfig config = lockConfig(reqDTO.getKamiConfigId());
            if (config == null) {
                return ResultObject.failed("卡密配置不存在");
            }
            if (!Integer.valueOf(1).equals(config.getSourceType())) {
                return ResultObject.failed("只有“本地库存卡密”类型可以添加单条卡密");
            }
            XianyuKamiItem item = new XianyuKamiItem();
            item.setKamiConfigId(reqDTO.getKamiConfigId());
            String content = reqDTO.getKamiContent().trim();
            item.setKamiContent(content);
            item.setStatus(0);
            item.setSortOrder(kamiItemMapper.nextSortOrder(reqDTO.getKamiConfigId()));

            boolean duplicated = kamiItemMapper.countByConfigIdAndContent(reqDTO.getKamiConfigId(), content) > 0;
            if (duplicated) {
                return ResultObject.failed("该卡券内容已存在，请勿重复添加");
            }
            kamiItemMapper.insert(item);
            refreshConfigCounts(reqDTO.getKamiConfigId());
            return ResultObject.success(toItemRespDTO(item));
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("添加卡密失败", e);
            return ResultObject.failed("添加卡密失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Integer> batchImportKamiItems(KamiBatchImportReqDTO reqDTO) {
        try {
            XianyuKamiConfig config = lockConfig(reqDTO.getKamiConfigId());
            if (config == null) {
                return ResultObject.failed("卡密配置不存在");
            }
            if (!Integer.valueOf(1).equals(config.getSourceType())) {
                return ResultObject.failed("只有“本地库存卡密”类型可以批量导入");
            }
            String[] lines = reqDTO.getKamiContents().split("\\r?\\n");
            int baseOrder = kamiItemMapper.nextSortOrder(reqDTO.getKamiConfigId());
            int added = 0;
            int duplicated = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                boolean dup = kamiItemMapper.countByConfigIdAndContent(reqDTO.getKamiConfigId(), trimmed) > 0;
                if (dup) {
                    duplicated++;
                    continue;
                }

                XianyuKamiItem item = new XianyuKamiItem();
                item.setKamiConfigId(reqDTO.getKamiConfigId());
                item.setKamiContent(trimmed);
                item.setStatus(0);
                item.setSortOrder(baseOrder + added);
                kamiItemMapper.insert(item);
                added++;
            }
            refreshConfigCounts(reqDTO.getKamiConfigId());
            String msg = duplicated > 0
                    ? String.format("成功导入%d条，已跳过重复%d条", added, duplicated)
                    : String.format("成功导入%d条", added);
            return ResultObject.success(added, msg);
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("批量导入卡密失败", e);
            return ResultObject.failed("批量导入卡密失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigId(Long kamiConfigId) {
        try {
            List<XianyuKamiItem> items = kamiItemMapper.findByConfigId(kamiConfigId);
            List<KamiItemRespDTO> result = items.stream()
                    .map(this::toItemRespDTO)
                    .collect(Collectors.toList());
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询卡密列表失败", e);
            return ResultObject.failed("查询卡密列表失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigIdWithFilter(KamiItemQueryReqDTO reqDTO) {
        try {
            List<XianyuKamiItem> items = kamiItemMapper.findByConfigIdWithFilter(
                    reqDTO.getKamiConfigId(), 
                    reqDTO.getStatus(), 
                    reqDTO.getKeyword());
            List<KamiItemRespDTO> result = items.stream()
                    .map(this::toItemRespDTO)
                    .collect(Collectors.toList());
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询卡密列表失败", e);
            return ResultObject.failed("查询卡密列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Void> deleteKamiItem(Long id) {
        try {
            XianyuKamiItem item = kamiItemMapper.selectById(id);
            if (item == null) {
                return ResultObject.failed("卡密不存在");
            }
            lockConfig(item.getKamiConfigId());
            if (KamiStatus.DELIVERED.getCode() == item.getStatus()
                    && !hasCurrentDeliveryHistory(item)) {
                return ResultObject.failed("已使用卡券缺少对应历史凭证，未删除");
            }
            int rows = kamiItemMapper.deleteIfNotPending(id);
            if (rows == 0) {
                return ResultObject.failed("卡券正在发货处理中，暂时不能删除，请稍后重试");
            }
            refreshConfigCounts(item.getKamiConfigId());
            return ResultObject.success(null);
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("删除卡密失败", e);
            return ResultObject.failed("删除卡密失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Integer> batchDeleteKamiItems(List<Long> ids) {
        List<Long> itemIds = normalizeKamiItemIds(ids);
        if (itemIds.isEmpty()) {
            return ResultObject.failed("请先选择至少一张可删除卡券");
        }
        List<XianyuKamiItem> items = kamiItemMapper.selectBatchIds(itemIds);
        lockInventoryConfigs(items);

        List<Long> safeItemIds = new ArrayList<>();
        int missingHistory = 0;
        for (XianyuKamiItem item : items) {
            if (KamiStatus.DELIVERED.getCode() == item.getStatus()
                    && !hasCurrentDeliveryHistory(item)) {
                missingHistory++;
                continue;
            }
            safeItemIds.add(item.getId());
        }

        int deleted = safeItemIds.isEmpty() ? 0 : kamiItemMapper.deleteBatchIfNotPending(safeItemIds);
        refreshKamiItemConfigCounts(items);
        String message = "已删除 " + deleted + " 张卡券；发货处理中的卡券已跳过";
        if (missingHistory > 0) {
            message += "；" + missingHistory + " 张已使用卡券因缺少对应历史凭证已跳过";
        }
        return ResultObject.success(deleted, message);
    }

    @Override
    @Transactional
    public ResultObject<Integer> batchResetKamiItems(List<Long> ids) {
        List<Long> itemIds = normalizeKamiItemIds(ids);
        if (itemIds.isEmpty()) {
            return ResultObject.failed("请先选择至少一张可重置卡券");
        }
        List<XianyuKamiItem> items = kamiItemMapper.selectBatchIds(itemIds);
        lockInventoryConfigs(items);
        int reset = kamiItemMapper.markUnusedBatch(itemIds);
        refreshKamiItemConfigCounts(items);
        return ResultObject.success(reset, "已重置 " + reset + " 张卡券；未使用或发货处理中的卡券已跳过");
    }

    private List<Long> normalizeKamiItemIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(500)
                .toList();
    }

    private void refreshKamiItemConfigCounts(List<XianyuKamiItem> items) {
        if (items == null) {
            return;
        }
        items.stream()
                .map(XianyuKamiItem::getKamiConfigId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(this::refreshConfigCounts);
    }
    @Override
    @Transactional
    public ResultObject<Integer> clearUsedKamiItems(Long kamiConfigId) {
        ResultObject<KamiArchiveResultDTO> result = archiveUsedKamiItems(kamiConfigId);
        if (!Integer.valueOf(200).equals(result.getCode())) {
            return new ResultObject<>(result.getCode(), result.getMsg(), null);
        }
        KamiArchiveResultDTO data = result.getData();
        return ResultObject.success(data == null || data.getArchivedCount() == null ? 0 : data.getArchivedCount(),
                result.getMsg());
    }

    @Override
    @Transactional
    public ResultObject<KamiArchivePreviewDTO> previewUsedKamiItems(Long kamiConfigId) {
        try {
            XianyuKamiConfig config = requireLocalConfig(kamiConfigId);
            if (config == null) {
                return ResultObject.failed("卡券库不存在");
            }

            KamiArchivePreviewDTO preview = buildArchivePreview(kamiConfigId);
            return ResultObject.success(preview);
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("预览已使用卡券归档失败, configId={}", kamiConfigId, e);
            return ResultObject.failed("预览已使用卡券归档失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<KamiArchiveResultDTO> archiveUsedKamiItems(Long kamiConfigId) {
        try {
            XianyuKamiConfig config = requireLocalConfig(kamiConfigId);
            if (config == null) {
                return ResultObject.failed("卡券库不存在");
            }

            KamiArchivePreviewDTO preview = buildArchivePreview(kamiConfigId);
            List<Long> archivableIds = kamiUsageRecordMapper.findArchivableDeliveredItemIds(kamiConfigId);
            if (archivableIds == null) {
                archivableIds = List.of();
            }
            int archived = archivableIds.isEmpty()
                    ? 0
                    : kamiItemMapper.deleteArchivableDelivered(kamiConfigId, archivableIds);
            refreshConfigCounts(kamiConfigId);

            KamiArchiveResultDTO result = new KamiArchiveResultDTO();
            result.setArchivedCount(archived);
            result.setSkippedMissingHistoryCount(preview.getMissingHistoryCount());
            String message = "已安全归档 " + archived + " 条已使用卡券，使用历史仍然保留";
            if (preview.getMissingHistoryCount() > 0) {
                message += "；" + preview.getMissingHistoryCount()
                        + " 条因缺少当前交付历史凭证已跳过";
            }
            return ResultObject.success(result, message);
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("执行已使用卡券归档失败, configId={}", kamiConfigId, e);
            return ResultObject.failed("执行已使用卡券归档失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Void> resetKamiItem(Long id) {
        try {
            XianyuKamiItem item = kamiItemMapper.selectById(id);
            if (item == null) {
                return ResultObject.failed("卡密不存在");
            }
            lockConfig(item.getKamiConfigId());
            int rows = kamiItemMapper.markUnused(id);
            if (rows == 0) {
                return ResultObject.failed("卡密状态重置失败，可能已是未使用状态");
            }
            refreshConfigCounts(item.getKamiConfigId());
            return ResultObject.success(null);
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("重置卡密状态失败", e);
            return ResultObject.failed("重置卡密状态失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public XianyuKamiItem acquireKami(Long kamiConfigId, String orderId) {
        try {
            List<XianyuKamiItem> items = reserveKami(kamiConfigId, orderId, 1);
            return items.isEmpty() ? null : items.getFirst();
        } catch (BusinessException e) {
            // 预占失败没有业务写入，但库存计数和预警 edge 状态仍应提交。
            return null;
        }
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public List<XianyuKamiItem> reserveKami(Long kamiConfigId, String orderId, int quantity) {
        if (kamiConfigId == null || orderId == null || orderId.isBlank() || quantity < 1) {
            throw new BusinessException(400, "卡密预占参数无效");
        }

        // 同一卡密库短事务串行预占，避免同订单在租约交叠时重复取卡。
        XianyuKamiConfig config = kamiConfigMapper.lockById(kamiConfigId);
        if (config == null) {
            throw new BusinessException(404, "卡密配置不存在");
        }

        List<XianyuKamiItem> existing = kamiItemMapper.findReservedByOrder(kamiConfigId, orderId);
        if (!existing.isEmpty()) {
            boolean allReserved = existing.stream()
                    .allMatch(item -> item.getStatus() == KamiStatus.RESERVED.getCode());
            if (!allReserved) {
                throw new BusinessException(409, "订单卡密已交付或正在待核对");
            }
            if (existing.size() == quantity) {
                // 兼容进程重启前已存在的预占，重新校准库存和预警状态。
                refreshConfigCounts(kamiConfigId);
                return existing;
            }
            throw new BusinessException(409, "订单卡密数量与已有预占不一致");
        }

        List<XianyuKamiItem> items = kamiItemMapper.lockAvailable(kamiConfigId, quantity);
        if (items.size() != quantity) {
            // 即使没有成功预占，也要把“已进入预警区间”的状态持久化。
            refreshConfigCounts(kamiConfigId);
            throw new BusinessException(409, "卡密库存不足");
        }

        List<Long> itemIds = items.stream().map(XianyuKamiItem::getId).toList();
        if (kamiItemMapper.reserve(itemIds, orderId) != quantity) {
            throw new BusinessException(409, "卡密预占冲突");
        }
        items.forEach(item -> {
            item.setStatus(KamiStatus.RESERVED.getCode());
            item.setOrderId(orderId);
        });
        refreshConfigCounts(kamiConfigId);
        return items;
    }

    @Override
    @Transactional
    public void commitReservation(String reservationOrderId, String businessOrderId, Long accountId,
                                  String xyGoodsId, String buyerUserId, String buyerUserName) {
        List<XianyuKamiItem> reservedItems = kamiItemMapper.findByOrderAndStatus(
                reservationOrderId, KamiStatus.RESERVED.getCode());
        if (reservedItems.isEmpty()) {
            return;
        }

        // 账号行是同一 account + businessOrder 下 deliveryIndex 的稳定数据库锁边界。
        lockDeliveryAccount(accountId);
        // 再锁卡券库，保持与预占事务一致的锁顺序。
        lockInventoryConfigs(reservedItems);
        if (kamiItemMapper.commitReservation(reservationOrderId, businessOrderId) != reservedItems.size()) {
            throw new BusinessException(409, "卡密交付提交冲突");
        }

        int nextDeliveryIndex = Math.max(0,
                kamiUsageRecordMapper.findMaxDeliveryIndex(accountId, businessOrderId));
        for (int index = 0; index < reservedItems.size(); index++) {
            XianyuKamiItem item = reservedItems.get(index);
            XianyuKamiUsageRecord usageRecord = new XianyuKamiUsageRecord();
            usageRecord.setKamiConfigId(item.getKamiConfigId());
            usageRecord.setKamiItemId(item.getId());
            usageRecord.setXianyuAccountId(accountId);
            usageRecord.setXyGoodsId(xyGoodsId);
            usageRecord.setOrderId(businessOrderId);
            usageRecord.setDeliveryIndex(++nextDeliveryIndex);
            usageRecord.setDeliveryStatus(KamiStatus.DELIVERED.name());
            usageRecord.setBuyerUserId(buyerUserId);
            usageRecord.setBuyerUserName(buyerUserName);
            usageRecord.setKamiContent(item.getKamiContent());
            kamiUsageRecordMapper.insert(usageRecord);
        }

        reservedItems.stream().map(XianyuKamiItem::getKamiConfigId).distinct()
                .forEach(this::refreshConfigCounts);
    }

    @Override
    @Transactional
    public void releaseReservation(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        List<XianyuKamiItem> reservedItems = kamiItemMapper.findByOrderAndStatus(
                orderId, KamiStatus.RESERVED.getCode());
        lockInventoryConfigs(reservedItems);
        if (kamiItemMapper.releaseReservation(orderId) > 0) {
            refreshKamiItemConfigCounts(reservedItems);
        }
    }

    @Override
    @Transactional
    public void markReservationReviewRequired(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        List<XianyuKamiItem> reservedItems = kamiItemMapper.findByOrderAndStatus(
                orderId, KamiStatus.RESERVED.getCode());
        lockInventoryConfigs(reservedItems);
        kamiItemMapper.markReservationReviewRequired(orderId);
    }

    @Override
    public XianyuKamiConfig getConfig(Long kamiConfigId) {
        return kamiConfigMapper.selectById(kamiConfigId);
    }

    @Override
    public ResultObject<List<KamiItemRespDTO>> exportKamiItems(KamiExportReqDTO reqDTO) {
        try {
            List<XianyuKamiItem> items = new ArrayList<>();
            boolean includeUnused = reqDTO.getIncludeUnused() != null && reqDTO.getIncludeUnused();
            boolean includeUsed = reqDTO.getIncludeUsed() != null && reqDTO.getIncludeUsed();

            if (includeUnused && includeUsed) {
                items = kamiItemMapper.findByConfigId(reqDTO.getKamiConfigId());
            } else if (includeUnused) {
                items = kamiItemMapper.findByConfigIdAndStatus(reqDTO.getKamiConfigId(), 0);
            } else if (includeUsed) {
                items = kamiItemMapper.findByConfigIdAndStatus(reqDTO.getKamiConfigId(), 1);
            }

            List<KamiItemRespDTO> result = items.stream()
                    .map(this::toItemRespDTO)
                    .collect(Collectors.toList());
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("导出卡密失败", e);
            return ResultObject.failed("导出卡密失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<KamiApiTestRespDTO> testApiConfig(KamiApiTestReqDTO reqDTO) {
        try {
            return ResultObject.success(apiKamiDeliveryService.test(reqDTO));
        } catch (BusinessException e) {
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            log.error("测试外部卡券 API 失败", e);
            return ResultObject.failed("测试外部卡券 API 失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<KamiRelatedGoodsDTO>> getRelatedGoods(Long kamiConfigId) {
        try {
            if (kamiConfigId == null || kamiConfigMapper.selectById(kamiConfigId) == null) {
                return ResultObject.failed("卡券库不存在");
            }

            Map<Long, XianyuAccount> accounts = accountMapper.selectList(null).stream()
                    .collect(Collectors.toMap(XianyuAccount::getId, account -> account, (left, right) -> left));
            List<XianyuGoodsAutoDeliveryConfig> relatedConfigs = autoDeliveryConfigMapper.findDefaultByKamiConfigId(kamiConfigId);
            Set<String> relatedKeys = relatedConfigs.stream()
                    .map(config -> goodsKey(config.getXianyuAccountId(), config.getXyGoodsId()))
                    .collect(Collectors.toSet());
            Map<String, XianyuGoodsAutoDeliveryConfig> defaultConfigs = new HashMap<>();
            for (XianyuGoodsInfo goods : goodsInfoMapper.selectList(null)) {
                XianyuGoodsAutoDeliveryConfig deliveryConfig = autoDeliveryConfigMapper
                        .findByAccountIdAndGoodsIdNoSku(goods.getXianyuAccountId(), goods.getXyGoodId());
                if (deliveryConfig != null) {
                    defaultConfigs.put(goodsKey(goods.getXianyuAccountId(), goods.getXyGoodId()), deliveryConfig);
                }
            }

            List<KamiRelatedGoodsDTO> result = new ArrayList<>();
            for (XianyuGoodsInfo goods : goodsInfoMapper.selectList(null)) {
                String key = goodsKey(goods.getXianyuAccountId(), goods.getXyGoodId());
                XianyuAccount account = accounts.get(goods.getXianyuAccountId());
                KamiRelatedGoodsDTO dto = new KamiRelatedGoodsDTO();
                dto.setXianyuAccountId(goods.getXianyuAccountId());
                dto.setXianyuGoodsId(goods.getId());
                dto.setXyGoodsId(goods.getXyGoodId());
                dto.setAccountNote(account == null || account.getAccountNote() == null || account.getAccountNote().isBlank()
                        ? (account == null ? "未知账号" : account.getUnb()) : account.getAccountNote());
                dto.setGoodsTitle(goods.getTitle());
                dto.setCoverPic(goods.getCoverPic());
                dto.setSoldPrice(goods.getSoldPrice());
                dto.setStatus(goods.getStatus());
                dto.setAssociated(relatedKeys.contains(key));
                dto.setWillReplace(!relatedKeys.contains(key) && defaultConfigs.containsKey(key));
                result.add(dto);
            }
            result.sort(Comparator
                    .comparing((KamiRelatedGoodsDTO item) -> !Boolean.TRUE.equals(item.getAssociated()))
                    .thenComparing(item -> item.getAccountNote() == null ? "" : item.getAccountNote())
                    .thenComparing(item -> item.getGoodsTitle() == null ? "" : item.getGoodsTitle()));
            return ResultObject.success(result);
        } catch (Exception e) {
            log.error("查询卡券库关联商品失败: configId={}", kamiConfigId, e);
            return ResultObject.failed("查询关联商品失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ResultObject<Integer> saveRelatedGoods(KamiRelatedGoodsSaveReqDTO reqDTO) {
        try {
            if (reqDTO == null || reqDTO.getKamiConfigId() == null) {
                return ResultObject.failed("请选择要关联的卡券库");
            }
            XianyuKamiConfig kamiConfig = kamiConfigMapper.selectById(reqDTO.getKamiConfigId());
            if (kamiConfig == null) {
                return ResultObject.failed("卡券库不存在");
            }

            Map<String, XianyuGoodsInfo> goodsByKey = goodsInfoMapper.selectList(null).stream()
                    .collect(Collectors.toMap(goods -> goodsKey(goods.getXianyuAccountId(), goods.getXyGoodId()),
                            goods -> goods, (left, right) -> left));
            Set<String> desiredKeys = new LinkedHashSet<>();
            for (KamiRelatedGoodsDTO dto : reqDTO.getGoods() == null ? List.<KamiRelatedGoodsDTO>of() : reqDTO.getGoods()) {
                if (dto.getXianyuAccountId() == null || dto.getXyGoodsId() == null || dto.getXyGoodsId().isBlank()) {
                    return ResultObject.failed("关联商品信息不完整");
                }
                String key = goodsKey(dto.getXianyuAccountId(), dto.getXyGoodsId());
                if (!goodsByKey.containsKey(key)) {
                    return ResultObject.failed("商品不存在或已被删除：" + dto.getXyGoodsId());
                }
                desiredKeys.add(key);
            }

            List<XianyuGoodsAutoDeliveryConfig> existingLinks = autoDeliveryConfigMapper
                    .findDefaultByKamiConfigId(reqDTO.getKamiConfigId());
            for (XianyuGoodsAutoDeliveryConfig linkedConfig : existingLinks) {
                String key = goodsKey(linkedConfig.getXianyuAccountId(), linkedConfig.getXyGoodsId());
                if (!desiredKeys.contains(key)) {
                    unlinkKamiConfig(linkedConfig, reqDTO.getKamiConfigId());
                }
            }

            for (String key : desiredKeys) {
                XianyuGoodsInfo goods = goodsByKey.get(key);
                bindKamiConfig(goods, reqDTO.getKamiConfigId());
            }
            return ResultObject.success(desiredKeys.size(), "已关联 " + desiredKeys.size() + " 个商品");
        } catch (Exception e) {
            markTransactionRollbackOnly();
            log.error("保存卡券库关联商品失败", e);
            return ResultObject.failed("保存关联商品失败: " + e.getMessage());
        }
    }

    private void bindKamiConfig(XianyuGoodsInfo goods, Long kamiConfigId) {
        XianyuGoodsAutoDeliveryConfig deliveryConfig = autoDeliveryConfigMapper
                .findByAccountIdAndGoodsIdNoSku(goods.getXianyuAccountId(), goods.getXyGoodId());
        if (deliveryConfig == null) {
            deliveryConfig = new XianyuGoodsAutoDeliveryConfig();
            deliveryConfig.setXianyuAccountId(goods.getXianyuAccountId());
            deliveryConfig.setXianyuGoodsId(goods.getId());
            deliveryConfig.setXyGoodsId(goods.getXyGoodId());
            deliveryConfig.setDeliveryMode(2);
            deliveryConfig.setKamiConfigIds(String.valueOf(kamiConfigId));
            deliveryConfig.setKamiDeliveryTemplate("{kmKey}");
            deliveryConfig.setAutoConfirmShipment(0);
            autoDeliveryConfigMapper.insert(deliveryConfig);
        } else {
            deliveryConfig.setXianyuGoodsId(goods.getId());
            deliveryConfig.setDeliveryMode(2);
            deliveryConfig.setKamiConfigIds(String.valueOf(kamiConfigId));
            if (deliveryConfig.getKamiDeliveryTemplate() == null || deliveryConfig.getKamiDeliveryTemplate().isBlank()) {
                deliveryConfig.setKamiDeliveryTemplate("{kmKey}");
            }
            autoDeliveryConfigMapper.updateById(deliveryConfig);
        }
        enableAutoDelivery(goods);
    }

    private void detachConfigFromRelatedGoods(Long kamiConfigId) {
        for (XianyuGoodsAutoDeliveryConfig linkedConfig : autoDeliveryConfigMapper.findDefaultByKamiConfigId(kamiConfigId)) {
            unlinkKamiConfig(linkedConfig, kamiConfigId);
        }
    }

    private void unlinkKamiConfig(XianyuGoodsAutoDeliveryConfig deliveryConfig, Long kamiConfigId) {
        String remainingConfigIds = removeConfigId(deliveryConfig.getKamiConfigIds(), kamiConfigId);
        deliveryConfig.setKamiConfigIds(remainingConfigIds);
        if (remainingConfigIds.isBlank()) {
            // 保留原有文本和其他设置，但关闭自动发货，避免商品继续引用已解绑的卡券库。
            deliveryConfig.setDeliveryMode(1);
        }
        autoDeliveryConfigMapper.updateById(deliveryConfig);
        if (remainingConfigIds.isBlank() && !hasUsableDeliveryConfig(
                deliveryConfig.getXianyuAccountId(), deliveryConfig.getXyGoodsId())) {
            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(
                    deliveryConfig.getXianyuAccountId(), deliveryConfig.getXyGoodsId());
            if (goodsConfig != null) {
                goodsConfig.setXianyuAutoDeliveryOn(0);
                goodsConfigMapper.update(goodsConfig);
            }
        }
    }

    private boolean hasUsableDeliveryConfig(Long accountId, String xyGoodsId) {
        return autoDeliveryConfigMapper.findByAccountIdAndGoodsId(accountId, xyGoodsId).stream().anyMatch(config ->
                (Integer.valueOf(1).equals(config.getDeliveryMode())
                        && config.getAutoDeliveryContent() != null && !config.getAutoDeliveryContent().isBlank())
                        || (Integer.valueOf(2).equals(config.getDeliveryMode())
                        && config.getKamiConfigIds() != null && !config.getKamiConfigIds().isBlank()));
    }

    private void enableAutoDelivery(XianyuGoodsInfo goods) {
        XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(
                goods.getXianyuAccountId(), goods.getXyGoodId());
        if (goodsConfig == null) {
            goodsConfig = new XianyuGoodsConfig();
            goodsConfig.setXianyuAccountId(goods.getXianyuAccountId());
            goodsConfig.setXianyuGoodsId(goods.getId());
            goodsConfig.setXyGoodsId(goods.getXyGoodId());
            goodsConfig.setXianyuAutoDeliveryOn(1);
            goodsConfigMapper.insert(goodsConfig);
        } else if (!Integer.valueOf(1).equals(goodsConfig.getXianyuAutoDeliveryOn())) {
            goodsConfig.setXianyuAutoDeliveryOn(1);
            goodsConfigMapper.update(goodsConfig);
        }
    }

    private String removeConfigId(String configIds, Long kamiConfigId) {
        if (configIds == null || configIds.isBlank()) return "";
        return Arrays.stream(configIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank() && !value.equals(String.valueOf(kamiConfigId)))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String goodsKey(Long accountId, String xyGoodsId) {
        return String.valueOf(accountId) + ":" + (xyGoodsId == null ? "" : xyGoodsId.trim());
    }

    private XianyuKamiConfig lockConfig(Long kamiConfigId) {
        if (kamiConfigId == null) {
            return null;
        }
        XianyuKamiConfig config = kamiConfigMapper.lockById(kamiConfigId);
        if (config == null) {
            // 兼容不支持 FOR UPDATE 的轻量测试替身；生产 MySQL 始终走上面的锁定查询。
            config = kamiConfigMapper.selectById(kamiConfigId);
        }
        return config;
    }

    private void lockInventoryConfigs(List<XianyuKamiItem> items) {
        if (items == null) {
            return;
        }
        items.stream()
                .map(XianyuKamiItem::getKamiConfigId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(this::lockConfig);
    }

    private XianyuKamiConfig requireLocalConfig(Long kamiConfigId) {
        if (kamiConfigId == null) {
            throw new IllegalArgumentException("卡券库不能为空");
        }
        XianyuKamiConfig config = lockConfig(kamiConfigId);
        if (config == null) {
            return null;
        }
        if (!Integer.valueOf(1).equals(config.getSourceType())) {
            throw new IllegalArgumentException("只有本地库存卡券库可以归档已使用卡券");
        }
        return config;
    }

    private KamiArchivePreviewDTO buildArchivePreview(Long kamiConfigId) {
        int delivered = kamiItemMapper.countByConfigIdAndStatus(
                kamiConfigId, KamiStatus.DELIVERED.getCode());
        List<Long> archivableIds = kamiUsageRecordMapper.findArchivableDeliveredItemIds(kamiConfigId);
        int archivable = archivableIds == null ? 0 : archivableIds.size();

        KamiArchivePreviewDTO preview = new KamiArchivePreviewDTO();
        preview.setDeliveredCount(delivered);
        preview.setArchivableCount(archivable);
        preview.setMissingHistoryCount(Math.max(0, delivered - archivable));
        preview.setReservedCount(kamiItemMapper.countByConfigIdAndStatus(
                kamiConfigId, KamiStatus.RESERVED.getCode()));
        preview.setReviewRequiredCount(kamiItemMapper.countByConfigIdAndStatus(
                kamiConfigId, KamiStatus.REVIEW_REQUIRED.getCode()));
        return preview;
    }

    private boolean hasCurrentDeliveryHistory(XianyuKamiItem item) {
        return item != null
                && kamiUsageRecordMapper.countCurrentDeliveryHistory(
                        item.getId(), item.getKamiConfigId(), item.getOrderId()) > 0;
    }

    private void lockDeliveryAccount(Long accountId) {
        if (accountId == null) {
            return;
        }
        XianyuAccount account = accountMapper.lockById(accountId);
        if (account == null) {
            account = accountMapper.selectById(accountId);
        }
        if (account == null) {
            throw new BusinessException(409, "发货账号不存在，无法提交卡密交付");
        }
    }

    private void refreshConfigCounts(Long kamiConfigId) {
        if (kamiConfigId == null) {
            return;
        }

        // 以配置行作为串行化边界，保证多实例下只有一个事务能认领同一次阈值跨越。
        XianyuKamiConfig config = lockConfig(kamiConfigId);
        if (config == null) {
            return;
        }

        int total = kamiItemMapper.countByConfigId(kamiConfigId);
        int used = kamiItemMapper.countUsed(kamiConfigId);
        int available = kamiItemMapper.countUnused(kamiConfigId);
        boolean wasAlerting = Integer.valueOf(1).equals(config.getAlertState());
        boolean shouldAlert = isLowStock(config, available, total);

        config.setTotalCount(total);
        config.setUsedCount(used);
        config.setAlertState(shouldAlert ? 1 : 0);
        kamiConfigMapper.updateById(config);

        if (shouldAlert && !wasAlerting) {
            dispatchStockAlert(config, kamiConfigId, available, total);
        }
    }

    private boolean isLowStock(XianyuKamiConfig config, int available, int total) {
        if (config == null || !Integer.valueOf(1).equals(config.getAlertEnabled())) {
            return false;
        }
        if (config.getSourceType() != null && !Integer.valueOf(1).equals(config.getSourceType())) {
            return false;
        }

        int thresholdType = config.getAlertThresholdType() == null ? 1 : config.getAlertThresholdType();
        int thresholdValue = config.getAlertThresholdValue() == null ? 10 : config.getAlertThresholdValue();
        if (thresholdValue <= 0) {
            return false;
        }
        if (thresholdType == 2) {
            return total > 0 && (long) available * 100L < (long) total * thresholdValue;
        }
        return available < thresholdValue;
    }

    private void dispatchStockAlert(XianyuKamiConfig config, Long kamiConfigId,
                                    int availableCount, int totalCount) {
        if (notificationChannelService == null) {
            log.warn("库存预警通知服务不可用，跳过通知: configId={}", kamiConfigId);
            return;
        }

        String configName = config.getAliasName();
        if (configName == null || configName.isBlank()) {
            configName = "卡密配置" + kamiConfigId;
        }
        int thresholdType = config.getAlertThresholdType() == null ? 1 : config.getAlertThresholdType();
        int thresholdValue = config.getAlertThresholdValue() == null ? 10 : config.getAlertThresholdValue();
        Map<String, Object> params = new HashMap<>();
        params.put("configId", kamiConfigId);
        params.put("configName", configName);
        params.put("availableCount", availableCount);
        params.put("totalCount", totalCount);
        params.put("thresholdType", thresholdType == 2 ? "百分比" : "数量");
        params.put("thresholdValue", thresholdValue);
        params.put("alertEmail", config.getAlertEmail());
        params.put("reason", availableCount == 0 ? "卡密库存已耗尽" : "可用库存低于预设阈值");
        try {
            // dispatchMessage 本身异步执行；即使执行器拒绝任务，也不能回滚库存业务事务。
            notificationChannelService.dispatchMessage(STOCK_ALERT_EVENT, null, params);
        } catch (Exception e) {
            log.error("库存预警通知触发失败，不影响库存业务: configId={}", kamiConfigId, e);
        }
    }

    private KamiConfigRespDTO toConfigRespDTO(XianyuKamiConfig config) {
        KamiConfigRespDTO dto = new KamiConfigRespDTO();
        dto.setId(config.getId());
        dto.setXianyuAccountId(config.getXianyuAccountId());
        dto.setAliasName(config.getAliasName());
        dto.setSourceType(config.getSourceType() == null ? 1 : config.getSourceType());
        dto.setFixedContent(config.getFixedContent());
        dto.setDeliveryTemplate(config.getDeliveryTemplate());
        dto.setApiUrl(config.getApiUrl());
        dto.setApiMethod(config.getApiMethod());
        dto.setApiHeaders(config.getApiHeaders());
        dto.setApiRequestTemplate(config.getApiRequestTemplate());
        dto.setApiResultPath(config.getApiResultPath());
        dto.setApiTimeoutSeconds(config.getApiTimeoutSeconds());
        dto.setAlertEnabled(config.getAlertEnabled());
        dto.setAlertThresholdType(config.getAlertThresholdType());
        dto.setAlertThresholdValue(config.getAlertThresholdValue());
        dto.setAlertEmail(config.getAlertEmail());
        dto.setTotalCount(config.getTotalCount());
        dto.setUsedCount(config.getUsedCount());
        int unused = kamiItemMapper.countUnused(config.getId());
        dto.setAvailableCount(unused);
        dto.setRelatedGoodsCount(autoDeliveryConfigMapper.findDefaultByKamiConfigId(config.getId()).size());
        dto.setCreateTime(config.getCreateTime());
        dto.setUpdateTime(config.getUpdateTime());
        return dto;
    }

    private void validateApiConfig(KamiApiTestReqDTO config) {
        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            throw new BusinessException(400, "请填写外部 API 地址");
        }
        String method = config.getApiMethod() == null ? "POST" : config.getApiMethod().trim().toUpperCase();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new BusinessException(400, "外部 API 目前仅支持 GET 或 POST");
        }
        int timeout = config.getApiTimeoutSeconds() == null ? 10 : config.getApiTimeoutSeconds();
        if (timeout < 3 || timeout > 30) {
            throw new BusinessException(400, "接口超时时间请设置在 3 到 30 秒之间");
        }
        try {
            java.net.URI uri = java.net.URI.create(config.getApiUrl().trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BusinessException(400, "外部 API 地址仅支持 http 或 https");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "外部 API 地址格式不正确");
        }
        validateJsonObject(config.getApiHeaders(), "请求头");
        validateJsonData(config.getApiRequestTemplate(), "请求参数");
    }

    private void validateFixedContent(String fixedContent) {
        if (fixedContent == null || fixedContent.trim().isEmpty()) {
            throw new BusinessException(400, "请填写固定发货内容");
        }
        if (fixedContent.trim().length() > 200) {
            throw new BusinessException(400, "固定发货内容不能超过 200 个字符（闲鱼虚拟发货限制）");
        }
    }

    private void validateJsonObject(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) return;
        try {
            if (!objectMapper.readTree(value).isObject()) {
                throw new BusinessException(400, fieldName + "必须是 JSON 对象");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, fieldName + " JSON 格式不正确");
        }
    }

    private void validateJsonData(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) return;
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isObject() && !node.isArray()) {
                throw new BusinessException(400, fieldName + "必须是 JSON 对象或数组");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, fieldName + " JSON 格式不正确");
        }
    }

    private KamiItemRespDTO toItemRespDTO(XianyuKamiItem item) {
        KamiItemRespDTO dto = new KamiItemRespDTO();
        dto.setId(item.getId());
        dto.setKamiConfigId(item.getKamiConfigId());
        dto.setKamiContent(item.getKamiContent());
        dto.setStatus(item.getStatus());
        dto.setOrderId(item.getOrderId());
        dto.setUsedTime(item.getUsedTime());
        dto.setSortOrder(item.getSortOrder());
        dto.setCreateTime(item.getCreateTime());
        return dto;
    }

}
