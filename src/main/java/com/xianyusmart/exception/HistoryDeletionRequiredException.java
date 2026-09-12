package com.xianyusmart.exception;

/**
 * 删除操作必须先明确确认会永久删除本地卡密使用历史。
 */
public class HistoryDeletionRequiredException extends BusinessException {

    private final long historyCount;

    public HistoryDeletionRequiredException(long historyCount, String message) {
        super(409, message);
        this.historyCount = historyCount;
    }

    public long getHistoryCount() {
        return historyCount;
    }
}
