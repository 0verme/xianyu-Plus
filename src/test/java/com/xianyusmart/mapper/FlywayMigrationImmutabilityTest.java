package com.xianyusmart.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FlywayMigrationImmutabilityTest {

    private static final int LAST_PUBLISHED_VERSION = 33;
    private static final String CHECKSUM_RESOURCE = "/db/migration-checksums.properties";
    private static final String PUBLISHED_V21 = "V21__add_buyer_blacklist.sql";
    private static final String PUBLISHED_V21_SHA256 =
            "2eea06c214f638b20a1e031f7878eac1989876053ae36768678dae73cd6e67a7";

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
            assertEquals(expected.getProperty(filename), sha256NormalizedText(migrationDirectory.resolve(filename)),
                    "Published Flyway migration was modified: " + filename);
        }
    }

    @Test
    void normalizedChecksumIgnoresLineEndingsButDetectsContentChanges(@TempDir Path temporaryDirectory)
            throws IOException {
        Path lfMigration = temporaryDirectory.resolve("lf.sql");
        Path crlfMigration = temporaryDirectory.resolve("crlf.sql");
        Path changedMigration = temporaryDirectory.resolve("changed.sql");
        Files.writeString(lfMigration, "SELECT 1;\n", StandardCharsets.UTF_8);
        Files.writeString(crlfMigration, "SELECT 1;\r\n", StandardCharsets.UTF_8);
        Files.writeString(changedMigration, "SELECT 2;\n", StandardCharsets.UTF_8);

        String lfChecksum = sha256NormalizedText(lfMigration);
        assertEquals(lfChecksum, sha256NormalizedText(crlfMigration),
                "LF and CRLF representations of the same SQL must have identical checksums");
        assertNotEquals(lfChecksum, sha256NormalizedText(changedMigration),
                "A real SQL content change must change the checksum");
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

    private static String sha256NormalizedText(Path path) throws IOException {
        String normalizedText = Files.readString(path, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedText.getBytes(StandardCharsets.UTF_8));
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
