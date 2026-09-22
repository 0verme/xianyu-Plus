package com.xianyusmart.mapper;

import com.xianyusmart.XianYuPlusApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the published upgrade path and the baseline path against real MySQL 8.4 instances. */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMySqlMigrationIntegrationTest {

    private static final String MYSQL_IMAGE = System.getProperty("mysql.image", "mysql:8.4");

    @Container
    static final MySQLContainer<?> upgradeMysql = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName("xianyusmart")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final MySQLContainer<?> freshMysql = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName("xianyusmart")
            .withUsername("test")
            .withPassword("test");

    @Test
    void upgradesV32AndInitializesFreshDatabaseWithEquivalentSchemas() throws Exception {
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(compatibilityDataSource(upgradeMysql))
                .locations("filesystem:" + legacyMigrationLocation())
                .target("32")
                .validateOnMigrate(true)
                .load();
        legacyFlyway.migrate();
        assertEquals("32", legacyFlyway.info().current().getVersion().getVersion());
        assertEquals(32L, queryLong(upgradeMysql, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(1L, queryLong(upgradeMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21' AND success = 1"));

        String buyerId = "migration-buyer-" + UUID.randomUUID();
        long accountId = insertAccount(upgradeMysql);
        insertBlacklistEntry(upgradeMysql, null, buyerId);
        insertBlacklistEntry(upgradeMysql, accountId, buyerId);

        Flyway currentFlyway = flyway(upgradeMysql, plainDataSource(upgradeMysql));
        MigrationInfo publishedV21 = Arrays.stream(currentFlyway.info().all())
                .filter(info -> info.getVersion() != null && info.getVersion().getVersion().equals("21"))
                .findFirst()
                .orElseThrow();
        assertEquals(publishedV21.getChecksum(),
                queryInteger(upgradeMysql,
                        "SELECT checksum FROM flyway_schema_history WHERE version = '21'"));
        currentFlyway.migrate();
        currentFlyway.validate();

        assertEquals("34", currentFlyway.info().current().getVersion().getVersion());
        assertEquals(34L, queryLong(upgradeMysql, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(1L, queryLong(upgradeMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21' AND success = 1"));
        assertEquals(1L, queryLong(upgradeMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '33' AND success = 1"));
        assertEquals(1L, queryLong(upgradeMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '34' AND success = 1"));
        assertBlacklistForeignKeyIsRestrict(upgradeMysql);
        assertEquals(2L, queryLong(upgradeMysql,
                "SELECT COUNT(*) FROM xianyu_buyer_blacklist WHERE buyer_user_id = ?", buyerId));
        assertThrows(SQLException.class, () -> deleteAccount(upgradeMysql, accountId));

        SchemaSnapshot upgradeSchema = schemaSnapshot(upgradeMysql);
        assertApplicationStarts(upgradeMysql);

        Flyway freshFlyway = flyway(freshMysql, plainDataSource(freshMysql));
        freshFlyway.migrate();
        freshFlyway.validate();

        assertEquals("34", freshFlyway.info().current().getVersion().getVersion());
        assertEquals(2L, queryLong(freshMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        assertEquals(1L, queryLong(freshMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE script = 'B33__current_schema.sql' AND success = 1"));
        assertEquals(1L, queryLong(freshMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '34' AND success = 1"));
        assertEquals(0L, queryLong(freshMysql,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21'"));
        assertBlacklistForeignKeyIsRestrict(freshMysql);
        SchemaSnapshot freshSchema = schemaSnapshot(freshMysql);
        assertEquals(upgradeSchema, freshSchema,
                "Upgrade and fresh-install business schemas must be equivalent");
        assertApplicationStarts(freshMysql);
    }

    private static String legacyMigrationLocation() throws IOException {
        Path sourceDirectory = Path.of("src/main/resources/db/migration");
        Path targetDirectory = Path.of("target/legacy-migrations-v32");
        Files.createDirectories(targetDirectory);
        try (var paths = Files.list(sourceDirectory)) {
            paths.filter(path -> isLegacyVersionedMigration(path.getFileName().toString()))
                    .forEach(path -> copyMigration(path, targetDirectory.resolve(path.getFileName())));
        }
        return targetDirectory.toAbsolutePath().toString();
    }

    private static boolean isLegacyVersionedMigration(String filename) {
        if (!filename.startsWith("V") || !filename.endsWith(".sql")) {
            return false;
        }
        int separator = filename.indexOf("__");
        if (separator < 2) {
            return false;
        }
        try {
            int version = Integer.parseInt(filename.substring(1, separator));
            return version >= 1 && version <= 32;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void copyMigration(Path source, Path target) {
        try {
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to build V32 migration fixture", exception);
        }
    }

    private static Flyway flyway(MySQLContainer<?> mysql, DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load();
    }

    /**
     * Builds a V1..V32 fixture with the published V21 checksum while rewriting only that
     * DDL at the JDBC boundary so MySQL 8.4 can host the historical schema.
     */
    private static DataSource compatibilityDataSource(MySQLContainer<?> mysql) {
        return dataSource(mysql, true);
    }

    private static DataSource plainDataSource(MySQLContainer<?> mysql) {
        return dataSource(mysql, false);
    }

    private static DataSource dataSource(MySQLContainer<?> mysql, boolean rewritePublishedV21) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        Connection connection = args == null || args.length == 0
                                ? DriverManager.getConnection(jdbcUrl(mysql), mysql.getUsername(), mysql.getPassword())
                                : DriverManager.getConnection(jdbcUrl(mysql), (String) args[0], (String) args[1]);
                        return rewritePublishedV21 ? wrapConnection(connection) : connection;
                    }
                    if (method.getName().equals("isWrapperFor")) {
                        return false;
                    }
                    if (method.getName().equals("getParentLogger")) {
                        return java.util.logging.Logger.getGlobal();
                    }
                    if (method.getName().equals("getLoginTimeout")) {
                        return 0;
                    }
                    if (method.getName().equals("getLogWriter")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Connection wrapConnection(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("createStatement")) {
                        return wrapStatement((Statement) invoke(delegate, method, args), (Connection) proxy);
                    }
                    if ((name.equals("prepareStatement") || name.equals("prepareCall"))
                            && args != null && args.length > 0 && args[0] instanceof String) {
                        Object[] rewrittenArgs = args.clone();
                        rewrittenArgs[0] = rewritePublishedV21Sql((String) args[0]);
                        return wrapStatement((Statement) invoke(delegate, method, rewrittenArgs), (Connection) proxy);
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static Object wrapStatement(Statement delegate, Connection connection) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        interfaces.add(Statement.class);
        if (delegate instanceof PreparedStatement) {
            interfaces.add(PreparedStatement.class);
        }
        Class<?>[] proxyInterfaces = interfaces.toArray(Class<?>[]::new);
        return Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                proxyInterfaces,
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ((name.equals("execute") || name.equals("executeUpdate")
                            || name.equals("executeLargeUpdate") || name.equals("addBatch"))
                            && args != null && args.length > 0 && args[0] instanceof String) {
                        Object[] rewrittenArgs = args.clone();
                        rewrittenArgs[0] = rewritePublishedV21Sql((String) args[0]);
                        return invoke(delegate, method, rewrittenArgs);
                    }
                    if (name.equals("getConnection")) {
                        return connection;
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static String rewritePublishedV21Sql(String sql) {
        if (!sql.toLowerCase(Locale.ROOT).contains("create table xianyu_buyer_blacklist")) {
            return sql;
        }
        return sql.replaceAll("(?i)ON\\s+DELETE\\s+CASCADE", "ON DELETE RESTRICT");
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static void assertApplicationStarts(MySQLContainer<?> mysql) {
        try (Connection connection = connection(mysql)) {
            assertTrue(connection.isValid(5));
        } catch (SQLException exception) {
            throw new AssertionError("MySQL container stopped before application startup", exception);
        }

        Properties properties = new Properties();
        properties.put("DB_URL", jdbcUrl(mysql));
        properties.put("DB_USERNAME", mysql.getUsername());
        properties.put("DB_PASSWORD", mysql.getPassword());
        properties.put("JWT_SECRET", "flyway-integration-test-secret-32-bytes-minimum");
        properties.put("spring.datasource.url", jdbcUrl(mysql));
        properties.put("spring.datasource.username", mysql.getUsername());
        properties.put("spring.datasource.password", mysql.getPassword());
        properties.put("spring.flyway.validate-on-migrate", "true");
        properties.put("spring.main.banner-mode", "off");
        properties.put("jwt.secret", "flyway-integration-test-secret-32-bytes-minimum");
        properties.put("ai.enabled", "false");
        properties.put("app.websocket.auto-reconnect-on-startup", "false");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(XianYuPlusApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run()) {
            assertTrue(context.isRunning());
        }
    }

    private static void assertBlacklistForeignKeyIsRestrict(MySQLContainer<?> mysql) throws SQLException {
        assertEquals("RESTRICT", queryString(mysql,
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name = ? AND constraint_name = ?",
                "xianyu_buyer_blacklist", "fk_buyer_blacklist_account"));
    }

    private static long insertAccount(MySQLContainer<?> mysql) throws SQLException {
        try (Connection connection = connection(mysql);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO xianyu_account (account_note, unb) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "migration test");
            statement.setString(2, "migration-account-" + UUID.randomUUID());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static void insertBlacklistEntry(MySQLContainer<?> mysql, Long accountId, String buyerId)
            throws SQLException {
        try (Connection connection = connection(mysql);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO xianyu_buyer_blacklist (xianyu_account_id, buyer_user_id) VALUES (?, ?)")) {
            if (accountId == null) {
                statement.setObject(1, null);
            } else {
                statement.setLong(1, accountId);
            }
            statement.setString(2, buyerId);
            statement.executeUpdate();
        }
    }

    private static void deleteAccount(MySQLContainer<?> mysql, long accountId) throws SQLException {
        try (Connection connection = connection(mysql);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM xianyu_account WHERE id = ?")) {
            statement.setLong(1, accountId);
            statement.executeUpdate();
        }
    }

    private static SchemaSnapshot schemaSnapshot(MySQLContainer<?> mysql) throws SQLException {
        try (Connection connection = connection(mysql)) {
            return new SchemaSnapshot(
                    queryRows(connection,
                            "SELECT table_name, engine, table_collation, table_comment "
                                    + "FROM information_schema.tables "
                                    + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                                    + "AND table_name <> 'flyway_schema_history' "
                                    + "ORDER BY table_name"),
                    queryRows(connection,
                            "SELECT table_name, ordinal_position, column_name, column_type, is_nullable, "
                                    + "COALESCE(column_default, '<NULL>'), COALESCE(extra, '<NULL>'), "
                                    + "COALESCE(generation_expression, '<NULL>'), "
                                    + "COALESCE(character_set_name, '<NULL>'), COALESCE(collation_name, '<NULL>') "
                                    + "FROM information_schema.columns "
                                    + "WHERE table_schema = DATABASE() ORDER BY table_name, ordinal_position"),
                    queryRows(connection,
                            "SELECT table_name, index_name, non_unique, seq_in_index, "
                                    + "COALESCE(column_name, '<EXPRESSION>'), COALESCE(expression, '<NULL>'), "
                                    + "index_type, COALESCE(collation, '<NULL>'), COALESCE(sub_part, '<NULL>') "
                                    + "FROM information_schema.statistics "
                                    + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history' "
                                    + "ORDER BY table_name, index_name, seq_in_index"),
                    queryRows(connection,
                            "SELECT kcu.table_name, kcu.constraint_name, kcu.ordinal_position, kcu.column_name, "
                                    + "COALESCE(kcu.referenced_table_name, '<NULL>'), "
                                    + "COALESCE(kcu.referenced_column_name, '<NULL>'), "
                                    + "rc.update_rule, rc.delete_rule "
                                    + "FROM information_schema.key_column_usage kcu "
                                    + "JOIN information_schema.referential_constraints rc "
                                    + "ON rc.constraint_schema = kcu.constraint_schema "
                                    + "AND rc.table_name = kcu.table_name "
                                    + "AND rc.constraint_name = kcu.constraint_name "
                                    + "WHERE kcu.constraint_schema = DATABASE() "
                                    + "AND kcu.referenced_table_name IS NOT NULL "
                                    + "ORDER BY kcu.table_name, kcu.constraint_name, kcu.ordinal_position"));
        }
    }

    private static Set<String> queryRows(Connection connection, String sql) throws SQLException {
        Set<String> rows = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                String[] values = new String[columnCount];
                for (int index = 0; index < columnCount; index++) {
                    values[index] = String.valueOf(resultSet.getObject(index + 1));
                }
                rows.add(String.join("|", values));
            }
        }
        return rows;
    }

    private static Connection connection(MySQLContainer<?> mysql) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(mysql), mysql.getUsername(), mysql.getPassword());
    }

    private static String jdbcUrl(MySQLContainer<?> mysql) {
        String url = mysql.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?")
                + "useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static long queryLong(MySQLContainer<?> mysql, String sql, Object... parameters) throws SQLException {
        return queryNumber(mysql, sql, parameters).longValue();
    }

    private static Integer queryInteger(MySQLContainer<?> mysql, String sql, Object... parameters)
            throws SQLException {
        return queryNumber(mysql, sql, parameters).intValue();
    }

    private static Number queryNumber(MySQLContainer<?> mysql, String sql, Object... parameters) throws SQLException {
        try (Connection connection = connection(mysql);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return (Number) resultSet.getObject(1);
            }
        }
    }

    private static String queryString(MySQLContainer<?> mysql, String sql, Object... parameters) throws SQLException {
        try (Connection connection = connection(mysql);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private record SchemaSnapshot(
            Set<String> tables,
            Set<String> columns,
            Set<String> indexes,
            Set<String> foreignKeys) {
    }
}
