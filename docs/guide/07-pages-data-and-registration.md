# 07 — Page Objects, Test Data and the Registration Flow

Module: `parabank-tests/src/main` · Packages: `...parabank.pages`, `.data`, `.model`, `.api`, `.service`

## Layers
```
Feature file          "When the customer is registered on ParaBank"
   │
Step class            RegistrationSteps            ← glue only: reads context, calls service, asserts
   │
Service               RegistrationService          ← the business rule (idempotent registration)
   │            ╲
Page objects   RegisterPage      API client  ParaBankApi (Rest Assured)
   │
UiDriver (automation-core)
```
Steps stay thin. If the rule changes ("register through the API instead of the UI"), only the service changes.

## Model (Java records)
- `ParaBankUser` — one Users row. Immutable: `withCredentials(...)` and `withRegistration(...)` return changed copies,
  so a user object can be shared between threads safely.
- `LoginCase` — one LoginNegative row, with `{U01}` placeholders already replaced by U01's username.
- `RegistrationResult` — the user after registration + status (`REGISTERED`, `EXISTING`, `FAILED`) + acknowledgement.

## The workbook (`testdata/ParaBankData.xlsx`)
Created by `CreateTestDataWorkbook` (see the command in its Javadoc). The copy in git is a **blank template**.

**Users** — inputs: UserId, FirstName … SSN, Username, Password (blank = generate).
Written back: Username, Password, Status, Acknowledgement, CustomerId, AccountNumber, RegisteredAt, Browser.

**LoginNegative** — inputs: CaseId, Description, Username, Password, ExpectedError.
Written back: Result, ActualOutcome, ExecutedAt, Browser.

`TestDataRepository` is the only class that knows sheet and column names. All writes go through
`ExcelUtil.updateRow`, which is locked and atomic (guide 04), so 3 threads can write results at the same time.

## Idempotent registration (`RegistrationService`)
1. Row already has credentials **and** REST `GET /login/{u}/{p}` works → `EXISTING`, nothing is registered.
2. Credentials blank → generate (`qa_yyMMddHHmmss_NNN`, password with upper/lower/digits).
3. Fill the form through `RegisterPage`; if the site says "username already exists" → new name, one retry.
4. Read the customer id (REST login) and first account number (`GET /customers/{id}/accounts`).
5. The step writes the result to Excel **immediately**, before any assertion, so generated credentials are never lost.

Run it twice and the second run reports 10 × `EXISTING`. That is what "idempotent" means: running it again changes
nothing.

## Why the API is used inside UI tests
- **Fast setup / checks**: one HTTP call instead of a login through the browser.
- **Truth source**: ParaBank's UI accepts any credentials (defect), but the API rejects wrong ones. So "does this user
  really exist?" is asked through the API.

## Page objects
| Page | Notable detail |
|---|---|
| `LoginPage` | `login()` returns an outcome enum: `LOGGED_IN`, `ERROR_SHOWN`, or `NO_PARABANK_RESPONSE` (Cloudflare blocked the request) |
| `RegisterPage` | Last Name is filled with a **Selenium 4 relative locator** ("the input below First Name"), then double-checked by reading the field's value. The outcome is found with `waitForAnyVisible(success, username error, any field error)` — one wait instead of three try/catches |
| `AccountsOverviewPage` | welcome text "Welcome First Last", Log Out |
| `FooterComponent` | reads a footer link's `href` |
| `SiteGuard` | if the page shows Cloudflare "error code: 1015" (rate limit), fail at once with a clear message instead of a 20 s timeout |

## Things the live site taught us (see BUILD_LOG)
1. **Usernames longer than 20 characters are rejected with the wrong message** ("This username already exists").
   Found by changing one thing at a time with curl: 18 chars → OK, 21 chars → "exists". Format shortened to 19 chars.
2. **Each scenario creates its own `TestDataGenerator`**, so the sequence counter must be `static` (one per JVM),
   or three threads in the same second all produce `_001`.
3. **Too many requests → Cloudflare rate limit** (HTTP 429). Keep the default at 3 threads.
