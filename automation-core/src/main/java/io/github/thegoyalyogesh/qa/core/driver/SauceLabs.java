package io.github.thegoyalyogesh.qa.core.driver;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Browsers in the Sauce Labs cloud: a {@link RemoteWebDriver} pointed at {@code ondemand.<region>.saucelabs.com}.
 * Credentials come only from the environment ({@code SAUCE_USERNAME}, {@code SAUCE_ACCESS_KEY}, e.g. a git-ignored
 * {@code .env} file or Jenkins credentials), never from config files in git.
 * <p>
 * Settings (system properties): {@code sauce.region} (default {@code us-west-1}), {@code sauce.build} (groups the
 * sessions of one run on the dashboard), {@code sauce.platform.<browser>} to override the operating system.
 */
public final class SauceLabs {

    static final String USERNAME = "SAUCE_USERNAME";
    static final String ACCESS_KEY = "SAUCE_ACCESS_KEY";
    static final Map<BrowserType, String> DEFAULT_PLATFORM = Map.of(
            BrowserType.CHROME, "Windows 11",
            BrowserType.FIREFOX, "Windows 11",
            BrowserType.EDGE, "Windows 11",
            BrowserType.SAFARI, "macOS 15");

    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(60);

    private SauceLabs() {
    }

    /** Starts a cloud browser; {@code sessionName} is shown on the Sauce Labs dashboard. */
    public static WebDriver create(BrowserType browser, String sessionName) {
        Function<String, String> env = System::getenv;
        WebDriver driver = new RemoteWebDriver(endpoint(System.getProperty("sauce.region", "us-west-1")),
                capabilities(browser, sessionName, env, System.getProperties()::getProperty));
        driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
        return driver;
    }

    /** Names the job after the scenario and marks it passed or failed on the dashboard (Sauce Labs JS commands). */
    public static void report(WebDriver driver, String name, boolean passed) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("sauce:job-name=" + name);
        js.executeScript("sauce:job-result=" + (passed ? "passed" : "failed"));
    }

    static URL endpoint(String region) {
        try {
            return URI.create("https://ondemand." + region + ".saucelabs.com:443/wd/hub").toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sauce.region '" + region + "'", e);
        }
    }

    static MutableCapabilities capabilities(BrowserType browser, String sessionName,
                                            Function<String, String> env, Function<String, String> properties) {
        String username = required(env, USERNAME);
        String accessKey = required(env, ACCESS_KEY);
        MutableCapabilities options = switch (browser) {
            case CHROME -> new ChromeOptions();
            case FIREFOX -> new FirefoxOptions();
            case EDGE -> new EdgeOptions();
            case SAFARI -> new SafariOptions();
        };
        options.setCapability("platformName", platform(browser, properties));
        options.setCapability("browserVersion", "latest");

        Map<String, Object> sauce = new LinkedHashMap<>();
        sauce.put("username", username);
        sauce.put("accessKey", accessKey);
        sauce.put("name", sessionName);
        sauce.put("build", properties.apply("sauce.build") == null ? "ParaBank local" : properties.apply("sauce.build"));
        if (browser != BrowserType.SAFARI) {
            sauce.put("screenResolution", "1920x1080");
        }
        options.setCapability("sauce:options", sauce);
        return options;
    }

    static String platform(BrowserType browser, Function<String, String> properties) {
        String override = properties.apply("sauce.platform." + browser.name().toLowerCase());
        return override == null || override.isBlank() ? DEFAULT_PLATFORM.get(browser) : override.trim();
    }

    private static String required(Function<String, String> env, String name) {
        String value = env.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set. Sauce Labs runs need SAUCE_USERNAME and "
                    + "SAUCE_ACCESS_KEY in the environment (e.g. `set -a; . ./.env; set +a`), never in git.");
        }
        return value.trim();
    }
}
