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
