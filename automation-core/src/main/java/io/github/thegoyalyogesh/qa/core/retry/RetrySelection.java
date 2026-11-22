package io.github.thegoyalyogesh.qa.core.retry;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which scenarios a retry run executes: {@code -Dretry.scenarios=uri:line,uri:line}. Empty or missing = all.
 * <p>
 * Why not Cucumber's own filters? Any filter passed as a property ({@code cucumber.features} with lines,
 * {@code cucumber.filter.name}, {@code cucumber.filter.tags}) <b>replaces</b> every filter in {@code @CucumberOptions},
 * including the runner's tags, so every runner would execute every retried scenario (seen live: 6 retries ran 24
 * times). Filtering the TestNG data-provider rows keeps each runner's tags and adds the retry selection on top.
 */
public final class RetrySelection {

    public static final String PROPERTY = "retry.scenarios";

    private RetrySelection() {
    }

    public static Set<String> requested(String propertyValue) {
        if (propertyValue == null || propertyValue.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(propertyValue.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Selected if nothing was requested, or {@code uri:line} is one of the requested ids. */
    public static boolean selected(Set<String> requested, URI uri, int line) {
        return requested.isEmpty() || requested.contains(uri + ":" + line);
    }

    public static String propertyValue(List<ScenarioResult> scenarios) {
        return scenarios.stream().map(ScenarioResult::id).collect(Collectors.joining(","));
    }
}
