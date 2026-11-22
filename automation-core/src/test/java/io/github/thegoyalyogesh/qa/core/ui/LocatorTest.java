package io.github.thegoyalyogesh.qa.core.ui;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LocatorTest {

    @Test
    public void parsesIdWithDotsInValue() {
        assertThat(Locator.parse("id=customer.firstName"))
                .isEqualTo(new Locator(Locator.Strategy.ID, "customer.firstName"));
    }

    @Test
    public void onlyFirstEqualsSignSeparatesStrategy() {
        assertThat(Locator.parse("xpath=//input[@value='Log In']"))
                .isEqualTo(new Locator(Locator.Strategy.XPATH, "//input[@value='Log In']"));
    }

    @Test
    public void strategyIsCaseInsensitiveAndTrimmed() {
        assertThat(Locator.parse(" CSS = #rightPanel h1 "))
                .isEqualTo(new Locator(Locator.Strategy.CSS, "#rightPanel h1"));
    }

    @Test
    public void supportsNameAndLinkText() {
        assertThat(Locator.parse("name=username").strategy()).isEqualTo(Locator.Strategy.NAME);
        assertThat(Locator.parse("link=Log Out").strategy()).isEqualTo(Locator.Strategy.LINK_TEXT);
    }

    @Test
    public void missingStrategyIsRejected() {
        assertThatThrownBy(() -> Locator.parse("customer.firstName"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategy=value");
    }

    @Test
    public void unknownStrategyIsRejected() {
        assertThatThrownBy(() -> Locator.parse("foo=bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown locator strategy 'foo'");
    }

    @Test
    public void emptyValueIsRejected() {
        assertThatThrownBy(() -> Locator.parse("id="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }
}
