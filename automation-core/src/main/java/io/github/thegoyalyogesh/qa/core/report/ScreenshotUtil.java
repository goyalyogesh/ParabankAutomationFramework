package io.github.thegoyalyogesh.qa.core.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves screenshots with names that can never clash between parallel threads. */
public final class ScreenshotUtil {

    private ScreenshotUtil() {
    }

    /** Writes {@code <name>_t<threadId>_<nanoTime>.png} into {@code directory} and returns the path. */
    public static Path save(byte[] png, Path directory, String name) {
        String safe = name.replaceAll("[^A-Za-z0-9.-]+", "_").replaceAll("_+$", "");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        String fileName = "%s_t%d_%d.png".formatted(safe, Thread.currentThread().threadId(), System.nanoTime());
        try {
            Files.createDirectories(directory);
            return Files.write(directory.resolve(fileName), png);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save screenshot " + fileName, e);
        }
    }
}
