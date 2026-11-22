package io.github.thegoyalyogesh.qa.core.ui;

import java.util.Arrays;

/**
 * Which library drives the browser behind {@link UiDriver}. Chosen by config ({@code ui.engine}), never in code.
 * {@code SAUCE} is Selenium driving a browser in the Sauce Labs cloud.
 */
public enum UiEngine {
    SELENIUM, PLAYWRIGHT, SAUCE;

    public static UiEngine from(String name) {
        return Arrays.stream(values())
                .filter(engine -> engine.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported UI engine '" + name + "'. Supported: " + Arrays.toString(values())));
    }
}
