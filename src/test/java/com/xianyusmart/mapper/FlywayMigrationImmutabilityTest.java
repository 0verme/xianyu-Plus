package com.xianyusmart.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlywayMigrationImmutabilityTest {

    private static final int LAST_PUBLISHED_VERSION = 33;
    private static final String CHECKSUM_RESOURCE = "/db/migration-checksums.properties";
    private static final String PUBLISHED_V21 = "V21__add_buyer_blacklist.sql";
    private static final String PUBLISHED_V21_SHA256 =
            "0c4bdb495c6096da2dc68130b3e917872e3ea8e20cacc6663db69b9ce74091d6";

    @Test
    void publishedVersionedMigrationsMatchTheImmutableManifest() throws IOException {
        Properties expected = loadExpectedChecksums();
        assertEquals(33, expected.size(), "The immutable manifest must cover V1 through V33");
        assertEquals(PUBLISHED_V21_SHA256, expected.getProperty(PUBLISHED_V21),
                "The published V21 checksum must remain unchanged");
        Path migrationDirectory = Path.of("src/main/resources/db/migration");
        Set<String> actual;
        try (var paths = Files.list(migrationDirectory)) {
            actual = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(FlywayMigrationImmutabilityTest::isPublishedVersionedMigration)
                    .collect(Collectors.toSet());
        }

        assertEquals(expected.stringPropertyNames(), actual,
                "Published migration filenames must not be removed or renamed");
        for (String filename : actual) {
            assertEquals(expected.getProperty(filename), sha256(migrationDirectory.resolve(filename)),
                    "Published Flyway migration was modified: " + filename);
        }
    }

    private static Properties loadExpectedChecksums() throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = FlywayMigrationImmutabilityTest.class
                .getResourceAsStream(CHECKSUM_RESOURCE)) {
            assertNotNull(stream, "Migration checksum manifest is missing");
            properties.load(stream);
        }
        return properties;
    }

    private static boolean isPublishedVersionedMigration(String filename) {
        if (!filename.startsWith("V") || !filename.endsWith(".sql")) {
            return false;
        }
        int separator = filename.indexOf("__");
        if (separator < 2) {
            return false;
        }
        try {
            int version = Integer.parseInt(filename.substring(1, separator));
            return version >= 1 && version <= LAST_PUBLISHED_VERSION;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the JDK", exception);
        }
    }
}
