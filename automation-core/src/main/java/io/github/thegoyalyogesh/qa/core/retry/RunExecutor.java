package io.github.thegoyalyogesh.qa.core.retry;

import java.util.List;

/** Runs the suite once. {@code onlyScenarios} empty = everything; otherwise only these scenarios (from a previous run). */
@FunctionalInterface
public interface RunExecutor {

    List<ScenarioResult> execute(int run, BrowserChoice browser, List<ScenarioResult> onlyScenarios);
}
