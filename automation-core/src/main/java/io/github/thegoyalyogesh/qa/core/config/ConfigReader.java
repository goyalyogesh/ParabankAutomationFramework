package io.github.thegoyalyogesh.qa.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads configuration with this precedence (highest wins):
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dthreads=5}</li>
 *   <li>Environment variable, key upper-cased with dots as underscores, e.g. {@code THREADS}</li>
 *   <li>Properties file on the classpath (default {@code config.properties})</li>
 * </ol>
 */
public final class ConfigReader {

    public static final String DEFAULT_FILE = "config.properties";

    private final Properties fileProperties;
    private final Map<String, String> environment;

    public ConfigReader() {
        this(DEFAULT_FILE, System.getenv());
    }

    public ConfigReader(String classpathFile, Map<String, String> environment) {
        this.fileProperties = load(classpathFile);
        this.environment = Map.copyOf(environment);
    }

    public Optional<String> find(String key) {
        String fromSystem = System.getProperty(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return Optional.of(fromSystem.trim());
        }
        String fromEnv = environment.get(toEnvKey(key));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.trim());
        }
        return Optional.ofNullable(fileProperties.getProperty(key))
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    public String get(String key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Missing required config key '" + key + "'. Set it in config.properties, as env var "
                        + toEnvKey(key) + ", or with -D" + key + "=..."));
    }

    public String get(String key, String defaultValue) {
        return find(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return find(key).map(Integer::parseInt).orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return find(key).map(Double::parseDouble).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return find(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    /** Comma-separated value as a list, each item trimmed, blanks removed. */
    public List<String> getList(String key) {
        return find(key).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static Properties load(String classpathFile) {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(classpathFile)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + classpathFile, e);
        }
        return properties;
    }
}
