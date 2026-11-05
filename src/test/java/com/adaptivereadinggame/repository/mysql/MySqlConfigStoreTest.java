package com.adaptivereadinggame.repository.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MySqlConfigStoreTest {
    @TempDir Path temporary;

    @Test void savesAndLoadsLocalConnectionSettings() throws Exception {
        Path file = temporary.resolve("database.properties");
        MySqlConfig expected = new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "adaptive_game", "local-secret");

        MySqlConfigStore.save(file, expected);

        assertEquals(expected, MySqlConfigStore.load(file).orElseThrow());
        assertFalse(expected.toString().contains("local-secret"));
    }

    @Test void usesExplicitEnvironmentSettingsBeforeSavedSettings() throws Exception {
        Path file = temporary.resolve("database.properties");
        MySqlConfigStore.save(file, new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "saved_user", "saved-secret"));
        Map<String, String> environment = Map.of(
                "ARG_DB_URL", "jdbc:mysql://127.0.0.1:3307/other_db",
                "ARG_DB_USER", "override_user",
                "ARG_DB_PASSWORD", "override-secret");

        assertEquals(new MySqlConfig("jdbc:mysql://127.0.0.1:3307/other_db", "override_user", "override-secret"),
                MySqlConfig.fromEnvironment(environment, file).orElseThrow());
    }

    @Test void doesNotMixPartialEnvironmentWithSavedSettings() throws Exception {
        Path file = temporary.resolve("database.properties");
        MySqlConfigStore.save(file, new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "saved_user", "saved-secret"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MySqlConfig.fromEnvironment(Map.of("ARG_DB_USER", "override_user"), file));
        assertTrue(failure.getMessage().contains("ARG_DB_URL"));
    }

    @Test void restrictsSavedFileToTheCurrentUserOnPosix() throws Exception {
        Path file = temporary.resolve("private/database.properties");
        MySqlConfigStore.save(file, new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "adaptive_game", "local-secret"));
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(file).supportsFileAttributeView("posix"));

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
    }

    @Test void restrictsSavedFileAclToItsOwnerOnWindows() throws Exception {
        Path file = temporary.resolve("private/database.properties");
        MySqlConfigStore.save(file, new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "adaptive_game", "local-secret"));
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(acl != null);

        String username = System.getenv().getOrDefault("USERNAME", System.getProperty("user.name"));
        String domain = System.getenv("USERDOMAIN");
        if (domain != null && domain.equalsIgnoreCase("WORKGROUP")) domain = System.getenv("COMPUTERNAME");
        String account = domain == null || domain.isBlank() ? username : domain + "\\" + username;
        UserPrincipal currentUser = file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(account);
        assertTrue(acl.getAcl().stream().allMatch(entry -> entry.principal().equals(currentUser)));
    }

    @Test void doesNotChangePermissionsOnAnExistingSharedParentDirectory() throws Exception {
        Path shared = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(shared).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(shared);
        Path file = shared.resolve("adaptive-reading-game-config-" + java.util.UUID.randomUUID() + ".properties");
        try {
            MySqlConfigStore.save(file, new MySqlConfig("jdbc:mysql://127.0.0.1:3306/adaptive_reading", "adaptive_game", "local-secret"));
            assertEquals(original, Files.getPosixFilePermissions(shared));
        } finally {
            Files.deleteIfExists(file);
            try { Files.setPosixFilePermissions(shared, original); }
            catch (java.io.IOException | UnsupportedOperationException ignored) { }
        }
    }
}
