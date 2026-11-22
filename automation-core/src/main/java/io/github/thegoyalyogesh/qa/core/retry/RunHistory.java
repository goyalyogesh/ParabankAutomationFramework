package io.github.thegoyalyogesh.qa.core.retry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every attempt of every scenario across runs. The pass rate is cumulative: a scenario counts as passed if it passed
 * in any run so far. Scenarios tagged with the known-defect tag are reported but never counted or retried.
 */
public final class RunHistory {

    /** One execution of one scenario. */
    public record Attempt(int run, String browser, boolean passed) {
    }

    private final String knownDefectTag;
    /** First result seen per scenario (name, feature, tags); insertion order = order of run 1. */
    private final Map<String, ScenarioResult> scenarios = new LinkedHashMap<>();
    private final Map<String, List<Attempt>> attempts = new LinkedHashMap<>();

    public RunHistory(String knownDefectTag) {
        this.knownDefectTag = knownDefectTag;
    }

    public void record(int run, String browser, Collection<ScenarioResult> results) {
        for (ScenarioResult result : results) {
            scenarios.putIfAbsent(result.id(), result);
            attempts.computeIfAbsent(result.id(), id -> new ArrayList<>())
                    .add(new Attempt(run, browser, result.passed()));
        }
    }

    public List<ScenarioResult> scenarios() {
        return List.copyOf(scenarios.values());
    }

    public List<Attempt> attempts(String scenarioId) {
        return List.copyOf(attempts.getOrDefault(scenarioId, List.of()));
    }

    public boolean isKnownDefect(String scenarioId) {
        return scenarios.get(scenarioId).hasTag(knownDefectTag);
    }

    public boolean passedInAnyRun(String scenarioId) {
        return attempts.getOrDefault(scenarioId, List.of()).stream().anyMatch(Attempt::passed);
    }

    /** Percentage of counted scenarios (not known defects) that passed in any run; 100 if nothing is counted. */
    public double passRate() {
        List<String> counted = scenarios.keySet().stream().filter(id -> !isKnownDefect(id)).toList();
        if (counted.isEmpty()) {
            return 100.0;
        }
        long passed = counted.stream().filter(this::passedInAnyRun).count();
        return 100.0 * passed / counted.size();
    }

    /** Scenarios to run again: not a known defect and not passed in any run yet (in run-1 order). */
    public List<String> retryCandidates() {
        return scenarios.keySet().stream()
                .filter(id -> !isKnownDefect(id))
                .filter(id -> !passedInAnyRun(id))
                .toList();
    }

    /** {@link #retryCandidates()} as full results (the executor needs the names). */
    public List<ScenarioResult> retryScenarios() {
        return retryCandidates().stream().map(scenarios::get).toList();
    }

    public List<ScenarioResult> knownDefects() {
        return scenarios.values().stream().filter(s -> s.hasTag(knownDefectTag)).toList();
    }

    /** Failed in their first run and passed later: flaky or environment-dependent, worth investigating. */
    public List<String> passedOnRetry() {
        return attempts.entrySet().stream()
                .filter(e -> !e.getValue().get(0).passed() && e.getValue().stream().anyMatch(Attempt::passed))
                .map(Map.Entry::getKey)
                .toList();
    }
}
