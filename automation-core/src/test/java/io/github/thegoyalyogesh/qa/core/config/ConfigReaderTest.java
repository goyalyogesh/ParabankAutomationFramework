package io.github.thegoyalyogesh.qa.core.config;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigReaderTest {

    private static final String FILE = "test-config.properties";

    @AfterMethod
    public void clearSystemProperties() {
        System.clearProperty("threads");
        System.clearProperty("headless");
    }

    @Test
    public void readsValueFromFile() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.get("app.url")).isEqualTo("https://example.test/app");
    }

    @Test
    public void environmentVariableOverridesFile() {
        ConfigReader config = new ConfigReader(FILE, Map.of("HEADLESS", "true"));
        assertThat(config.getBoolean("headless", false)).isTrue();
    }

    @Test
    public void systemPropertyOverridesEnvironmentAndFile() {
        System.setProperty("threads", "7");
        ConfigReader config = new ConfigReader(FILE, Map.of("THREADS", "5"));
        assertThat(config.getInt("threads", 1)).isEqualTo(7);
    }

    @Test
    public void defaultsAreUsedWhenKeyMissing() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.get("no.such.key", "fallback")).isEqualTo("fallback");
        assertThat(config.getInt("no.such.key", 42)).isEqualTo(42);
        assertThat(config.getDouble("threshold", 0)).isEqualTo(85.5);
    }

    @Test
    public void missingRequiredKeyThrowsClearMessage() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThatThrownBy(() -> config.get("no.such.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no.such.key");
    }

    @Test
    public void listValuesAreSplitAndTrimmed() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.getList("browser.rotation"))
                .containsExactly("chrome", "firefox", "chrome-headless");
    }

    @Test
    public void environmentKeyIsUpperCaseWithUnderscores() {
        assertThat(ConfigReader.toEnvKey("browser.rotation")).isEqualTo("BROWSER_ROTATION");
    }
}
