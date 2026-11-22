# 10 — Retry Engine, Pass-Rate Threshold and the Combined Report

Code: `automation-core/src/main/java/.../core/retry/` · Run it: `mvn -pl parabank-tests exec:java`

## The requirement
Run everything on Chrome. If the pass rate is below 85%, run **only the failed scenarios** again on another browser.
Still below 85%? One more run on a third browser, then stop. Keep a report per run and one combined report.

## One command
```bash
mvn -q install -DskipTests                       # once: core + tests compiled and installed
mvn -pl parabank-tests exec:java -Dheadless=true
```
Options: `-Dthreshold=85`, `-Dretry.maxRuns=3`, `-Dbrowser.rotation=chrome,firefox,chrome-headless`, `-Dthreads=3`.
Exit code: 0 if the threshold is met, non-zero otherwise (so Jenkins marks the build red).

Output:
```
reports/
├── run-1/   reports/spark.html (Extent) · cucumber/*.html|json · screenshots/ · rerun/ · maven.log
├── run-2/   only if run 1 was below the threshold
└── combined/index.html   verdict, one row per run, every scenario × run, known defects
```

## How it works
```
RetryOrchestrator.main
  └─ loop run = 1..maxRuns
       browser = rotation[run-1]
       MavenRunExecutor.execute(run, browser, onlyScenarios)   ← a separate "mvn test" process
            → copies target/{cucumber,reports,screenshots,rerun} to reports/run-N
            → CucumberJsonReader reads reports/run-N/cucumber/*.json
       RunHistory.record(results)
       ThresholdPolicy.anotherRun? (rate < threshold AND runs left AND something to retry)
       onlyScenarios = RunHistory.retryCandidates()
  └─ CombinedReport.write → reports/combined/index.html
```

| Class | One job |
|---|---|
| `ScenarioResult` | id (`uri:line`), feature, name, tags, passed |
| `CucumberJsonReader` | Cucumber JSON → results. Passed only if every hook and step passed (a failing `@Before` is a failure too) |
| `RunHistory` | every attempt of every scenario; cumulative pass rate; retry candidates; "passed on retry" |
| `ThresholdPolicy` | the decision "another run?" (a record with validation in the compact constructor) |
| `BrowserChoice` | parses `chrome-headless` into (chrome, headless=true) |
| `RunExecutor` | interface: "run these scenarios on this browser". Lets the loop be unit-tested with a fake |
| `MavenRunExecutor` | the real executor: `ProcessBuilder("mvn", "test", ...)` |
| `CombinedReport` | static HTML, no libraries, escapes scenario names |
| `RetrySelection` + `RetryAwareCucumberTests` | `-Dretry.scenarios` → keep only those data-provider rows |

## Key decisions
- **Cumulative pass rate.** A scenario counts as passed if it passed in *any* run. Run 1: 30/35 → retry 5 →
  3 pass → the rate is computed over all 35, not over the 5 retried.
- **Known defects are excluded** from the rate *and* never retried. They fail for a real reason (an application
  bug), so retrying them only wastes time, and counting them would hide real failures behind them.
  5 known defects out of 35 would otherwise cap the rate at 85.7%.
- **The scenario id is `uri:line`** (`classpath:features/login.feature:14`), taken from Cucumber's JSON. A retry run
  gets `-Dretry.scenarios=<id>,<id>`, and `RetryAwareCucumberTests` (the base class of every runner) drops all
  data-provider rows whose pickle `uri:line` isn't listed. Each runner keeps its own tags, so a login scenario re-runs
  only in the login runner, an API scenario only in the sequential API block.
- **A new Maven process per run.** Nothing leaks between runs (static registries, ThreadLocal drivers, the Extent
  report singleton). It's also how Jenkins would run it, and the orchestrator only needs the JSON left behind.
  Maven's exit code is ignored (it is non-zero whenever any scenario fails); the JSON is the truth.
- **Run 1 with no results is an error**, not "100% of nothing". It means the build or the run is broken.
- **Retrying on a *different* browser** separates browser-specific failures from flaky ones: a scenario that fails
  on Chrome and passes on Firefox shows up as "Passed on retry" in the combined report.

## Proving it works without breaking the site: fault injection
On a healthy site run 1 is already above 85%, so the retry path would never run. `Hooks.injectFailure` fails
scenarios whose name matches `-Dinject.failure=<regex>`. The orchestrator forwards
`-Dretry.demo.injectFailure=<regex>` to **run 1 only**:
```bash
mvn -pl parabank-tests exec:java -Dheadless=true "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out"
```
Run 1: 6 injected failures → 24/30 = 80% < 85% → run 2 on Firefox with exactly those 6 → they pass → 100%.
This is the same idea as chaos testing: inject a fault on purpose to check the safety net.

## What went wrong while building it (and why the design is what it is)
1. **First try: `-Dcucumber.features=login.feature:14,...`.** Run 2 executed 24 scenarios instead of 6. Cucumber
   treats any filter given as a property (features with lines, `cucumber.filter.name`, `cucumber.filter.tags`) as a
   *replacement* for every filter in `@CucumberOptions`, so the runners lost their tags and all 4 runners ran the
   6 logins. The report then crashed on duplicate results (`Collectors.toMap` without a merge function).
2. **Second try: `-Dcucumber.filter.name=^(?:\Q...\E|...)$`.** Same problem, same reason. Worse: a login that failed
   in the login runner (rate limited by the 4× traffic) "passed" in the registration runner, so the pass rate lied.
3. **Fix: filter the TestNG data provider ourselves.** Cucumber's filters stay untouched, the runner's tags still
   apply, and only the listed rows run. Verified live: run 2 = 6 scenarios, all in `login.json`, 0 elsewhere.
4. **Then ApiRunner silently lost its 2 scenarios** (run 1: 33 instead of 35). Annotations are not inherited by an
   overriding method: the base class's `scenarios()` override had no `@DataProvider`, and ApiRunner (which doesn't
   override it again) failed with "requires a @DataProvider named: scenarios". Fixed in the base class, with
   `RunnerDataProviderTest` checking every runner.

Lesson for interviews: verify the *numbers* of a retry run, not just the verdict. Both bugs produced a green
"PASSED" at first.

## Testing the engine
- `RetryOrchestratorTest` uses a scripted fake executor: run 1 → 70%, run 2 (Firefox, only a,b,c) → 80%,
  run 3 (headless Chrome, only b,c) → stop at max runs. Also: no retry when run 1 is good, early stop, broken run 1.
- `MavenRunExecutorTest` checks the exact command line, without starting Maven.
- `RetrySelectionTest` checks parsing and `uri:line` matching; `RunnerDataProviderTest` (parabank unit suite) checks
  every runner still exposes the `scenarios` data provider.
- `CombinedReportTest` checks links, escaping (`<logs>` → `&lt;logs&gt;`) and sections.
