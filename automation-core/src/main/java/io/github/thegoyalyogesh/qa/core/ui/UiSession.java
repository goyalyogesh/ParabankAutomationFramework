package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.DriverManager;

/**
 * One browser session per thread, for either engine. Test hooks call {@link #start} before a scenario and
 * {@link #quit} after it; nothing else needs to know whether Selenium or Playwright is running.
 */
public final class UiSession {

    private static final ThreadLocal<Running> SESSION = new ThreadLocal<>();

    /** The driver and how to stop it. */
    private record Running(UiDriver ui, AutoCloseable stopper) {
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
                yield new Running(new SeleniumUiDriver(DriverManager.get(), options.timeout()), DriverManager::quit);
            }
            case PLAYWRIGHT -> {
                PlaywrightUiDriver driver = PlaywrightUiDriver.launch(options);
                yield new Running(driver, driver);
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
