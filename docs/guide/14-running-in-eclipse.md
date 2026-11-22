# 14 — Opening and Running the Project in Eclipse

Checked with the Eclipse IDE 2026 build on this Mac (m2e 2.8 included, TestNG plugin not installed by default).

## 1. One-time setup
1. **TestNG plugin**: Help → Eclipse Marketplace → search "TestNG" → install **TestNG for Eclipse** → restart.
   Without it Eclipse has no "Run As → TestNG Test". The Maven way (section 4) works without it.
2. **Optional: Cucumber plugin**: Marketplace → "Cucumber Eclipse" gives colours and step navigation in `.feature`
   files. Not needed to run anything.
3. **A JDK 21 or newer**: Settings → Java → Installed JREs → Add → Standard VM →
   `/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home` (the Homebrew JDK). Tick it as default.
   The project compiles for Java 21 (`maven.compiler.release` in the root POM); any newer JDK works.

## 2. Import
File → Import → Maven → **Existing Maven Projects** → Root directory: the repository folder → Finish.
Eclipse shows three projects: `banking-automation-parent` (the parent POM), `automation-core`, `parabank-tests`.
m2e reads the POMs, downloads dependencies, and links `parabank-tests` to the `automation-core` project in the
workspace, so a change in the core library is used immediately (no `mvn install` needed inside Eclipse).

If a project shows a red mark: right-click it → Maven → **Update Project** (Alt+F5) → select all → OK.

Eclipse's own files (`.project`, `.classpath`, `.settings/`) are git-ignored: the POMs are the single source of truth.

## 3. Run with the TestNG plugin (fast, and you can debug)
In `parabank-tests`:

| To run | Right-click | Run As |
|---|---|---|
| Everything in order (registration → login → transfers → API) | `testng-parabank.xml` | TestNG Suite |
| One area | `testng-registration.xml`, `testng-login.xml`, `testng-transfers.xml` | TestNG Suite |
| One runner | `runners/LoginRunner.java` (or Registration, Transfer, Api) | TestNG Test |
| Unit tests, no browser | `testng-unit.xml` | TestNG Suite |

On a fresh clone run registration (or the full suite) first: it creates `testdata/ParaBankData.xlsx` and the 10
customers that the other runners log in with.

**Settings** go in Run → Run Configurations → (your launch) → Arguments → **VM arguments**, the same `-D` options as
on the command line:
```
-Dheadless=true -Dthreads=2 -Dbrowser=firefox -Dui.engine=playwright
```
Leave the working directory at its default (the `parabank-tests` project): `config.properties` finds the workbook
at `../testdata/`.

**Debug**: set a breakpoint in a step definition or page object, then Debug As → TestNG Test. Use `-Dthreads=1` and
leave `headless` off so you can watch the browser while you step.

**Threads**: Maven passes `-Dthreads` to TestNG, but the TestNG plugin does not. Without help, TestNG would run
10 scenarios at once, and the public site rate-limits that (Cloudflare error 1015). `RetryAwareCucumberTests` applies
`-Dthreads` itself (class `ScenarioThreads`, default 3), so Eclipse, IntelliJ and Maven all behave the same.

## 4. Run through Maven inside Eclipse (exactly like the command line / Jenkins)
Right-click `parabank-tests` → Run As → **Maven build...** → Goals, for example:

| Goals | What |
|---|---|
| `test -Dheadless=true` | full suite |
| `test -DsuiteXmlFile=testng-login.xml -Dui.engine=playwright` | login on Playwright |
| `exec:java -Dheadless=true` | **retry engine**: Chrome → Firefox → Playwright, per-run + combined reports |

Tick **Resolve Workspace artifacts** so the core library comes from the workspace.
The retry engine starts a fresh `mvn test` for every run. It uses the `bin/mvn` of the Maven that started it; if that
cannot be found (Eclipse's embedded Maven has no `bin/mvn`) it falls back to `mvn` on the PATH, which an Eclipse
started from the Dock does not have. Two fixes, either one:
- Settings → Maven → Installations → Add → `/opt/homebrew/opt/maven/libexec` and select it; or
- add `-Dmaven.cmd=/opt/homebrew/bin/mvn` to the goals.
The child runs use the installed `automation-core`, so run `install -DskipTests` on the parent project once after
changing the core library.

## 5. Where the results are
Refresh the project (F5) after a run:
- `parabank-tests/target/reports/spark.html` (Extent), `target/cucumber/`, `target/screenshots/`;
- retry engine: `reports/run-N/` and `reports/combined/index.html` at the repository root;
- the TestNG plugin's own results view and `test-output/` (git-ignored).
Open an HTML report with right-click → Open With → Web Browser.
