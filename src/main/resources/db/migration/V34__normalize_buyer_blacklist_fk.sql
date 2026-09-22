-- MySQL 8.4 disallows cascading actions for a foreign key whose child
-- column is referenced by a generated column. Keep account deletion explicit.
ALTER TABLE xianyu_buyer_blacklist
    DROP FOREIGN KEY fk_buyer_blacklist_account;

ALTER TABLE xianyu_buyer_blacklist
    ADD CONSTRAINT fk_buyer_blacklist_account
        FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id)
        ON DELETE RESTRICT;
