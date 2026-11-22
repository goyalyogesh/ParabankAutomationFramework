# 13 — Playwright as a Second UI Engine (Run 3)

Requirement E4: run 3 of the retry engine uses **Playwright** instead of Selenium, switched **by configuration only**.
No page object, step definition or feature file changed to make this work.

Classes (all in `automation-core`, package `core.ui`): `UiEngine`, `UiOptions`, `PlaywrightUiDriver`, `UiSession`.
Tests: `UiDriverContractTest` (abstract) with `SeleniumUiDriverBrowserTest` and `PlaywrightUiDriverBrowserTest`,
plus `PlaywrightSelectorTest`, `UiSessionTest` and `UiEngineTest`.

## How to switch
| Where | Setting | Effect |
|---|---|---|
| `config.properties` | `ui.engine=selenium` (default) or `playwright` | every UI scenario in a plain `mvn test` |
| command line | `-Dui.engine=playwright -Dbrowser=chrome` | same, for one run |
| retry rotation | `-Dbrowser.rotation=chrome,firefox,playwright:chrome` (the default) | run 1 Selenium Chrome, run 2 Selenium Firefox, run 3 Playwright Chrome |
| Jenkins | `BROWSER_ROTATION` parameter, same syntax | same |

A rotation entry is `[engine:]browser[-headless]`. Without a prefix the engine is `selenium`, so old rotations such as
`chrome,firefox,chrome-headless` still work unchanged.

## Why it took no page changes
Page objects have only ever used `UiDriver` (guide 05), an interface with methods like `click("id=login")`. Locators
are strings, not Selenium `By` objects. So a second engine is a second implementation of `UiDriver`:

```
page objects ──► UiDriver ──┬── SeleniumUiDriver   (WebDriver + WebDriverWait)
                            └── PlaywrightUiDriver (Playwright Page + Locator)
Hooks ──► UiSession.start(config.uiOptions())  picks one per scenario, per thread
```

This is the **Strategy pattern**: the algorithm (how to drive a browser) is chosen at runtime by configuration. It
also follows the **Dependency Inversion** principle: high-level page objects depend on an abstraction, not on a library.

## Proof that the engines are interchangeable: one contract test
`UiDriverContractTest` holds 13 checks on one local HTML page: typing, clicking, a JavaScript-filled `<select>`,
visibility, waiting for text, screenshots, `typeBelow`, attributes, `waitForAnyVisible`, tabs, hover, scroll, lists
and late text. The two subclasses only say how to start and stop their engine, so **both engines must pass exactly the
same checks**:

```
mvn -pl automation-core test -Dcore.excludedGroups=none -Dgroups=browser
Tests run: 28 (13 Selenium + 13 Playwright + 2 driver smoke tests), Failures: 0
```

When a third engine arrives (for example Appium for mobile web), it gets a 4-line subclass of this test first.

## Same contract, different library: the translation table
| `UiDriver` rule | Selenium | Playwright |
|---|---|---|
| Single-element action uses the first match | `findElement` returns the first | `locator(...).first()` (Playwright is **strict**: an action on a locator matching 2 elements throws, so we opt out) |
| `id=customer.firstName` | `By.id` | `css=[id="customer.firstName"]` (CSS `#customer.firstName` would mean id `customer` with class `firstName`) |
| `link=Log Out` | `By.linkText` (exact) | `css=a:text-is("Log Out")` (exact, whitespace-normalised) |
| Explicit waits | `WebDriverWait` + `ExpectedConditions` | built-in auto-waiting for actions; a small `until(...)` poll loop for "wait until text/options appear" |
| `type` clears then types | `clear()` + `sendKeys()` | `fill()` (clears itself) |
| Windows/tabs | window handles (strings) | `Page` objects; the driver names them `page-1`, `page-2` … so `windowHandles()` still returns a `Set<String>` |
| `typeBelow` (Selenium 4 relative locator) | `RelativeLocator.with(tagName("input")).below(anchor)` | same maths by hand: visible inputs whose top ≥ the anchor's bottom, nearest by distance between centres (Playwright's `:below()` selector is deprecated) |
| Timeout error | `TimeoutException` | `TimeoutError` |

## Browsers and downloads
- `chrome`: Playwright drives the **installed Google Chrome** (`channel=chrome`) and is told to skip browser downloads.
  Nothing extra is installed.
- `firefox`: Playwright needs its own patched Firefox. It is downloaded once into `tools/playwright/` (git-ignored,
  `playwright.browsers.path` in config) so everything stays inside the project folder.
- Playwright for Java starts a small Node.js driver process per `Playwright.create()`. It ships inside the jar and is
  extracted to a temp folder.

## Lifecycle and parallel runs
`UiSession` is a `ThreadLocal`, like `DriverManager` (guide 05): one session per TestNG thread. For Playwright, each
scenario creates `Playwright → Browser → BrowserContext → Page` and `close()` releases them in reverse order, each in a
`finally`, so one failure doesn't leak the others. Playwright objects are **not thread-safe**, which is why each thread
gets its own.

Excel write-back records `runLabel()`: `CHROME` for Selenium and `PLAYWRIGHT-CHROME` for Playwright, so a row shows
which engine produced it.

## Showing run 3 on a healthy site
The retry demo injects failures into chosen scenarios. `-Dretry.demo.injectRuns=2` keeps the injection on for runs 1
and 2, so those scenarios fail on Selenium Chrome and Selenium Firefox and finally pass on **run 3 = Playwright**:

```
mvn -pl parabank-tests exec:java -Dheadless=true \
  "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out" -Dretry.demo.injectRuns=2 -Dthreshold=100
```

Add `-Dthreshold=100` locally: since the API suite was added, 6 injected failures out of about 47 counted scenarios
still leave 87%, above the default 85%, and no retry would start (seen in Jenkins build 3). Jenkins `DEMO_RETRY=true`
sets both the injection and the 100% threshold.

## Selenium vs Playwright (interview view)
| | Selenium 4 | Playwright |
|---|---|---|
| Protocol | W3C WebDriver (HTTP to a driver binary); BiDi is being added | Chrome DevTools Protocol / patched Firefox and WebKit over one WebSocket |
| Waiting | explicit waits you write | auto-waits before every action (visible, enabled, stable, receives events) |
| Browsers | real Chrome, Firefox, Edge, Safari | Chromium (or installed Chrome/Edge), patched Firefox, WebKit |
| Isolation | one driver = one browser profile | many cheap `BrowserContext`s in one browser |
| Grid / cloud | Selenium Grid, every cloud vendor | fewer cloud options |
| Strength here | the standard; real Safari; biggest community | speed, fewer flaky waits, tracing |

Using both lets the retry engine tell **site problems** (they fail on both engines) apart from **engine or timing
problems** (they pass on the other engine).
