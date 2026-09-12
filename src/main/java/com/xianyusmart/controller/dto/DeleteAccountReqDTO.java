package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 删除账号请求DTO
 */
@Data
public class DeleteAccountReqDTO {
    private Long accountId;       // 账号ID

    /** 有本地卡密使用历史时，是否明确确认永久删除历史。 */
    private Boolean confirmHistoryDeletion = false;
}