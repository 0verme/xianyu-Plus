package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地卡密使用历史记录。
 * 列表接口中的 kamiContent 默认是脱敏内容；详情接口才返回完整内容。
 */
@Data
public class KamiUsageHistoryDTO {

    private Long id;

    private Long kamiConfigId;

    private Long kamiItemId;

    private String orderId;

    private String buyerUserId;

    private String buyerUserName;

    private String goodsId;

    private Integer deliveryIndex;

    private String deliveryStatus;

    private String kamiContent;

    private Boolean contentRevealed;

    private LocalDateTime deliveryTime;
}
