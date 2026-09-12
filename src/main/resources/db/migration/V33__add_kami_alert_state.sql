-- 持久化库存预警是否已进入低库存区间，避免应用重启或多实例重复通知。
ALTER TABLE xianyu_kami_config
    ADD COLUMN alert_state TINYINT NOT NULL DEFAULT 0
        COMMENT '库存预警状态：0正常，1已进入预警区间'
        AFTER alert_email;
