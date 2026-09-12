package com.xianyusmart.service.impl;

import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.exception.HistoryDeletionRequiredException;
import com.xianyusmart.mapper.XianyuAiBargainSessionMapper;
import com.xianyusmart.mapper.XianyuBuyerBlacklistMapper;
import com.xianyusmart.mapper.XianyuChatMessageMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
import com.xianyusmart.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuKamiUsageRecordMapper;
import com.xianyusmart.mapper.XianyuOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private XianyuBuyerBlacklistMapper buyerBlacklistMapper;
    @Mock
    private XianyuCookieMapper cookieMapper;
    @Mock
    private XianyuChatMessageMapper chatMessageMapper;
    @Mock
    private XianyuGoodsInfoMapper goodsInfoMapper;
    @Mock
    private XianyuGoodsConfigMapper goodsConfigMapper;
    @Mock
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    @Mock
    private XianyuGoodsOrderMapper orderMapper;
    @Mock
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;
    @Mock
    private XianyuOperationLogMapper operationLogMapper;
    @Mock
    private XianyuAiBargainSessionMapper bargainSessionMapper;
    @Mock
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;

    @InjectMocks
    private AccountServiceImpl service;

    @Test
    void removesAccountScopedBlacklistBeforeDeletingAccount() {
        XianyuAccount account = account(7L);
        when(accountMapper.lockById(7L)).thenReturn(account);
        when(kamiUsageRecordMapper.countByAccountId(7L)).thenReturn(0L);
        when(accountMapper.deleteById(7L)).thenReturn(1);
        when(buyerBlacklistMapper.deleteByAccountId(7L)).thenReturn(1);

        assertTrue(service.deleteAccountAndRelatedData(7L));

        InOrder order = inOrder(buyerBlacklistMapper, accountMapper);
        order.verify(buyerBlacklistMapper).deleteByAccountId(7L);
        order.verify(accountMapper).deleteById(7L);
    }

    @Test
    void requiresExplicitConfirmationBeforeDeletingKamiUsageHistory() {
        when(accountMapper.lockById(7L)).thenReturn(account(7L));
        when(kamiUsageRecordMapper.countByAccountId(7L)).thenReturn(2L);

        HistoryDeletionRequiredException exception = assertThrows(
                HistoryDeletionRequiredException.class,
                () -> service.deleteAccountAndRelatedData(7L, false));

        assertEquals(2L, exception.getHistoryCount());
        verify(accountMapper, never()).deleteById(7L);
        verify(buyerBlacklistMapper, never()).deleteByAccountId(7L);
    }

    private XianyuAccount account(Long id) {
        XianyuAccount account = new XianyuAccount();
        account.setId(id);
        return account;
    }
}
