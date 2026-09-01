# 数据库结构说明

XianYuPlus 支持 MySQL 8.0 及以上版本，数据库结构由 Flyway 管理。

## 文件位置

- `db/migration/V1__baseline.sql`：全新环境完整基线。
- 后续版本：按 `V2__说明.sql`、`V3__说明.sql` 顺序新增，不修改已发布迁移。

## 初始化流程

1. 创建空数据库并配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
2. 启动应用。
3. Flyway 在业务组件启动前创建表、唯一约束和索引。

## 关键约束

- 订单以账号、消息 ID 和订单 ID 去重。
- 卡密以配置和内容摘要去重。
- 卡密使用记录以账号、订单和交付序号去重。
- 自动回复以账号、会话和消息去重。
- 持久任务按状态、重试时间和租约到期时间领取。

### V21 在 MySQL 8.x 上的升级说明

V21 已随 V1.4.0 发布，并一直包含在后续版本中。原迁移同时把 `xianyu_account_id` 作为 `account_scope` 的 stored generated column 基础列，并为该列配置 `ON DELETE CASCADE`。MySQL 8.0/8.4 禁止这种生成列依赖上的级联外键动作，因此全新数据库会在 V21 失败。修复版本将外键改为 `ON DELETE RESTRICT`；账号删除事务会先删除该账号专属的黑名单记录，NULL 账号的全局黑名单不会被删除。

这里是对已发布 V21 的有意例外：原 DDL 在项目支持的 MySQL 8.x 上无法成功执行，原地修复比再增加一个永远无法到达的后续 migration 更安全。修改 SQL 会改变 Flyway checksum，已有成功记录应使用标准 Flyway `repair` 更新 checksum；失败的 V21 记录会由 `repair` 移除，应用重启后重新执行修复后的 V21。

如果数据库已经留下 `V21 success = 0`：

1. 停止应用并备份数据库；不要直接 UPDATE `flyway_schema_history`。
2. 使用与应用兼容的 Flyway CLI，在能连接数据库的环境中执行 `repair`，并指向当前源码的 migration 目录：

   ```bash
   flyway \
     -url="jdbc:mysql://<mysql-host>:3306/<database>?useSSL=false&allowPublicKeyRetrieval=true" \
     -user="<database-user>" \
     -password="<database-password>" \
     -locations="filesystem:src/main/resources/db/migration" \
     repair
   ```

3. 重新构建并启动应用：`docker compose up -d --build`。

在 MySQL 8.0/8.4 的实测中，失败的 `CREATE TABLE` 不会留下 `xianyu_buyer_blacklist` 半成品，因此不需要盲目执行 `DROP TABLE`；如果现场已经存在该表，应先备份并检查其结构，再决定恢复方案。更新脚本不会代替 Flyway 修改 schema history。

MySQL 官方限制说明：[FOREIGN KEY Constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)。

禁止在生产库手工修改表结构。结构变更必须新增 Flyway 迁移并在空库和已有版本库分别验证。
