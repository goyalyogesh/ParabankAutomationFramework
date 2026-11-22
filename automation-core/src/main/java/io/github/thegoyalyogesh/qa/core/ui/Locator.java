package io.github.thegoyalyogesh.qa.core.ui;

import java.util.Locale;
import java.util.Objects;

/**
 * Engine-neutral element locator written as {@code strategy=value}, e.g. {@code id=customer.firstName},
 * {@code name=username}, {@code css=#rightPanel h1}, {@code xpath=//input[@value='Log In']}, {@code link=Log Out}.
 */
public record Locator(Strategy strategy, String value) {

    public enum Strategy { ID, NAME, CSS, XPATH, LINK_TEXT }

    public Locator {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(value, "value");
    }

    public static Locator parse(String raw) {
        Objects.requireNonNull(raw, "locator");
        int separator = raw.indexOf('=');
        if (separator <= 0) {
            throw new IllegalArgumentException("Locator must look like 'strategy=value' (e.g. id=login) but was: " + raw);
        }
        String prefix = raw.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = raw.substring(separator + 1).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Locator value is empty in: " + raw);
        }
        Strategy strategy = switch (prefix) {
            case "id" -> Strategy.ID;
            case "name" -> Strategy.NAME;
            case "css" -> Strategy.CSS;
            case "xpath" -> Strategy.XPATH;
            case "link" -> Strategy.LINK_TEXT;
            default -> throw new IllegalArgumentException(
                    "Unknown locator strategy '" + prefix + "' in: " + raw + " (use id, name, css, xpath, link)");
        };
        return new Locator(strategy, value);
    }
}
