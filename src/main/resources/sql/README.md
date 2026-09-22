# 数据库结构说明

XianYuPlus 支持 MySQL 8.0 及以上版本，数据库结构由 Flyway 管理。

## 文件位置

- `db/migration/V1__baseline.sql`：历史版本的初始迁移，只能读取，不能修改。
- `db/migration/B33__current_schema.sql`：全新空库使用的当前 schema baseline，省略 Flyway history、数据和当前自增值。
- `db/migration/V34__normalize_buyer_blacklist_fk.sql`：已有数据库的前向迁移，将黑名单账号外键规范为 `ON DELETE RESTRICT`。
- 后续版本：按 `V35__说明.sql`、`V36__说明.sql` 顺序新增，不修改已发布 migration。

## 初始化与升级流程

1. 创建空数据库并配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
2. 启动应用。
3. 空库由 Flyway 应用 `B33`，随后执行 `V34`；不会重新执行 V1–V33。
4. 已有 Flyway history 的数据库继续从当前版本升级，按顺序执行未应用的版本。

`B33` 只用于没有 Flyway history 的全新空库。它绕过 MySQL 8.0/8.4 无法执行的历史 V21 DDL，同时提供与升级路径等价的业务 schema。不要把 `B33` 当作已有数据库的修复脚本。

## 关键约束

- 订单以账号、消息 ID 和订单 ID 去重。
- 卡密以配置和内容摘要去重。
- 卡密使用记录以账号、订单和交付序号去重。
- 自动回复以账号、会话和消息去重。
- 持久任务按状态、重试时间和租约到期时间领取。

### V21 与 V34 的不可变迁移策略

已发布的 V21 必须保持原内容和 checksum，其中黑名单账号外键仍为 `ON DELETE CASCADE`。由于 `xianyu_account_id` 同时是 `account_scope` stored generated column 的基础列，MySQL 8.0/8.4 拒绝该级联外键动作；因此全新安装走 `B33`，不再执行 V21。

已有数据库如果已经成功应用 V21，则由 V34 删除并重新创建该外键为 `ON DELETE RESTRICT`。V34 是前向迁移，不修改任何历史 SQL 或 `flyway_schema_history` 记录；黑名单数据会保留，存在关联黑名单时删除账号会被数据库拒绝。

禁止：

- 修改或重命名 V1–V33；
- 直接 UPDATE、DELETE 或 INSERT `flyway_schema_history`；
- 使用 Flyway `repair` 改写历史 checksum；
- 在生产库手工执行本次迁移中的 DDL。

`update.sh` 会在检测到已有 `V21 success = 0` 时停止，不会自动改写 history。此类数据库必须停止应用、保留现场并按生产变更与备份恢复流程处理，不应绕过不可变 migration 保护。

MySQL 官方限制说明：[FOREIGN KEY Constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)。

禁止在生产库手工修改表结构。结构变更必须新增 Flyway migration，并在空库和已有版本库分别验证。
