# Plan 07 — Playwright as a Second UI Engine (Run 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Requirement E4. Run 3 of the retry engine uses Playwright instead of Selenium, switched only by configuration
(`ui.engine=playwright`, or a `playwright:` entry in `browser.rotation`). No page object or step changes.

**Architecture:** Page objects already talk only to `UiDriver` (D-series, guide 05). Add `PlaywrightUiDriver`, a second
implementation of the same interface, and `UiSession`, a per-thread holder that starts the configured engine and closes
it. One abstract browser test (`UiDriverContractTest`) runs the same 13 checks against both engines, which is the proof
that they are interchangeable. The retry engine's `BrowserChoice` gains an `engine` so a rotation entry can say
`playwright:chrome`.

**Tech Stack:** Playwright for Java 1.64.0 (released 2026-10-21; 1.65.x is under two weeks old), Selenium 4.50, TestNG.

---

## File map

| File | Change |
| --- | --- |
| `pom.xml` (parent) | `playwright.version` property + `dependencyManagement` entry |
| `automation-core/pom.xml` | `playwright` dependency, `<optional>true</optional>` |
| `core/ui/UiEngine.java` | new enum `SELENIUM, PLAYWRIGHT` with `from(String)` |
| `core/ui/UiOptions.java` | new record: engine, browser, headless, timeout, Playwright browsers folder |
| `core/ui/PlaywrightUiDriver.java` | new `UiDriver` + `AutoCloseable` on Playwright |
| `core/ui/UiSession.java` | new per-thread start/get/quit for either engine |
| `core/retry/BrowserChoice.java` | `engine` component, `engine:browser[-headless]` syntax |
| `core/retry/MavenRunExecutor.java` | passes `-Dui.engine=` |
| `core/retry/RetryOrchestrator.java` | default rotation `chrome,firefox,playwright:chrome` |
| tests: `UiEngineTest`, `PlaywrightSelectorTest`, `UiDriverContractTest` (abstract) + `SeleniumUiDriverBrowserTest`, `PlaywrightUiDriverBrowserTest`, `RunHistoryTest`, `MavenRunExecutorTest` | |
| `parabank-tests/pom.xml` | `playwright` dependency |
| `ParaBankConfig.java`, `config.properties` | `ui.engine`, `playwright.browsers.path`, `uiOptions()`, `runLabel()` |
| `hooks/Hooks.java` | `UiSession.start(config.uiOptions())` / `UiSession.quit()` |
| `Jenkinsfile`, `README.md`, guide 13, DECISIONS D57-D62, INTERVIEW_QA, BUILD_LOG | docs |

## Design rules

1. **Same behaviour, not same API.** Each `UiDriver` method keeps its Javadoc contract in both engines:
   - single-element actions act on the **first** match (Selenium `findElement`), so Playwright locators use `.first()`
     and never hit Playwright's strict mode;
   - `isVisible` never waits or throws;
   - waits use the configured timeout and fail with a timeout error.
2. **Locator translation** (`PlaywrightUiDriver.selector`):
   - `id=a.b` -> `[id="a.b"]` (a CSS `#a.b` would mean id `a` with class `b`; ParaBank ids contain dots);
   - `name=x` -> `[name="x"]`;
   - `css=` and `xpath=` pass through;
   - `link=Log Out` -> `css=a:text-is("Log Out")` (exact, whitespace-normalised, like Selenium `linkText`).
3. **Windows.** Playwright has pages, not window handles. The driver gives each page an id (`page-1`, `page-2` ...) and
   keeps them in insertion order, so `windowHandles()` / `switchToWindow()` behave like Selenium's.
4. **`typeBelow`.** Selenium 4 relative locators measure bounding boxes in the browser. Playwright's `:below()` layout
   selector is deprecated, so the driver does the same maths itself: visible `input`s whose top is at or below the
   anchor's bottom, nearest by distance between centres.
5. **Browsers.** `chrome` runs Playwright's Chromium engine on the **installed Google Chrome** (`channel=chrome`), so
   nothing is downloaded (`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`). `firefox` needs Playwright's patched Firefox, which is
   downloaded into the project (`tools/playwright`, git-ignored), never into `~/Library/Caches`.
6. **Lifecycle.** One Playwright + browser + context + page per scenario, closed in reverse order. Playwright objects
   are not thread-safe; a per-thread instance (like `DriverManager`) keeps parallel scenarios apart.
7. **Optional dependency.** Playwright is `optional` in `automation-core`: a project that only uses Selenium does not
   download it. `parabank-tests` declares it.

## Tasks

### Task 1: dependency + `UiEngine` + `UiOptions`

- [ ] Parent POM: `<playwright.version>1.64.0</playwright.version>` and `com.microsoft.playwright:playwright` in
  `dependencyManagement`. Core: dependency with `<optional>true</optional>`.
- [ ] Failing test `UiEngineTest`: `from("Playwright")` -> `PLAYWRIGHT`, `from(" selenium ")` -> `SELENIUM`, `from("cypress")`
  throws `IllegalArgumentException` listing the supported values.
- [ ] Implement `UiEngine` (same shape as `BrowserType.from`) and record `UiOptions(UiEngine engine, BrowserType browser,
  boolean headless, Duration timeout, Path playwrightBrowsersPath)`, with `playwrightBrowsersPath` nullable.
- [ ] `mvn -q -pl automation-core test -Dtest=UiEngineTest` -> green.

### Task 2: `PlaywrightUiDriver`

- [ ] Failing test `PlaywrightSelectorTest` for rule 2, including quotes escaped in `id`/`link` values.
- [ ] Implement:
  - `static PlaywrightUiDriver launch(UiOptions)`;
  - every `UiDriver` method per rules 1, 3 and 4;
  - a private `until(timeout, supplier, description)` poll loop (100 ms, through `page.waitForTimeout` so Playwright
    events keep flowing) for the waits Playwright has no built-in for (`waitForText`, `options`, `texts`,
    `waitForNonBlankText`, `waitForAnyVisible`);
  - `close()` closes context, browser, playwright in that order, each in `finally`.
- [ ] Green.

### Task 3: one contract test for both engines

- [ ] Move the body of `SeleniumUiDriverBrowserTest` into `abstract class UiDriverContractTest` (group `browser`) with
  `abstract UiDriver start()`, `abstract void stop()`, `abstract long scrollY()`.
- [ ] `SeleniumUiDriverBrowserTest` and `PlaywrightUiDriverBrowserTest` only start/stop their engine and read `scrollY`.
- [ ] `mvn -pl automation-core test -Dcore.excludedGroups=none -Dgroups=browser` -> 26 browser tests green (13 each),
  and the smoke tests still pass.

### Task 4: `UiSession`

- [ ] `UiSession.start(UiOptions)` returns a `UiDriver` and keeps it per thread:
  - Selenium: `DriverManager.start` + `SeleniumUiDriver`;
  - Playwright: `PlaywrightUiDriver.launch`.
  Starting twice on one thread throws, as in `DriverManager`.
- [ ] `UiSession.quit()` is safe when nothing was started.
- [ ] Unit test: `quit()` without `start()` is a no-op and `isStarted()` is false.
- [ ] Browser test: start Playwright, check `isStarted()`, then quit.

### Task 5: retry engine knows the engine

- [ ] Failing tests:
  - `BrowserChoice.rotation("chrome,playwright:chrome-headless,PLAYWRIGHT:firefox", false)` gives
    `(selenium,chrome,false)`, `(playwright,chrome,true)`, `(playwright,firefox,false)`;
  - labels `chrome`, `playwright chrome (headless)`;
  - `MavenRunExecutor.command` contains `-Dui.engine=playwright`.
- [ ] `BrowserChoice(String engine, String browser, boolean headless)` plus the old 2-argument constructor
  (engine `selenium`).
- [ ] Default rotation `chrome,firefox,playwright:chrome`. Jenkins `BROWSER_ROTATION` default is the same.

### Task 6: ParaBank wiring

- [ ] `config.properties`: `ui.engine=selenium`, `playwright.browsers.path=../tools/playwright`.
- [ ] `ParaBankConfig`:
  - `uiEngine()`;
  - `uiOptions()`;
  - `runLabel()`: `CHROME`, or `PLAYWRIGHT-CHROME`, used for the Excel "browser" columns so a row shows which engine
    wrote it.
- [ ] `Hooks`: `ctx.setUi(UiSession.start(config.uiOptions()))`, `UiSession.quit()`; scenario log shows the engine.
- [ ] `.gitignore`: `tools/` is already ignored (check).

### Task 7: verify live

- [ ] `mvn -pl parabank-tests test -Dui.engine=playwright -Dheadless=true -Dtest=LoginRunner`: all login scenarios
  pass, apart from known defects.
- [ ] Whole UI suite on Playwright: registration, login, transfers, footer.
- [ ] Retry demo that reaches run 3: new `-Dretry.demo.injectRuns=N` (default 1) injects the failure into runs 1..N.
  With `injectRuns=2` the injected scenarios fail on Chrome and Firefox and pass on run 3 = Playwright. Unit test for
  the run-specific argument function. Jenkins `DEMO_RETRY` uses `injectRuns=2`, so one demo build shows all three
  runs.

### Task 8: docs, commit, push, republish

- [ ] Guide `docs/guide/13-playwright-engine.md`, D57-D62, Q&A "Selenium vs Playwright", README, BUILD_LOG, execution
  notes below.
- [ ] Commit as the user, push, republish the docs artifact.

## Execution notes

- Tasks 1-6 went TDD as planned. Browser contract: 28/28 green on the first run (13 Selenium + 13 Playwright + 2 smoke).
  Core unit tests 64 -> 75, parabank unit tests 20.
- `PlaywrightUiDriver.scrollY()` would have been test-only API; replaced by a package-private `evaluate(js)`.
- Live, login suite on Playwright (headless): 18/18 in 31 s; Excel rows labelled `PLAYWRIGHT-CHROME`.
- Live, full suite with `-Dbrowser.rotation=playwright:chrome`: 59 executed, 51 passed, 8 failed = 7 API known defects
  + T03. T03's screenshot shows "Welcome John Smith", account 12345: ParaBank's demo customer, i.e. the site served a
  different session (each Playwright scenario has its own isolated context, so it isn't cookie sharing in the test).
  T03 alone on Playwright: passed. Site flake, exactly what retries are for. 97.9% >= 85%, BUILD SUCCESS.
- The 5 UI known defects passed again (3rd clean run in a row).
