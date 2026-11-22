package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;
import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;

import java.time.Duration;

@Test(groups = "browser")
public class SeleniumUiDriverBrowserTest extends UiDriverContractTest {

    @Override
    protected UiDriver start(Duration timeout) {
        DriverManager.start(BrowserType.CHROME, true);
        return new SeleniumUiDriver(DriverManager.get(), timeout);
    }

    @Override
    protected void stop() {
        DriverManager.quit();
    }

    @Override
    protected long scrollY() {
        return ((Number) ((JavascriptExecutor) DriverManager.get()).executeScript("return window.scrollY")).longValue();
    }
}
