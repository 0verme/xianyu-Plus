package com.xianyusmart.mapper;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every Flyway migration against a real MySQL instance.
 *
 * <p>Use {@code -Dmysql.image=mysql:8.0} to run the same check against MySQL 8.0.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMySqlMigrationIntegrationTest {

    private static final String MYSQL_IMAGE = System.getProperty("mysql.image", "mysql:8.4");

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName("xianyusmart")
            .withUsername("test")
            .withPassword("test");

    @Test
    void migratesAllVersionsAndKeepsBlacklistAccountDataConsistent() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        flyway.validate();

        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("32", current.getVersion().getVersion());

        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            assertEquals(32L, queryLong(connection,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1L, queryLong(connection,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?",
                    "xianyu_buyer_blacklist"));
            assertTrue(queryString(connection,
                    "SELECT extra FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    "xianyu_buyer_blacklist", "account_scope").contains("STORED GENERATED"));
            assertEquals(1L, queryLong(connection,
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? "
                            + "AND index_name = ? AND non_unique = 0",
                    "xianyu_buyer_blacklist", "uk_buyer_blacklist_scope"));
            assertEquals("RESTRICT", queryString(connection,
                    "SELECT delete_rule FROM information_schema.referential_constraints "
                            + "WHERE constraint_schema = DATABASE() AND table_name = ? AND constraint_name = ?",
                    "xianyu_buyer_blacklist", "fk_buyer_blacklist_account"));

            String buyerId = "migration-buyer-" + UUID.randomUUID();
            insertGlobalBlacklistEntry(connection, buyerId);
            long accountId = insertAccount(connection);
            insertAccountBlacklistEntry(connection, accountId, buyerId);

            assertEquals(2L, queryLong(connection,
                    "SELECT COUNT(*) FROM xianyu_buyer_blacklist WHERE buyer_user_id = ?", buyerId));
            try (Statement statement = connection.createStatement()) {
                assertThrows(SQLException.class,
                        () -> statement.executeUpdate("DELETE FROM xianyu_account WHERE id = " + accountId));
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM xianyu_buyer_blacklist WHERE xianyu_account_id = ?")) {
                statement.setLong(1, accountId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM xianyu_account WHERE id = ?")) {
                statement.setLong(1, accountId);
                statement.executeUpdate();
            }

            assertEquals(1L, queryLong(connection,
                    "SELECT COUNT(*) FROM xianyu_buyer_blacklist WHERE buyer_user_id = ?", buyerId));
        }
    }

    private static String jdbcUrl() {
        String url = mysql.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?")
                + "useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static void insertGlobalBlacklistEntry(Connection connection, String buyerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO xianyu_buyer_blacklist (xianyu_account_id, buyer_user_id) VALUES (NULL, ?)")) {
            statement.setString(1, buyerId);
            statement.executeUpdate();
        }
    }

    private static long insertAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO xianyu_account (account_note, unb) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            String unb = "migration-account-" + UUID.randomUUID();
            statement.setString(1, "migration test");
            statement.setString(2, unb);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static void insertAccountBlacklistEntry(Connection connection, long accountId, String buyerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO xianyu_buyer_blacklist (xianyu_account_id, buyer_user_id) VALUES (?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, buyerId);
            statement.executeUpdate();
        }
    }

    private static long queryLong(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private static String queryString(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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
}
