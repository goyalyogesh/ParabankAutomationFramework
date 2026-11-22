package io.github.thegoyalyogesh.qa.parabank.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import io.github.thegoyalyogesh.qa.core.report.ScreenshotUtil;
import io.github.thegoyalyogesh.qa.core.ui.SeleniumUiDriver;
import io.github.thegoyalyogesh.qa.parabank.config.ParaBankConfig;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Browser lifecycle per scenario, failure screenshots, and Excel write-back of failures/results. */
public class Hooks {

    private static final Logger LOG = LogManager.getLogger(Hooks.class);
    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots");

    private final TestContext ctx;

    public Hooks(TestContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Fault injection to test the retry engine itself: {@code -Dinject.failure=<regex>} fails every scenario whose
     * name matches. The orchestrator passes it to run 1 only ({@code -Dretry.demo.injectFailure}), so the retry path
     * can be shown on a healthy site. Never set in normal runs.
     */
    @Before(order = 1)
    public void injectFailure(Scenario scenario) {
        String pattern = System.getProperty("inject.failure", "");
        if (!pattern.isBlank() && Pattern.compile(pattern).matcher(scenario.getName()).find()) {
            throw new AssertionError("Injected failure (testing the retry engine), pattern: " + pattern);
        }
    }

    @Before(value = "not @api", order = 0)
    public void startBrowser(Scenario scenario) {
        ParaBankConfig config = ctx.config();
        DriverManager.start(config.browser(), config.headless());
        ctx.setUi(new SeleniumUiDriver(DriverManager.get(), config.timeout()));
        scenario.log("Browser: " + config.browser() + (config.headless() ? " (headless)" : "")
                + " | thread: " + Thread.currentThread().getName());
        LOG.info("START  {} [{}]", scenario.getName(), config.browser());
    }

    /** @After hooks run from highest to lowest order: screenshot (100) → Excel (50) → quit (0). */
    @After(order = 100)
    public void screenshotOnFailure(Scenario scenario) {
        if (scenario.isFailed() && ctx.hasUi()) {
            byte[] png = ctx.ui().screenshot();
            scenario.attach(png, "image/png", "Failure screenshot");
            Path file = ScreenshotUtil.save(png, SCREENSHOT_DIR, scenario.getName());
            LOG.error("FAILED {} -> {}", scenario.getName(), file);
        }
    }

    @After(value = "@registration", order = 50)
    public void recordRegistrationFailure(Scenario scenario) {
        if (scenario.isFailed() && !ctx.resultRecorded() && ctx.currentUser() != null) {
            ctx.repository().recordRegistrationFailure(ctx.currentUser().userId(),
                    "Scenario failed, see report: " + scenario.getName(), ctx.config().browser().name());
        }
    }

    @After(value = "@negativeLogin", order = 50)
    public void recordNegativeLoginResult(Scenario scenario) {
        if (ctx.loginCase() != null) {
            ctx.repository().recordLoginResult(ctx.loginCase().caseId(), scenario.isFailed() ? "FAIL" : "PASS",
                    ctx.loginObservation(), ctx.config().browser().name());
        }
    }

    @After(order = 0)
    public void quitBrowser(Scenario scenario) {
        DriverManager.quit();
        LOG.info("FINISH {} -> {}", scenario.getName(), scenario.getStatus());
    }
}
