package com.adaptivereadinggame.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Resolves optional scene and CG keys only within their approved asset subfolders. */
public final class StoryAssetResolver {
    private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg");

    public Optional<Path> findSceneImage(String key, Path assetsDirectory) {
        return findImage("scenes", key, assetsDirectory);
    }

    public Optional<Path> findCgImage(String key, Path assetsDirectory) {
        return findImage("cg", key, assetsDirectory);
    }

    private static Optional<Path> findImage(String folder, String key, Path assetsDirectory) {
        return find(folder, key, assetsDirectory, IMAGE_EXTENSIONS);
    }

    private static Optional<Path> find(String folder, String key, Path assetsDirectory, List<String> extensions) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}") || assetsDirectory == null)
            return Optional.empty();
        try {
            Path root = assetsDirectory.toRealPath();
            Path targetDirectory = root.resolve(folder).toRealPath();
            if (!targetDirectory.startsWith(root)) return Optional.empty();
            for (String extension : extensions) {
                Path candidate = targetDirectory.resolve(key + extension);
                if (!Files.isRegularFile(candidate)) continue;
                try {
                    Path resolved = candidate.toRealPath();
                    if (resolved.startsWith(targetDirectory)) return Optional.of(resolved);
                } catch (IOException missingOrInvalid) {
                    // A broken link is just an unavailable optional asset.
                }
            }
        } catch (IOException | InvalidPathException missingOrInvalid) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
