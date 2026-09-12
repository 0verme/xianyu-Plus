package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 本地卡密使用历史分页响应。
 */
@Data
public class KamiUsageHistoryPageDTO {

    private List<KamiUsageHistoryDTO> records;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;
}
