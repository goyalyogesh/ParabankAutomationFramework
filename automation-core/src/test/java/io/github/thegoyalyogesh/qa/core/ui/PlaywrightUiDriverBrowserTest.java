package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;
import org.testng.annotations.Test;

import java.time.Duration;

@Test(groups = "browser")
public class PlaywrightUiDriverBrowserTest extends UiDriverContractTest {

    private PlaywrightUiDriver driver;

    @Override
    protected UiDriver start(Duration timeout) {
        driver = PlaywrightUiDriver.launch(new UiOptions(UiEngine.PLAYWRIGHT, BrowserType.CHROME, true, timeout, null));
        return driver;
    }

    @Override
    protected void stop() {
        if (driver != null) {
            driver.close();
        }
    }

    @Override
    protected long scrollY() {
        return ((Number) driver.evaluate("window.scrollY")).longValue();
    }
}
