package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import io.github.thegoyalyogesh.qa.core.driver.SauceLabs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

/**
 * One browser session per thread, for either engine. Test hooks call {@link #start} before a scenario and
 * {@link #quit} after it; nothing else needs to know whether Selenium or Playwright is running.
 */
public final class UiSession {

    private static final Logger LOG = LogManager.getLogger(UiSession.class);
    private static final ThreadLocal<Running> SESSION = new ThreadLocal<>();
    private static final BiConsumer<String, Boolean> NO_REPORT = (name, passed) -> { };

    /** The driver, how to stop it, and where to report the scenario result (only Sauce Labs has a dashboard). */
    private record Running(UiDriver ui, AutoCloseable stopper, BiConsumer<String, Boolean> reporter) {
    }

    private UiSession() {
    }

    public static UiDriver start(UiOptions options) {
        if (SESSION.get() != null) {
            throw new IllegalStateException("A UI session is already running on thread "
                    + Thread.currentThread().getName() + ". Call UiSession.quit() first.");
        }
        Running running = switch (options.engine()) {
            case SELENIUM -> {
                DriverManager.start(options.browser(), options.headless());
                yield new Running(new SeleniumUiDriver(DriverManager.get(), options.timeout()), DriverManager::quit,
                        NO_REPORT);
            }
            case PLAYWRIGHT -> {
                PlaywrightUiDriver driver = PlaywrightUiDriver.launch(options);
                yield new Running(driver, driver, NO_REPORT);
            }
            case SAUCE -> {
                DriverManager.start(() -> SauceLabs.create(options.browser(), "ParaBank scenario"));
                yield new Running(new SeleniumUiDriver(DriverManager.get(), options.timeout()), DriverManager::quit,
                        (name, passed) -> SauceLabs.report(DriverManager.get(), name, passed));
            }
        };
        SESSION.set(running);
        return running.ui();
    }

    public static UiDriver get() {
        Running running = SESSION.get();
        if (running == null) {
            throw new IllegalStateException("No UI session on thread " + Thread.currentThread().getName()
                    + ". Call UiSession.start(options) first.");
        }
        return running.ui();
    }

    /**
     * Tells the browser provider how the scenario ended (Sauce Labs: job name + passed/failed on its dashboard).
     * Does nothing for local browsers. Never fails the test: a reporting problem is only logged.
     */
    public static void reportResult(String scenarioName, boolean passed) {
        Running running = SESSION.get();
        if (running == null) {
            return;
        }
        try {
            running.reporter().accept(scenarioName, passed);
        } catch (RuntimeException e) {
            LOG.warn("Could not report the result of '{}' to the browser provider: {}", scenarioName, e.getMessage());
        }
    }

    public static boolean isStarted() {
        return SESSION.get() != null;
    }

    /** Stops the browser and clears the slot; safe to call when nothing was started. */
    public static void quit() {
        Running running = SESSION.get();
        if (running == null) {
            return;
        }
        try {
            running.stopper().close();
        } catch (Exception e) {
            throw new IllegalStateException("Could not stop the UI session", e);
        } finally {
            SESSION.remove();
        }
    }
}
