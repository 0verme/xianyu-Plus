package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 已使用卡券安全归档预览结果。
 */
@Data
public class KamiArchivePreviewDTO {

    private Integer deliveredCount;

    private Integer archivableCount;

    private Integer missingHistoryCount;

    private Integer reservedCount;

    private Integer reviewRequiredCount;
}
