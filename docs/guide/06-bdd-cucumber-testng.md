# 06 — BDD with Cucumber + TestNG (and parallel runs)

Module: `parabank-tests/src/test` · Packages: `...parabank.runners`, `.hooks`, `.steps`, `.context`

## How a run flows
```
mvn -pl parabank-tests test -Dheadless=true
  └─ Surefire reads testng-parabank.xml
       ├─ <test> 1 "Register customers from Excel"   → RegistrationRunner (tags @registration)
       │     └─ parallel @DataProvider → 3 threads → 10 scenarios (U01–U10)
       └─ <test> 2 "Login and Selenium 4 features"   → LoginRunner (tags "@login or @selenium4")
             └─ parallel @DataProvider → 3 threads → 18 scenarios
```
The two `<test>` blocks run **one after the other** (login needs registered customers). Inside each block the
scenarios run **in parallel**.

## Runners
A runner is a class that extends `AbstractTestNGCucumberTests` and says *which* features, glue and tags to run.
```java
@CucumberOptions(features = "classpath:features",
        glue = {"...parabank.hooks", "...parabank.steps"},
        tags = "@registration",
        plugin = {"summary", "html:...", "json:...", "rerun:target/rerun/registration.txt",
                  "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"})
public class RegistrationRunner extends AbstractTestNGCucumberTests {
    @Override @DataProvider(parallel = true)
    public Object[][] scenarios() { return super.scenarios(); }
}
```
- `AbstractTestNGCucumberTests` turns every scenario into one row of a TestNG data provider.
- Overriding `scenarios()` with `parallel = true` makes TestNG run those rows on a thread pool.
- Pool size = Surefire property `dataproviderthreadcount`, which the POM sets from `-Dthreads` (default 3).

## Suites
| File | What runs | Command |
|---|---|---|
| `testng-parabank.xml` (default) | registration, then login + footer | `mvn -pl parabank-tests test` |
| `testng-registration.xml` | registration only | `... -DsuiteXmlFile=testng-registration.xml` |
| `testng-login.xml` | login + footer only (needs registered users in Excel) | `... -DsuiteXmlFile=testng-login.xml` |
| `testng-unit.xml` | fast unit tests, no browser | `... -DsuiteXmlFile=testng-unit.xml` |

> Lesson from the first run: `-Dcucumber.filter.tags=...` **replaces the tags of every runner**, so both runners ran
> the registration scenarios (20 instead of 10). Use the dedicated suite files instead.

## Hooks (`Hooks.java`)
| Hook | Order | Does |
|---|---|---|
| `@Before(order = 0)` | first | Starts this thread's browser (`DriverManager.start`) and wraps it in `SeleniumUiDriver` |
| `@After(order = 100)` | runs first | On failure: screenshot → attached to the report (Base64) and saved as a PNG |
| `@After("@registration", order = 50)` | then | On failure: writes `FAILED` + reason to the Users row |
| `@After("@negativeLogin", order = 50)` | then | Writes PASS/FAIL + what happened to the LoginNegative row |
| `@After(order = 0)` | last | Quits the browser and clears the `ThreadLocal` |

`@Before` hooks run from low to high order, `@After` hooks from **high to low**. That's why the screenshot (100) is
taken before the browser is closed (0).

## Dependency injection with PicoContainer
Step classes and hooks need to share per-scenario state (the browser, the current customer, the login case).
`cucumber-picocontainer` creates **one `TestContext` per scenario** and passes it into every constructor that asks:
```java
public LoginSteps(TestContext ctx) { this.ctx = ctx; }
```
Because each scenario gets its own instance, parallel scenarios never see each other's state, and no step class needs
static fields.

## Features (Gherkin)
- `registration.feature`: a Scenario Outline whose Examples are only **Excel keys** (`U01`…`U10`). The data itself is
  in Excel, so business users edit the workbook, not the feature file.
- `login.feature`: positive logins U01–U10; negative cases N01–N07 split into two Examples tables. The table tagged
  `@knownDefect` holds the cases that fail because of the ParaBank bug (UI accepts any credentials).
- `footer.feature` (`@selenium4`): opens the Parasoft footer link in a new tab and checks the window handles.

## Reports produced by one run
| Path | What |
|---|---|
| `target/reports/spark.html` | Extent Spark report, both runners, Base64 screenshots, "Known defect" category |
| `target/cucumber/*.html`, `*.json` | Cucumber's own report per runner (JSON feeds Jenkins later) |
| `target/rerun/*.txt` | failed scenarios as `file:line` — input for the Plan 04 retry engine |
| `target/screenshots/*.png` | failure screenshots, file name includes thread id + nanoTime |
| `target/logs/parabank.log` | Log4j2 log; every line shows the thread name (`TestNG-PoolService-2`) |
