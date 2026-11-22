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
| D11 | Unique usernames `qa_<timestamp>_<seq>` | Public shared site; names must not collide with other people or parallel runs | Fixed names (fail on second run) |
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
