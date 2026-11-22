package io.github.thegoyalyogesh.qa.core.driver;

import io.github.thegoyalyogesh.qa.core.ui.UiEngine;
import io.github.thegoyalyogesh.qa.core.ui.UiOptions;
import org.openqa.selenium.MutableCapabilities;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SauceLabsTest {

    private static final Function<String, String> ENV = Map.of(
            "SAUCE_USERNAME", "demo-user", "SAUCE_ACCESS_KEY", "demo-key")::get;
    private static final Function<String, String> NO_PROPERTIES = name -> null;

    @Test
    public void endpointIsTheRegionsWebDriverHub() {
        assertThat(SauceLabs.endpoint("us-west-1"))
                .hasToString("https://ondemand.us-west-1.saucelabs.com:443/wd/hub");
        assertThat(SauceLabs.endpoint("eu-central-1").getHost()).isEqualTo("ondemand.eu-central-1.saucelabs.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void capabilitiesCarryBrowserPlatformAndSauceOptions() {
        MutableCapabilities caps = SauceLabs.capabilities(BrowserType.SAFARI, "Login U01", ENV,
                Map.of("sauce.build", "Run 3")::get);

        assertThat(caps.getBrowserName()).isEqualTo("safari");
        assertThat(caps.getCapability("platformName")).hasToString("macOS 15");
        assertThat(caps.getCapability("browserVersion")).isEqualTo("latest");
        Map<String, Object> sauce = (Map<String, Object>) caps.getCapability("sauce:options");
        assertThat(sauce).containsEntry("username", "demo-user").containsEntry("accessKey", "demo-key")
                .containsEntry("name", "Login U01").containsEntry("build", "Run 3")
                .doesNotContainKey("screenResolution"); // Safari VMs reject a custom resolution
    }

    @Test
    @SuppressWarnings("unchecked")
    public void windowsBrowsersGetWindowsElevenAndAFullHdScreen() {
        MutableCapabilities caps = SauceLabs.capabilities(BrowserType.EDGE, "x", ENV, NO_PROPERTIES);
        assertThat(caps.getBrowserName()).isEqualTo("MicrosoftEdge");
        assertThat(SauceLabs.platform(BrowserType.EDGE, NO_PROPERTIES)).isEqualTo("Windows 11");
        assertThat((Map<String, Object>) caps.getCapability("sauce:options")).containsEntry("screenResolution", "1920x1080")
                .containsEntry("build", "ParaBank local");
    }

    @Test
    public void platformCanBeOverriddenPerBrowser() {
        assertThat(SauceLabs.platform(BrowserType.CHROME, Map.of("sauce.platform.chrome", "macOS 14")::get))
                .isEqualTo("macOS 14");
    }

    @Test
    public void missingCredentialsFailWithAHelpfulMessage() {
        assertThatThrownBy(() -> SauceLabs.capabilities(BrowserType.CHROME, "x", name -> null, NO_PROPERTIES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAUCE_USERNAME")
                .hasMessageContaining("never in git");
    }

    @Test
    public void safariAndEdgeAreRefusedLocallyWithTheSauceHint() {
        assertThatThrownBy(() -> DriverFactory.create(BrowserType.SAFARI, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sauce:safari");
    }

    @Test
    public void runLabelNamesTheCloud() {
        UiOptions options = new UiOptions(UiEngine.SAUCE, BrowserType.SAFARI, false, Duration.ofSeconds(1), null);
        assertThat(options.label()).isEqualTo("SAUCE-SAFARI");
    }
}
