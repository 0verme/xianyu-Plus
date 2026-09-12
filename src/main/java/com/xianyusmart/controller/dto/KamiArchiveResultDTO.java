package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 已使用卡券安全归档执行结果。
 */
@Data
public class KamiArchiveResultDTO {

    private Integer archivedCount;

    private Integer skippedMissingHistoryCount;
}
