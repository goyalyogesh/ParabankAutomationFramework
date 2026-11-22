package io.github.thegoyalyogesh.qa.core.retry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** One browser of the rotation: {@code chrome}, {@code firefox}, or with a {@code -headless} suffix. */
public record BrowserChoice(String browser, boolean headless) {

    private static final String HEADLESS_SUFFIX = "-headless";

    /** e.g. {@code "chrome,firefox,chrome-headless"}; entries without the suffix use {@code defaultHeadless}. */
    public static List<BrowserChoice> rotation(String commaSeparated, boolean defaultHeadless) {
        List<BrowserChoice> rotation = Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> parse(entry, defaultHeadless))
                .toList();
        if (rotation.isEmpty()) {
            throw new IllegalArgumentException("Browser rotation is empty: '" + commaSeparated + "'");
        }
        return rotation;
    }

    static BrowserChoice parse(String entry, boolean defaultHeadless) {
        String value = entry.toLowerCase(Locale.ROOT);
        return value.endsWith(HEADLESS_SUFFIX)
                ? new BrowserChoice(value.substring(0, value.length() - HEADLESS_SUFFIX.length()), true)
                : new BrowserChoice(value, defaultHeadless);
    }

    public String label() {
        return browser + (headless ? " (headless)" : "");
    }
}
