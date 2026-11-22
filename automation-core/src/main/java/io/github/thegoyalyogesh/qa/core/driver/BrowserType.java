package io.github.thegoyalyogesh.qa.core.driver;

import java.util.Arrays;

/** SAFARI and EDGE run only on Sauce Labs ({@code sauce:safari}); locally the framework drives Chrome and Firefox. */
public enum BrowserType {
    CHROME, FIREFOX, SAFARI, EDGE;

    public static BrowserType from(String name) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported browser '" + name + "'. Supported: " + Arrays.toString(values())));
    }
}
