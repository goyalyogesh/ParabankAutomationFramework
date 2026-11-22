# 08 — Login, Known Defects, Selenium 4 Features and Collections

## Positive login (U01–U10)
`Given registered customer "U01"` takes the user from `UserRegistry` (filled during registration in the same JVM)
or, if the login suite runs alone, from Excel. Then: log in → "Welcome Aarav Sharma" on Accounts Overview → log out.

## Negative login (N01–N07, from Excel)
| Case | Input | Expected | Actual (2026-11-21) |
|---|---|---|---|
| N01 | U01's username + wrong password | rejected | **logged in** — defect |
| N02 | unknown username | rejected | **logged in** — defect |
| N03 | empty username | "Please enter a username and password." | ✔ |
| N04 | empty password | same | ✔ |
| N05 | both empty | same | ✔ |
| N06 | `' OR '1'='1` | not logged in | ✔ (Cloudflare 403 page; ParaBank never sees it) |
| N07 | 300 × "a" in both fields | rejected | **logged in** — defect |

The result of each case is written back to the LoginNegative sheet by an `@After("@negativeLogin")` hook, so it is
recorded for passes **and** failures.

## Known defects (`@knownDefect`)
ParaBank's UI logs in with **any** credentials; the REST API correctly rejects them. Our tests assert the *correct*
behaviour, so N01, N02 and N07 fail, and that is intended. They are tagged `@knownDefect`:
- the Extent report puts them in a "Known defect" category with an explanation and a screenshot;
- Plan 04 excludes them from the 85% threshold and from retries (retrying a real bug only wastes time).

We do **not** change the assertion to "expect login to succeed". That would make the suite green while hiding a bug.

> N07 was first planned as a validation case (an error was seen once during the spike). The real run showed it logs
> in, and a manual check confirmed it, so it moved to the known-defect table. Plan from observations, then verify.

### Update 2026-11-22: fixed on the site, tag removed
From 2026-11-22 the retry engine's "Known defects that passed" line listed N01, N02 and N07 in every run. The Excel
write-back confirmed the cause: the error message was really shown, so the site had changed. The decision was not
to remove the tag after the first pass, but to watch a few clean runs. After 4 clean runs (2 local, Jenkins builds 4
and 6) the `@knownDefect` tag was removed (D65). The cases now sit in a normal Examples table, "Wrong credentials are
rejected". If ParaBank regresses, they fail and count against the threshold, which is exactly what we want.

### Update 2026-11-22 afternoon: regressed, tag back
The first Sauce Labs runs (Edge) failed N01, N02 and N07: the site logged in with a wrong password again. Before
blaming the new cloud browser, the same three cases were run on local Chrome: same failures, so ParaBank itself had
regressed. An earlier `curl` probe had shown the same thing (a failed login landing on "Welcome John Smith"). The
cases went back under `@knownDefect` with a dated comment (D70). The tests did their job: they caught a regression
hours after the fix. The same afternoon B01 (UI Bill Pay: payer not debited) regressed too, on local Chrome and
Sauce Safari and Edge alike (Jenkins #8), and was re-tagged the same way.

## Selenium 4 features in real flows
| Feature | Where | API |
|---|---|---|
| Relative locator | Register: Last Name = input below First Name | `RelativeLocator.with(By.tagName("input")).below(anchor)` |
| New tab | Footer: open the Parasoft link in a new tab | `driver.switchTo().newWindow(WindowType.TAB)` |
| Window handles as a `Set` | Footer: exactly 2 distinct handles, close child, back to parent | `getWindowHandles()` → `Set<String>` |
| `getDomAttribute` / `getDomProperty` | link `href`, input `value` | replace the deprecated `getAttribute` |

(The Actions API, for hover and scroll, comes in Plan 03 on the Accounts Overview page.)

## Collections and Streams used for a real reason
| Where | Collection | Why this one |
|---|---|---|
| `UserRegistry` | `ConcurrentHashMap<String, ParaBankUser>` | 3 threads add users at the same time; no global lock needed |
| `ExtentCucumberPlugin` | `ConcurrentHashMap<UUID, ExtentTest>` and `ConcurrentHashMap<Path, ExtentReports>` | events from parallel scenarios; one shared report per output path |
| Window handles | `Set<String>` (`LinkedHashSet`) | handles are unique; order kept for readable logs |
| Excel rows | `List<Map<String,String>>` + Streams (`filter`, `map`, `findFirst`) | look up a row by key, convert rows to records |
| Write-back values | `LinkedHashMap` | keeps column order predictable |
| `UserRegistry.all()` | Stream `sorted(Comparator.comparing(ParaBankUser::userId)).toList()` | stable order regardless of which thread finished first |
| `waitForAnyVisible` | `Arrays.stream(locators).filter(...).findFirst()` | first visible outcome wins |
