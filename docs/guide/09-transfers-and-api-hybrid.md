# 09 — Transfers, Rest Assured Hybrid Checks and Surviving a Rate Limit

Feature: `parabank-tests/src/test/resources/features/transfers.feature` · Data: sheet **Transfers**

## The four kinds of money movement
| Rows | Channel | What happens | Verified how |
|---|---|---|---|
| T01–T03 | Browser | Customer opens a SAVINGS account, then transfers money into it | API balances before/after (exact), UI + API activity |
| B01–B02 | Browser | Customer A pays customer B through Bill Pay | API: payer debited, payee unchanged, payer has a Debit transaction |
| B03 | REST API | Same A → B payment through `POST /billpay` | same checks |
| C01–C03 | REST API | Chain U08 → U09 → U10 → U08 between *different* customers, strictly in order | net change per account (Streams ledger) |

## Hybrid testing: UI for the action, API for the truth
The browser does what a customer does (open account, transfer, pay). The REST API then answers precisely:
exact balances (`BigDecimal`, not the formatted "$1,234.50" on screen), the list of accounts, the transactions.
This is faster and more reliable than scraping tables, and it catches what the UI hides. That's how we found the
Bill Pay defect below.

## Defect found: UI Bill Pay confirms but never debits
The Bill Pay page shows "Bill Payment Complete", yet the payer's balance doesn't change and no transaction appears.
How it was isolated:
1. The scenario failed: expected 485.50, actual 515.50.
2. API: the account had **no** transactions, so this wasn't a timing issue.
3. The page's JavaScript posts to `services_proxy/bank/billpay`. Sent by hand (curl, logged-in session): response
   says success, balance unchanged.
4. The same body to `services/bank/billpay` (the REST endpoint): balance 515.50 → 514.50.

So B01/B02 are tagged `@knownDefect` (assertions unchanged), and **B03** proves the A → B flow through the endpoint
that works. The Excel sheet shows it side by side: B01 balance before = after; B03 before − 7.25 = after.

**Update 2026-11-22:** the site was fixed: B01 debited 515.50 → 485.50 in Excel. B01 passed 4 clean runs and lost its
tag (D65). B02 passed 3 runs, then failed once in Jenkins build 4 with a *different* symptom (no confirmation within
20 s, likely site slowness), then passed in build 6. It keeps `@knownDefect` in its own Examples table until it passes
another clean run. A tag is removed on evidence, not on one lucky pass.

## Finding: `/transfer` has no ownership check and no authentication
The UI only lists *your own* accounts on Transfer Funds. The REST endpoint accepts any two account numbers from
anyone, with no login. The chain scenario (C01–C03) relies on this and documents it. In a real bank this would be
a critical security bug.

## Collections in this feature
| Where | What | Why |
|---|---|---|
| `TransferQueue` | `ArrayDeque<TransferInstruction>` | FIFO: C01 must run before C02. O(1) at both ends, no per-node objects (vs `LinkedList`), not synchronised (vs `Stack`/`Vector`); one thread owns the queue. A failure stops the queue and leaves the rest in `remaining()` |
| `TransferLedger.expectedNetChange` | `flatMap` → `groupingBy(TreeMap)` → `reducing(BigDecimal::add)` | each transfer becomes (sender, −amount) and (receiver, +amount), summed per account. The sum of all changes is 0 (money is moved, never created) |
| Open account check | `Set<Long>` difference: `after.removeAll(before)` | exactly one new account, whatever else the customer already had |
| Balances | `Map<Long, BigDecimal>` via `Collectors.toMap` | before/after snapshots per account |

## BigDecimal, never double
`0.1 + 0.2` in `double` is `0.30000000000000004`. Money uses `BigDecimal`: Rest Assured is configured to parse JSON
numbers as `BigDecimal` (`JsonPathConfig.NumberReturnType.BIG_DECIMAL`), Excel amounts are parsed with
`new BigDecimal("40.50")`, and comparisons use `isEqualByComparingTo` (so `15.0` equals `15`).

## Selenium 4 Actions API
- `AccountServicesMenu.go(Link)` **hovers** over the left-menu link before clicking, then waits for the target page's
  title (an enum maps each link to its page title, e.g. Bill Pay → "Bill Payment Service").
- `AccountsOverviewPage.openActivity(id)` **scrolls** to the account link (`scrollToElement`) and opens it.

## Running in parallel without interference
Balance checks only work if nobody else touches the account at the same time.
- **Browser block (parallel, 3 threads):** each scenario has its own customers: T01–T03 → U01–U03, B01 → U04/U05,
  B02 → U06/U07.
- **API block (sequential):** `ApiRunner` uses the default non-parallel data provider, runs after the browser block,
  and may reuse customers (B03 uses U04/U05, the chain uses U08–U10). API scenarios take 1–2 s each, so sequential
  costs almost nothing.

## Surviving Cloudflare's rate limit
ParaBank is a shared public site behind Cloudflare. Too many requests from one machine → every URL returns
"Error 1015 – You are being rate limited" (HTTP 429) for a few minutes. Seen in practice: a full run of 34 scenarios
got blocked halfway, and every later scenario failed.
- **API:** `ApiClient.send(...)` repeats a request that got 429, waiting for the server's `Retry-After` header if
  present (Cloudflare sent 300 s), otherwise 30 s, 60 s, 90 s. Unit-tested against the JDK's built-in HTTP server
  with a fake sleeper, so the test takes milliseconds. A retried POST is safe: 429 means the request was rejected
  *before* the application saw it.
- **Browser:** `SiteGuard` recognises both forms of the 1015 page, waits 30/60/90 s and reloads the same URL; after
  3 waits it fails with a clear message. It runs on every page open and every menu navigation.
- Observed in the final run: one 429 with `Retry-After: 300` during the API block → waited, then passed.

## Working copy of the workbook
`testdata/ParaBankData-template.xlsx` is tracked in git and never written. The first run copies it to
`testdata/ParaBankData.xlsx` (git-ignored), which keeps credentials and results between runs. When the template
gains sheets or rows (Transfers, B03), they're added to an existing working copy, and rows it already has are never
changed.

## Known limitation
Every own-transfer run opens a new account ($90 minimum deposit from the main account) and moves money out, so the
main balances keep shrinking (515.50 → 195.50 after a few runs). The tests check *changes*, not absolute values,
so they keep working until a balance goes negative. The public site is reset by Parasoft from time to time, and
registration then creates the customers again (idempotent registration, guide 07).
