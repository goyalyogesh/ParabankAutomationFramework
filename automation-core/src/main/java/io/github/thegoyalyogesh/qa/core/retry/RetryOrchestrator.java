package io.github.thegoyalyogesh.qa.core.retry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Run 1 executes every scenario. While the cumulative pass rate (known defects excluded) is below the threshold and
 * runs are left, the next run executes only the scenarios that haven't passed yet, on the next browser of the rotation.
 * <p>
 * Command line (from the repository root, after {@code mvn install -DskipTests}):
 * <pre>
 * mvn -pl parabank-tests exec:java [-Dthreshold=85] [-Dretry.maxRuns=3]
 *     [-Dbrowser.rotation=chrome,firefox,playwright:chrome] [-Dheadless=true] [-Dthreads=3]
 *     [-Dretry.passThrough=testdata.file] [-Dretry.demo.injectFailure=&lt;regex of scenario names to fail&gt;]
 *     [-Dretry.demo.injectRuns=1]
 * </pre>
 */
public final class RetryOrchestrator {

    public static final String KNOWN_DEFECT_TAG = "@knownDefect";
    /** Run 1 Selenium Chrome, run 2 Selenium Firefox, run 3 Playwright on Chrome (requirement E4). */
    public static final String DEFAULT_ROTATION = "chrome,firefox,playwright:chrome";

    /** System properties forwarded to every run, if set; more with {@code -Dretry.passThrough=key1,key2}. */
    private static final List<String> PASS_THROUGH = List.of("threads", "timeout.seconds", "app.url", "api.url", "suiteXmlFile",
            "sauce.region", "sauce.build");

    private final ThresholdPolicy policy;
    private final List<BrowserChoice> rotation;
    private final RunHistory history;
    private final RunExecutor executor;
    private final List<RunSummary> summaries = new ArrayList<>();

    public RetryOrchestrator(ThresholdPolicy policy, List<BrowserChoice> rotation, String knownDefectTag,
                             RunExecutor executor) {
        this.policy = policy;
        this.rotation = List.copyOf(rotation);
        this.history = new RunHistory(knownDefectTag);
        this.executor = executor;
    }

    public RunHistory orchestrate() {
        List<ScenarioResult> onlyScenarios = List.of();
        for (int run = 1; ; run++) {
            BrowserChoice browser = rotation.get((run - 1) % rotation.size());
            say("Run %d on %s: %s", run, browser.label(),
                    onlyScenarios.isEmpty() ? "all scenarios" : onlyScenarios.size() + " scenario(s) to retry");

            List<ScenarioResult> results = executor.execute(run, browser, onlyScenarios);
            if (run == 1 && results.isEmpty()) {
                throw new IllegalStateException("Run 1 produced no scenario results - the build or test run is broken. "
                        + "Check the Maven log of run 1.");
            }
            history.record(run, browser.label(), results);
            int passed = (int) results.stream().filter(ScenarioResult::passed).count();
            summaries.add(new RunSummary(run, browser, results.size(), passed, results.size() - passed, history.passRate()));
            say("Run %d done: %d executed, %d passed, %d failed. Cumulative pass rate %.1f%% (threshold %.1f%%, known defects excluded)",
                    run, results.size(), passed, results.size() - passed, history.passRate(), policy.thresholdPercent());

            if (!policy.anotherRun(history, run)) {
                return history;
            }
            onlyScenarios = history.retryScenarios();
        }
    }

    public List<RunSummary> summaries() {
        return List.copyOf(summaries);
    }

    public RunHistory history() {
        return history;
    }

    /** {@code -Dkey=value} for every default or {@code retry.passThrough} key that is set, in that order. */
    static List<String> passThroughArguments(Properties properties) {
        List<String> extra = Arrays.stream(properties.getProperty("retry.passThrough", "").split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .toList();
        return Stream.concat(PASS_THROUGH.stream(), extra.stream())
                .distinct()
                .filter(key -> properties.getProperty(key) != null)
                .map(key -> "-D" + key + "=" + properties.getProperty(key))
                .toList();
    }

    /**
     * Demo only: {@code -Dinject.failure=<pattern>} for runs 1..{@code runs}, so the retry path can be shown on a healthy
     * site. With {@code runs=2} the injected scenarios reach run 3 (Playwright in the default rotation).
     */
    static IntFunction<List<String>> demoArguments(String pattern, int runs) {
        return run -> pattern != null && run <= runs ? List.of("-Dinject.failure=" + pattern) : List.of();
    }

    static void say(String format, Object... args) {
        System.out.println("[retry] " + String.format(Locale.ROOT, format, args));
    }

    public static void main(String[] args) {
        ThresholdPolicy policy = new ThresholdPolicy(
                Double.parseDouble(System.getProperty("threshold", "85")), Integer.getInteger("retry.maxRuns", 3));
        List<BrowserChoice> rotation = BrowserChoice.rotation(
                System.getProperty("browser.rotation", DEFAULT_ROTATION), Boolean.getBoolean("headless"));
        Path moduleDir = Path.of(System.getProperty("retry.moduleDir", ".")).toAbsolutePath().normalize();
        Path reportsDir = Path.of(System.getProperty("retry.reportsDir", moduleDir.resolve("reports").toString()))
                .toAbsolutePath().normalize();
        List<String> passThrough = passThroughArguments(System.getProperties());
        MavenRunExecutor executor = new MavenRunExecutor(moduleDir, reportsDir,
                MavenRunExecutor.mavenCommand(System.getProperty("maven.cmd"), System.getProperty("maven.home"),
                        System.getProperty("os.name", "").startsWith("Windows")), passThrough,
                demoArguments(System.getProperty("retry.demo.injectFailure"), Integer.getInteger("retry.demo.injectRuns", 1)));
        executor.cleanReports();
        RetryOrchestrator orchestrator = new RetryOrchestrator(policy, rotation, KNOWN_DEFECT_TAG, executor);

        RunHistory history = orchestrator.orchestrate();
        Path report = CombinedReport.write(reportsDir, history, orchestrator.summaries(), policy);

        say("Combined report: %s", report);
        Stream.of(history.passedOnRetry().isEmpty() ? null : "Passed only on retry: " + history.passedOnRetry(),
                        history.retryCandidates().isEmpty() ? null : "Still failing: " + history.retryCandidates(),
                        "Known defects (not counted): " + history.knownDefects().size(),
                        history.knownDefectsThatPassed().isEmpty() ? null
                                : "Known defects that PASSED (fixed? verify and remove the tag): "
                                + history.knownDefectsThatPassed().stream().map(ScenarioResult::name).toList())
                .filter(line -> line != null)
                .forEach(line -> say("%s", line));
        if (!policy.met(history)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "Pass rate %.1f%% is below the threshold of %.1f%% after %d run(s). See %s",
                    history.passRate(), policy.thresholdPercent(), orchestrator.summaries().size(), report));
        }
        say("PASSED: %.1f%% >= %.1f%%", history.passRate(), policy.thresholdPercent());
    }
}
