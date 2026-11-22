package io.github.thegoyalyogesh.qa.core.retry;

import java.util.Set;

/**
 * One scenario's outcome in one run. {@code id} is {@code uri:line} (e.g. {@code classpath:features/login.feature:21}),
 * the format Cucumber accepts to run exactly that scenario again.
 */
public record ScenarioResult(String id, String feature, String name, Set<String> tags, boolean passed) {

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
