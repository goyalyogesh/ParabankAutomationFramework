# 15 — Sauce Labs: Real Browsers in the Cloud

## Why
The Mac runs Chrome and Firefox. Real users also use **Safari** and **Edge**, often on Windows. Sauce Labs provides
those browsers on real operating systems, records a video of every test, and is the "cloud grid" most companies use.
The account (trial) allows **3 browsers at the same time**, the same as the framework's default `-Dthreads=3`.

## Design: a third engine, nothing else changes
`UiEngine` is now `SELENIUM | PLAYWRIGHT | SAUCE`. `SAUCE` is Selenium driving a `RemoteWebDriver` in the cloud, so
`SeleniumUiDriver`, every page object and every step stay exactly as they are (Strategy pattern again).

| Piece | What it does |
|---|---|
| `SauceLabs` (core, `driver`) | Builds the capabilities and starts the `RemoteWebDriver` at `https://ondemand.<region>.saucelabs.com:443/wd/hub`; marks the job passed/failed |
| `BrowserType` | Now `CHROME, FIREFOX, SAFARI, EDGE`. Safari/Edge locally fail with a hint to use `sauce:` |
| `DriverManager.start(Supplier<WebDriver>)` | Keeps a browser made elsewhere (the remote one) in the same `ThreadLocal` |
| `UiSession` | `SAUCE` case; `reportResult(name, passed)` sends the scenario name and result to the dashboard (no-op locally, never fails a test) |
| `Hooks.quitBrowser` | Calls `reportResult` before closing the browser |

Platforms (defaults, override with `-Dsauce.platform.<browser>=...`): Chrome, Firefox, Edge on **Windows 11**; Safari
on **macOS 15** (Safari 18). `browserVersion=latest`. Chrome/Firefox/Edge get a 1920×1080 screen.

## Credentials: never in git
- Locally: `.env` in the repository root (git-ignored, permissions 600):
  ```
  SAUCE_USERNAME=...
  SAUCE_ACCESS_KEY=...
  ```
  Load it into the shell before running: `set -a; . ./.env; set +a`.
- The code reads only the **environment** (`SauceLabs.required`), and fails with a clear message if they are missing.
- Jenkins: `start-jenkins.sh` copies the two values from `.env` into JCasC secret files (`tools/jenkins/secrets/`,
  git-ignored, 600). JCasC creates the credential **sauce-labs** from them, so they are not in git and not in Jenkins'
  environment. The Jenkinsfile binds them with `withCredentials` **only** when the rotation contains `sauce:`, and
  Jenkins masks them in the console log.
- If a key was ever shared (chat, screenshot), regenerate it in Sauce Labs → User Settings and update `.env`.

## Running
```bash
set -a; . ./.env; set +a
mvn -pl parabank-tests test -Dui.engine=sauce -Dbrowser=safari                       # whole suite on Safari
mvn -pl parabank-tests test -Dui.engine=sauce -Dbrowser=edge -DsuiteXmlFile=testng-login.xml
mvn -pl parabank-tests exec:java -Dheadless=true -Dbrowser.rotation=chrome,sauce:safari,sauce:edge   # retries in the cloud
```
Optional: `-Dsauce.build="..."` groups the sessions on the dashboard (Jenkins sets `Jenkins <job> #<build>`),
`-Dsauce.region=eu-central-1` for an EU account. Both are passed on to every retry run.

Jenkins: `BROWSER_ROTATION=chrome,sauce:safari,sauce:edge` (the parameter check already allows `:`).

## Verified (2026-11-22)
- Footer scenario on Sauce Chrome (Windows 11), Safari 18 (macOS 15) and Edge (Windows 11): passed; dashboard shows
  the scenario name, "passed" and the build name.
- Registration, 10 customers, **3 Safari sessions in parallel**: 10/10.
- Login suite on Edge, 3 in parallel: 15/18. The 3 failures were not Sauce Labs: ParaBank's "accepts any password"
  defect came back the same afternoon (same failures on local Chrome), so N01/N02/N07 are `@knownDefect` again.
- ParaBank's Cloudflare did not block Sauce Labs' data-centre addresses.
- Jenkins build #8, rotation `chrome,sauce:safari,sauce:edge`, demo retry on: run 1 local Chrome 41/62 passed (6
  injected failures, Cloudflare rate limits, regressions); run 2 Sauce Safari 1/8; run 3 Sauce Edge 6/7, 98% overall.
  The one still failing was B01: the payer is not debited, on all three browsers, so ParaBank regressed; re-tagged
  `@knownDefect` (D70). Credentials were bound only for this build and masked in the log.

## Things to know
- A cloud session takes 10–30 s to start, so cloud runs are slower; use them for retries and cross-browser checks,
  local browsers for fast feedback.
- `headless` is ignored on Sauce Labs (real VMs with a screen; the video shows the test).
- Unit tests (`SauceLabsTest`) check endpoint, capabilities, platform override and the missing-credentials message
  with fake values, no network.
