# Backlog — parked work (agreed 2026-11-22)

Picked from a review of what ParaBank offers vs what the framework tests. Done items move to the build log.

## Scenarios (parked, in order of value)
| # | Scenario | Why it is worth adding | Status |
|---|---|---|---|
| 1 | **Find Transactions**: find the transfer just made by amount, transaction id, date and date range; malformed input refused | Banking search, checked against the API | **Done 2026-11-22** |
| 2 | **Request Loan in the UI**: approved vs denied by down payment | Today only through the API; gives a UI vs API comparison | Parked |
| 3 | **Update Contact Info in the UI**: change address/phone, save, reload, confirm through the API | Persistence check across UI and backend | Parked |
| 4 | **Accounts Overview total**: the "Total" row equals the sum of all accounts (Streams) | Cheap, catches rounding/aggregation bugs | Parked |
| 5 | **Session security**: after logout, browser Back and direct URLs must not show account pages | Security behaviour every bank must have | Parked |
| 6 | **Forgot login info**: customer lookup by personal details | Rarely used flow | Parked (lowest) |

Keep the total small: the public site rate-limits, so more scenarios make runs slower without adding much.

## Sauce Labs (cloud browsers) — **Done 2026-11-22** (guide 15)
- Sign up for the 28-day free trial (no credit card) or apply for Open Sauce (free for open-source repositories).
- Credentials only as environment variables `SAUCE_USERNAME` / `SAUCE_ACCESS_KEY` (Jenkins: credentials), never in git.
- Plan: `DriverFactory` creates a `RemoteWebDriver` to Sauce Labs when the rotation entry is `sauce:<browser>`, e.g.
  `chrome,firefox,sauce:safari`; job marked passed/failed on the Sauce dashboard; without credentials nothing changes.
- First check: one scenario on Sauce Labs, to see whether ParaBank's Cloudflare blocks data-centre addresses.

## JFrog Artifactory — waiting for an account
Publish `automation-core` (plan 05, task 6). Token in `~/.m2/settings.xml` / Jenkins credentials only.

## B02 (UI Bill Pay) — decide whether to remove the `@knownDefect` tag
It has passed in recent runs (D65 policy: remove after several clean runs).
