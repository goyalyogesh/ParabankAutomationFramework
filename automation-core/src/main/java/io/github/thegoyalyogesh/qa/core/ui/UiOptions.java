package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Everything needed to start a browser session with either engine.
 *
 * @param playwrightBrowsersPath where Playwright keeps browsers it downloads (Firefox); {@code null} = Playwright's
 *                               default cache folder. Not used by Selenium.
 */
public record UiOptions(UiEngine engine, BrowserType browser, boolean headless, Duration timeout,
                        Path playwrightBrowsersPath) {

    public UiOptions {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(timeout, "timeout");
    }

    /** e.g. {@code CHROME} or {@code PLAYWRIGHT-CHROME}: which engine and browser produced a result. */
    public String label() {
        return engine == UiEngine.SELENIUM ? browser.name() : engine.name() + "-" + browser.name();
    }
}
