# Design Decisions Log

Each entry: what we decided, why, and what we rejected. Newest at the bottom.

| # | Decision | Why | Rejected alternatives |
|---|---|---|---|
| D1 | Java 21 | Current LTS used by most companies; runs on JDK 25/27 too | 17 (older), 25 (less adopted) |
| D2 | Maven multi-module (`automation-core` + `parabank-tests`) | Generic utilities become a reusable, versioned library | Single module (utilities not reusable) |
| D3 | Publish `automation-core` to JFrog Artifactory (free cloud) | Standard way companies share internal libraries; consumers add one dependency | Copying JARs; local folder repo |
| D4 | Chrome primary, Firefox secondary; visible locally, headless in Jenkins | Two engines (Blink, Gecko) catch browser-specific bugs; headless is needed on CI | IE (retired, impossible on macOS), Safari (no headless, no parallel) |
| D5 | Selenium Manager (built into Selenium 4.6+) for drivers | No extra library; downloads matching chromedriver/geckodriver automatically | WebDriverManager (extra dependency, same job) |
| D6 | One `WebDriver` per thread via `ThreadLocal` | Parallel tests must never share a browser | Static driver (breaks in parallel) |
| D7 | Page objects use a `UiDriver` interface, locators are strings | Engine can switch to Playwright (Plan 07) without rewriting pages | PageFactory `@FindBy` (ties pages to Selenium) |
| D8 | Excel rows read as `List<Map<String,String>>` keyed by header | Columns can move without breaking code; easy to stream/filter | Index-based access (breaks when columns move) |
| D9 | Excel writes: one JVM-wide lock + write to temp file then atomic rename | Parallel threads can't corrupt the workbook; a crash never leaves a half-written file | No locking (lost updates); one file per thread (hard to read) |
| D10 | Config precedence: file < environment variable < `-D` property | Same code runs locally and in Jenkins; CI overrides without editing files | Hard-coded values; separate code paths for CI |
| D11 | Unique usernames `qa_<yyMMddHHmmss>_<seq>` (19 chars; see D30) | Public shared site; names must not collide with other people or parallel runs | Fixed names (fail on second run) |
| D12 | `@knownDefect` tag for UI login-with-any-credentials bug | Tests stay honest (they fail), but don't trigger retries or hide in the 85% math | Asserting buggy behaviour (hides the defect) |
| D13 | Run 3 = headless Chrome now; Playwright engine in Phase 2 | Deliver core value first; abstraction (D7) makes the switch a config change | Playwright in Phase 1 (+20–25% effort) |
| D14 | Cumulative pass % across runs | A test that passes on retry is counted as passed; matches common retry practice | Run-1-only pass % |
| D15 | Reports: Extent Spark + Cucumber HTML per run + combined | Plain HTML, screenshots, opens anywhere and in Jenkins | Allure (needs CLI/plugin to view) |
| D16 | Log4j2; core library depends on `log4j-api` only, test module adds `log4j-core` + `log4j-slf4j2-impl` | A library must not force a logging implementation on its users; the SLF4J bridge captures Selenium's logs | `System.out` (no levels/files); bundling log4j-core in the library |
| D17 | Screenshots embedded as Base64 in Extent, plus PNG files named `<browser>_<test>_t<threadId>_<nanoTime>.png` | Parallel threads never overwrite each other's screenshots; report is a single portable file | Shared file name like `failure.png` (overwritten in parallel) |
| D18 | TestNG suite XML files picked with `mvn test -DsuiteXmlFile=...`; Surefire forced to the TestNG provider | One clear command per suite; parallel settings live in XML | Hard-coded suite in POM |
| D19 | Collections with a real job: `ConcurrentHashMap` user registry, `ArrayDeque` transfer queue, `Set` of account numbers, Streams for aggregation | Shows Collections knowledge through actual needs (thread-safe sharing, ordered processing, uniqueness) | Using them only in toy examples |
| D20 | Selenium 4 features in real flows: relative locator (Register), Actions scroll/hover (Accounts Overview), `WindowType.TAB` + handle `Set` (footer link) | Demonstrates modern Selenium in context | Separate demo tests unrelated to banking |
| D21 | Planning docs (spec, roadmap, plans) committed under `docs/superpowers/` | Reviewers see the process: requirements → design → plan → code | Keeping them local |
| D22 | Cucumber 7.34.9, not 8.x | 8.1.0 was 6 days old; we only take versions at least 2 weeks old. 7.x is still maintained | 8.x (too new) |
| D23 | Rest Assured 6.2.0 | Brings Groovy 5, which runs on the JDK 27 our Maven uses; 5.x's Groovy 4 is older | 5.5.x |
| D24 | Own `ExtentCucumberPlugin` (~150 lines) instead of the third-party Extent adapter | We control screenshots, known-defect category and shared report; no version lock to an adapter | `extentreports-cucumber7-adapter` |
| D25 | PicoContainer DI, one `TestContext` per scenario | Steps/hooks share state without statics; parallel scenarios stay isolated | Static fields (break in parallel); Spring (heavy) |
| D26 | Two sequential `<test>` blocks (registration → login), parallel inside each | Login needs registered users; parallelism still within each phase | One block (login could run before registration) |
| D27 | Scenario Outline examples hold only Excel keys (`U01`, `N03`) | Data lives in one place (Excel); feature files stay readable | Full data in Gherkin tables (duplicated with Excel) |
| D28 | Idempotent registration: REST check first; write back to Excel immediately | Re-runs don't create duplicates; generated credentials are never lost even if a later check fails | Always register (new users every run); write at the end (lost on failure) |
| D29 | Results of negative logins written in an `@After` hook | Recorded for passes and failures alike | Writing in the last step (skipped when an earlier step fails) |
| D30 | Usernames kept ≤ 20 chars | ParaBank rejects longer names with a misleading "already exists" (found in the live run) | 21-char format (every registration failed) |
| D31 | Rate-limit guard (`SiteGuard`) + default 3 threads | Cloudflare returns 429 / "error code: 1015" under bursts; fail fast with a clear message | Silent 20 s timeouts; more threads |
| D32 | Hybrid checks: act through the UI, verify through the REST API | Exact `BigDecimal` balances and transaction lists; faster and more reliable than scraping; exposed the Bill Pay defect | UI-only checks (formatted text, misses hidden failures) |
| D33 | `BigDecimal` for all money; JSON parsed with `NumberReturnType.BIG_DECIMAL` | `double` cannot represent cents exactly | `double`/`float` |
| D34 | `ArrayDeque` queue for ordered transfers, failure stops the queue | FIFO, O(1), no locking needed (single owner) | `LinkedList` (node per element), `Stack` (LIFO, synchronised) |
| D35 | Browser scenarios parallel with partitioned customers; `@api` scenarios in a separate sequential block | Balance checks need exclusive accounts; API scenarios are fast, so sequential is cheap | Everything parallel (flaky balances); everything sequential (slow) |
| D36 | UI Bill Pay defect → `@knownDefect`; added REST Bill Pay (B03) to prove the A → B flow | Keep honest assertions and still cover the requirement through a working channel | Asserting the buggy behaviour; dropping the A → B requirement |
| D37 | Rate-limit handling: API retries 429 honouring `Retry-After`; UI waits 30/60/90 s and reloads; both give up with a clear message | Public shared site behind Cloudflare; a block shouldn't fail a whole run | Failing immediately; unlimited retries (hangs) |
| D38 | Workbook template tracked in git, working copy git-ignored; new template sheets/rows merged into an existing copy | Runs never dirty git; credentials survive between runs | Writing to the tracked file (every run changes git) |
| D39 | Gson declared explicitly | Rest Assured needs an object mapper to send a `Map` as JSON; relying on a transitive test-scope copy is fragile | Hand-built JSON strings (escaping bugs) |
| D40 | Retry engine as a Java orchestrator in core (`exec:java`), one `mvn test` process per run | Reusable by any Cucumber project, unit-testable loop (fake executor), clean JVM per run like separate CI builds | Surefire `rerunFailingTestsCount` (same browser, same JVM, no threshold); TestNG `IRetryAnalyzer` (per test, no browser switch); Jenkins-only scripting (not testable, not reusable) |
| D41 | Cumulative pass rate; `@knownDefect` excluded and never retried | Rate reflects the product, not the luck of a run; known bugs can't hide or inflate failures | Rate of the last run only; counting known defects |
| D42 | Retry selection by `uri:line` in the TestNG data provider (`RetryAwareCucumberTests`), not Cucumber property filters | Property filters replace the runner's tags (every runner re-ran every scenario: verified live) | `-Dcucumber.features=file:line`, `-Dcucumber.filter.name`, rerun files per runner |
| D43 | Read results from Cucumber JSON, ignore Maven's exit code | JSON has per-scenario status and tags; exit code is non-zero whenever any scenario fails | Surefire XML (one "test" per data-provider row, no tags) |
| D44 | Fault injection (`-Dretry.demo.injectFailure`, run 1 only) to demonstrate retries | The retry path must be shown working on a healthy site, without editing tests | Breaking the site/test data; waiting for a real flaky failure |
| D45 | Combined report as one static HTML page generated by the orchestrator | No extra library; links to each run's Extent/Cucumber reports; works offline and in Jenkins | Merging Extent reports (no per-run history view) |
| D46 | Jenkins from the official WAR inside the project (`tools/jenkins/`, git-ignored), not Homebrew | Project rule: everything in the project folder; nothing system-wide; easy to delete | `brew install jenkins-lts` (system service); Docker (another dependency) |
| D47 | Jenkins configured as code (JCasC + Job DSL), pipeline from SCM (`Jenkinsfile`) | Reproducible from git, reviewable, no UI clicking | Manual job setup (not reproducible); freestyle job |
| D48 | Local-only Jenkins: `127.0.0.1`, login required, generated admin password (mode 600, git-ignored) | Jenkins executes shell commands, so open access = remote code execution | Default open port; password in yaml |
| D49 | Relax `DirectoryBrowserSupport.CSP` on the local Jenkins only | Extent report needs scripts/styles; acceptable for a single-user local server | Keep default (Extent blank); download reports instead (recommended on shared servers) |
| D50 | Workbook for CI outside the workspace (`$JENKINS_HOME/parabank-data`), forwarded with `retry.passThrough` | Customers survive builds; fewer registrations = less load on the rate-limited site | Workspace copy (10 new customers per build) |
| D51 | Flag known defects that pass ("possibly fixed") in console and combined report | A stale `@knownDefect` tag hides a regression; seen live when ParaBank's behaviour changed | Ignore passes of known defects; auto-remove the tag (needs a human to confirm) |
| D52 | Contract validation with Atlassian swagger-request-validator as a Rest Assured filter, spec kept as a local copy | Validates status, content type and JSON schema of real responses with one line; local copy = changes are reviewed | Hand-written JSON schemas (drift from the spec); validating against the live spec URL (tests change silently) |
| D53 | Never call admin endpoints (`cleanDB`, `initializeDB`, `setParameter`, JMS); enforced by a unit test | They reset/reconfigure the shared public site for every user | Calling them for "clean" test data |
| D54 | `ApiCoverageTest`: spec operations = implemented ∪ excluded | New/renamed endpoints or path typos fail fast in the unit suite | Manual coverage lists in docs |
| D55 | Defect scenarios undo money movement inside the When step | Test data stays usable even though the assertion fails | `@After` cleanup (skipped data on crashes, harder to read) |
| D56 | Amounts relative to the balance (`balance plus 1.00`, `max(balance, 0)`) | Works whatever the account holds; a fixed $100,000 probe overdrew a CI customer | Fixed large amounts |
