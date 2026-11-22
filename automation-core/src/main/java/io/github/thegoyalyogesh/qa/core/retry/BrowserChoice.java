package io.github.thegoyalyogesh.qa.core.retry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One entry of the browser rotation: {@code [engine:]browser[-headless]}, e.g. {@code chrome}, {@code firefox-headless},
 * {@code playwright:chrome}. Without a prefix the engine is {@code selenium}.
 */
public record BrowserChoice(String engine, String browser, boolean headless) {

    public static final String DEFAULT_ENGINE = "selenium";
    private static final String HEADLESS_SUFFIX = "-headless";

    public BrowserChoice {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(browser, "browser");
        if (engine.isBlank() || browser.isBlank()) {
            throw new IllegalArgumentException("Engine and browser must not be blank: '" + engine + ":" + browser + "'");
        }
    }

    /** A Selenium browser. */
    public BrowserChoice(String browser, boolean headless) {
        this(DEFAULT_ENGINE, browser, headless);
    }

    /** e.g. {@code "chrome,firefox,playwright:chrome"}; entries without the suffix use {@code defaultHeadless}. */
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
        int colon = value.indexOf(':');
        String engine = colon < 0 ? DEFAULT_ENGINE : value.substring(0, colon).trim();
        String browser = value.substring(colon + 1).trim();
        return browser.endsWith(HEADLESS_SUFFIX)
                ? new BrowserChoice(engine, browser.substring(0, browser.length() - HEADLESS_SUFFIX.length()), true)
                : new BrowserChoice(engine, browser, defaultHeadless);
    }

    /** {@code chrome (headless)}, or {@code playwright chrome} when the engine isn't the default. */
    public String label() {
        return (DEFAULT_ENGINE.equals(engine) ? "" : engine + " ") + browser + (headless ? " (headless)" : "");
    }
}
