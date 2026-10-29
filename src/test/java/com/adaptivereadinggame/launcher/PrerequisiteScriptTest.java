package com.adaptivereadinggame.launcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs({OS.LINUX, OS.MAC})
class PrerequisiteScriptTest {
    @TempDir Path temporary;
    private Path tools;
    private Path runtime;
    private final Path script = Path.of("scripts/check-prerequisites.sh").toAbsolutePath();

    @BeforeEach void prepare() throws Exception {
        tools = Files.createDirectories(temporary.resolve("fake tools"));
        runtime = Files.createDirectories(temporary.resolve("fake jdk/bin")).getParent();
        executable(tools.resolve("java"), "#!/bin/sh\nif [ -n \"${JAVA_TOOL_OPTIONS:-}\" ]; then printf 'Picked up JAVA_TOOL_OPTIONS: %s\\n' \"$JAVA_TOOL_OPTIONS\" >&2; fi\nprintf 'openjdk version \"25\"\\n' >&2\n");
        executable(tools.resolve("javac"), "#!/bin/sh\nif [ -n \"${JAVA_TOOL_OPTIONS:-}\" ]; then printf 'Picked up JAVA_TOOL_OPTIONS: %s\\n' \"$JAVA_TOOL_OPTIONS\" >&2; fi\nprintf 'javac 25\\n'\n");
        executable(runtime.resolve("bin/javac"), "#!/bin/sh\nexit 0\n");
        executable(tools.resolve("mvn"), "#!/bin/sh\nprintf 'Apache Maven 3.9.0\\nJava version: %s, runtime: %s\\n' \"$FAKE_MAVEN_JAVA\" \"$FAKE_RUNTIME\"\n");
    }

    @Test void checksMavensJavaRatherThanOnlyThePathJava() throws Exception {
        Result result = run(Map.of("FAKE_MAVEN_JAVA", "17"));
        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("Maven uses Java 17"));
    }

    @Test void parsesJavaVersionsWhenJavaToolOptionsAddsAStderrLine() throws Exception {
        Result result = run(Map.of("JAVA_TOOL_OPTIONS", "-Dprism.order=sw"));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("OK: Java 25"));
        assertTrue(result.output().contains("OK: javac 25"));
    }

    @Test void acceptsAnExplicitEmptyPasswordAndNeverPrintsIt() throws Exception {
        Result result = run(Map.of("ARG_DB_URL", "jdbc:mysql://localhost/test", "ARG_DB_USER", "reader",
                "ARG_DB_PASSWORD", ""));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Connectivity and migrations were not checked"));
    }

    @Test void rejectsPartialDatabaseSettingsAndMissingPack() throws Exception {
        Result missingPassword = run(Map.of("ARG_DB_URL", "jdbc:mysql://localhost/test", "ARG_DB_USER", "reader"));
        assertNotEquals(0, missingPassword.exitCode());
        assertTrue(missingPassword.output().contains("ARG_DB_PASSWORD"));
        Result missingPack = run(Map.of("ARG_CONTENT_PACK", temporary.resolve("missing.json").toString()));
        assertNotEquals(0, missingPack.exitCode());
        assertTrue(missingPack.output().contains("ARG_CONTENT_PACK"));
    }

    @Test void warnsAboutUnmaterializedArtwork() throws Exception {
        Path assets = temporary.resolve("artwork");
        Files.createDirectories(assets.resolve("characters"));
        Files.writeString(assets.resolve("characters/example.png"), "version https://git-lfs.github.com/spec/v1\n");
        Result result = run(Map.of("ARG_ASSET_DIR", assets.toString()));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Git LFS pointers"));
    }

    private Result run(Map<String, String> overrides) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", script.toString(), "--check-only");
        builder.directory(temporary.toFile());
        builder.redirectErrorStream(true);
        var environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ARG_DB_") || key.equals("ARG_CONTENT_PACK") || key.equals("ARG_ASSET_DIR"));
        environment.put("PATH", tools + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("FAKE_RUNTIME", runtime.toString());
        environment.put("FAKE_MAVEN_JAVA", "25");
        environment.putAll(overrides);
        Process process = builder.start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void executable(Path path, String contents) throws Exception {
        Files.writeString(path, contents);
        assertTrue(path.toFile().setExecutable(true));
    }
    private record Result(int exitCode, String output) {}
}
