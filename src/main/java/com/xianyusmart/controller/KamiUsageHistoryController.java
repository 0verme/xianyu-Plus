package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.KamiUsageHistoryDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryPageDTO;
import com.xianyusmart.controller.dto.KamiUsageHistoryQueryReqDTO;
import com.xianyusmart.service.KamiUsageHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地卡密使用历史查询接口。
 *
 * <p>列表接口只返回脱敏卡密；详情接口才返回完整卡密内容。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/kami-usage-history")
public class KamiUsageHistoryController {

    @Autowired
    private KamiUsageHistoryService usageHistoryService;

    @PostMapping("/page")
    public ResultObject<KamiUsageHistoryPageDTO> queryPage(
            @RequestBody KamiUsageHistoryQueryReqDTO request) {
        try {
            return usageHistoryService.query(request);
        } catch (Exception e) {
            log.error("查询卡密使用历史失败", e);
            return ResultObject.failed("查询卡密使用历史失败: " + e.getMessage());
        }
    }

    @PostMapping("/detail")
    public ResultObject<KamiUsageHistoryDTO> getDetail(@RequestParam("id") Long id) {
        try {
            return usageHistoryService.getDetail(id);
        } catch (Exception e) {
            log.error("查询卡密使用历史详情失败, id={}", id, e);
            return ResultObject.failed("查询卡密使用历史详情失败: " + e.getMessage());
        }
    }
}
