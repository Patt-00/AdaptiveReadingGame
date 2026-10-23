package com.adaptivereadinggame.content;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Resolves a simple character asset key under the local characters folder. */
public final class CharacterAssetResolver {
    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif");

    public Optional<Path> findCharacterImage(String assetKey, Path assetsDirectory) {
        if (assetKey == null || !assetKey.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            return Optional.empty();
        if (assetsDirectory == null) return Optional.empty();
        try {
            Path assetsRoot = assetsDirectory.toRealPath();
            Path root = assetsRoot.resolve("characters").toRealPath();
            if (!root.startsWith(assetsRoot)) return Optional.empty();
            for (String extension : EXTENSIONS) {
                Path candidate = root.resolve(assetKey + extension);
                if (!Files.isRegularFile(candidate)) continue;
                try {
                    Path resolved = candidate.toRealPath();
                    if (resolved.startsWith(root)) return Optional.of(resolved);
                } catch (IOException missingOrInvalid) {
                    continue;
                }
            }
        } catch (IOException | InvalidPathException missingOrInvalid) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
