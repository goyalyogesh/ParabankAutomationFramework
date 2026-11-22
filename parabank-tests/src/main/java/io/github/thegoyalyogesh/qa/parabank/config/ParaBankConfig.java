package io.github.thegoyalyogesh.qa.parabank.config;

import io.github.thegoyalyogesh.qa.core.config.ConfigReader;
import io.github.thegoyalyogesh.qa.core.driver.BrowserType;

import java.nio.file.Path;
import java.time.Duration;

/** Typed view of config.properties (+ env vars and -D overrides). */
public final class ParaBankConfig {

    private static final ParaBankConfig INSTANCE = new ParaBankConfig(new ConfigReader());

    private final ConfigReader config;

    ParaBankConfig(ConfigReader config) {
        this.config = config;
    }

    public static ParaBankConfig get() {
        return INSTANCE;
    }

    public String appUrl() {
        return config.get("app.url");
    }

    public String apiUrl() {
        return config.get("api.url");
    }

    public BrowserType browser() {
        return BrowserType.from(config.get("browser", "chrome"));
    }

    public boolean headless() {
        return config.getBoolean("headless", false);
    }

    public Duration timeout() {
        return Duration.ofSeconds(config.getInt("timeout.seconds", 20));
    }

    public Path testDataFile() {
        return Path.of(config.get("testdata.file"));
    }

    public Path testDataTemplate() {
        return Path.of(config.get("testdata.template"));
    }

    public String usernamePrefix() {
        return config.get("username.prefix", "qa");
    }
}
