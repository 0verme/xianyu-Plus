package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地卡密使用历史分页查询请求。
 */
@Data
public class KamiUsageHistoryQueryReqDTO {

    private Long kamiConfigId;

    private String orderId;

    private String buyerKeyword;

    private String goodsId;

    private String deliveryStatus;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
