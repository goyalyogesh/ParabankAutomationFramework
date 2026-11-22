package io.github.thegoyalyogesh.qa.core.ui;

import org.openqa.selenium.By;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SeleniumByMappingTest {

    @Test
    public void mapsEveryStrategyToSeleniumBy() {
        assertThat(SeleniumUiDriver.toBy(Locator.parse("id=a"))).isEqualTo(By.id("a"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("name=b"))).isEqualTo(By.name("b"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("css=#c"))).isEqualTo(By.cssSelector("#c"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("xpath=//d"))).isEqualTo(By.xpath("//d"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("link=Log Out"))).isEqualTo(By.linkText("Log Out"));
    }
}
