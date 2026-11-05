package com.adaptivereadinggame.repository.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Stores local database connection settings outside the repository. */
public final class MySqlConfigStore {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private MySqlConfigStore() {}

    public static Path defaultPath() {
        return defaultPath(System.getenv(), System.getProperty("os.name", ""), System.getProperty("user.home", "."));
    }

    static Path defaultPath(Map<String, String> environment, String osName, String userHome) {
        String override = environment.get("ARG_DB_CONFIG_FILE");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();

        return defaultDirectory(environment, osName, userHome).resolve("database.properties");
    }

    private static Path defaultDirectory(Map<String, String> environment, String osName, String userHome) {
        if (osName.toLowerCase(java.util.Locale.ROOT).contains("win")) {
            String appData = environment.get("APPDATA");
            if (appData != null && !appData.isBlank())
                return Path.of(appData, "AdaptiveReadingGame").toAbsolutePath().normalize();
        }

        String xdg = environment.get("XDG_CONFIG_HOME");
        Path configHome = xdg == null || xdg.isBlank()
                ? Path.of(userHome, ".config")
                : Path.of(xdg);
        return configHome.resolve("adaptive-reading-game").toAbsolutePath().normalize();
    }

    public static Optional<MySqlConfig> load(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read the local database configuration.", failure);
        }
        String url = properties.getProperty("url");
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        try {
            return Optional.of(new MySqlConfig(url, username, password));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("The local database configuration is incomplete or invalid.", invalid);
        }
    }

    public static void save(Path file, MySqlConfig config) {
        Path destination = file.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) throw new IllegalArgumentException("The database configuration needs a parent directory.");
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            if (parent.equals(defaultDirectory(System.getenv(), System.getProperty("os.name", ""),
                    System.getProperty("user.home", ".")))) {
                restrictDirectory(parent);
            }
            try {
                temporary = Files.createTempFile(parent, ".database-config-", ".tmp",
                        PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
            } catch (UnsupportedOperationException unsupported) {
                temporary = Files.createTempFile(parent, ".database-config-", ".tmp");
            }
            restrictFile(temporary);
            Properties properties = new Properties();
            properties.setProperty("url", config.url());
            properties.setProperty("username", config.username());
            properties.setProperty("password", config.password());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Adaptive Reading Game local database settings");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictFile(destination);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save the local database configuration.", failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }

    private static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            if (Files.getFileStore(path).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows AppData uses the current user's inherited ACL.
        }
    }

    private static void restrictDirectory(Path path) throws IOException {
        if (isWindows()) {
            setWindowsOwnerAcl(path, true);
        } else {
            setPosixPermissions(path, DIRECTORY_PERMISSIONS);
        }
    }

    private static void restrictFile(Path path) throws IOException {
        if (isWindows()) {
            setWindowsOwnerAcl(path, false);
        } else {
            setPosixPermissions(path, FILE_PERMISSIONS);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void setWindowsOwnerAcl(Path path, boolean directory) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (view == null) throw new IOException("The local filesystem does not support Windows ACL permissions.");
        String username = System.getenv().getOrDefault("USERNAME", System.getProperty("user.name", ""));
        String domain = System.getenv("USERDOMAIN");
        if (domain != null && domain.equalsIgnoreCase("WORKGROUP")) domain = System.getenv("COMPUTERNAME");
        String account = domain == null || domain.isBlank() ? username : domain + "\\" + username;
        if (account.isBlank()) throw new IOException("Could not determine the current Windows account for database file permissions.");
        final UserPrincipal currentUser;
        try {
            currentUser = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(account);
        } catch (UserPrincipalNotFoundException missing) {
            throw new IOException("Could not resolve the current Windows account for database file permissions.", missing);
        }
        AclEntry.Builder entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(currentUser)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        view.setAcl(List.of(entry.build()));
    }
}
