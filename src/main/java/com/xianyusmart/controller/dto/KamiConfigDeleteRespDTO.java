package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 卡券库删除响应，同时承载需要显式确认历史删除时的提示信息。
 */
@Data
public class KamiConfigDeleteRespDTO {

    private String message;

    private Long historyCount;

    private Boolean historyDeletionRequired;
}
