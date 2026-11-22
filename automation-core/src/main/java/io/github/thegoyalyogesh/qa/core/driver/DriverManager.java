package io.github.thegoyalyogesh.qa.core.driver;

import org.openqa.selenium.WebDriver;

/**
 * Holds one WebDriver per thread so parallel tests never share a browser.
 * Always pair {@link #start} with {@link #quit} (e.g. in Cucumber @Before/@After hooks).
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static void start(BrowserType type, boolean headless) {
        if (DRIVER.get() != null) {
            throw new IllegalStateException("A WebDriver is already running on thread "
                    + Thread.currentThread().getName() + ". Call DriverManager.quit() first.");
        }
        DRIVER.set(DriverFactory.create(type, headless));
    }

    public static WebDriver get() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("No WebDriver on thread " + Thread.currentThread().getName()
                    + ". Call DriverManager.start(browser, headless) first.");
        }
        return driver;
    }

    public static boolean isStarted() {
        return DRIVER.get() != null;
    }

    /** Quits the browser and clears the slot; safe to call when nothing was started. */
    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } finally {
            DRIVER.remove();
        }
    }
}
