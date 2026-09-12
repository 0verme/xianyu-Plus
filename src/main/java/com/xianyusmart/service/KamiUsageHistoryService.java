package com.xianyusmart.service;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.KamiUsageHistoryDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryPageDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryQueryReqDTO;

/**
 * 本地卡密使用历史查询服务。
 */
public interface KamiUsageHistoryService {

    ResultObject<KamiUsageHistoryPageDTO> query(KamiUsageHistoryQueryReqDTO request);

    ResultObject<KamiUsageHistoryDTO> getDetail(Long id);
}
