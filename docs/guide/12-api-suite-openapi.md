# 12 — The REST API Suite: Contract, Functional and Negative Tests

Features: `api-banking.feature`, `api-contract.feature`, `api-errors.feature` (all `@api`, run one at a time by
`ApiRunner` after the browser scenarios). Client: `ParaBankApi`. Spec: `src/test/resources/openapi/parabank-openapi.yaml`.

## Three kinds of API tests
| Kind | Question it answers | Example |
|---|---|---|
| Functional | Does the operation do the right thing? | deposit 12.34 → balance +12.34 exactly; the Credit is found by amount, date, range, type |
| Contract | Does the response look like the spec says? | `Transaction.date` must be a date-time string; it's a number → defect |
| Negative | Are bad requests rejected cleanly, without side effects? | unknown account → 400 "Could not find account #…"; balance unchanged |

## Contract testing with the OpenAPI spec
`OpenApiContract` (core) wraps Atlassian's `swagger-request-validator`: `api.validating(contract)` adds a Rest Assured
filter that checks the request (path, parameters) and the response (status, content type, JSON schema) of every
call. A mismatch fails with a precise report, e.g.:
```
[Path '/0/date'] Instance type (integer) does not match any allowed primitive type (allowed: ["string"])
```
The spec is a **local copy**: a change on the server shouldn't silently change what the tests expect. Updating it
is a reviewed commit.

## Coverage guarded by a unit test
`ApiCoverageTest` compares the spec's 27 operations with `ParaBankApi.OPERATIONS` (22) and
`EXCLUDED_OPERATIONS` (5). A new endpoint in the spec, or a typo in a path template, fails the unit suite.
The 5 excluded ones (`cleanDB`, `initializeDB`, `setParameter`, JMS start/stop) reset or reconfigure the shared
public site for everyone; a second test fails if any source file even mentions them.

## Defects found by the API suite
| Scenario | Expected | Actual |
|---|---|---|
| Withdraw balance + $1 | 400 | 200, account overdrawn (no limit: $100,000 from $275 worked) |
| Withdraw −$5 | 400 | 200, balance *increases* |
| Transfer −$1 | 400 | 200, money moves backwards |
| `createAccount` with type 7 | 400 JSON error | 500 + HTML error page |
| Loan with down payment > balance | denied | approved; account overdrawn by the down payment |
| Position price history | list of prices | 400 "Could not find position" for every position, even ParaBank's demo data |
| `Transaction.date` | date-time string (spec) | number (epoch ms) |

Each is a `@knownDefect` scenario: the assertion states the correct behaviour, so the day ParaBank fixes it the
retry engine reports "Known defects that passed".

## Tests that change money must clean up
Every money-moving scenario leaves the account as it found it, or as close as possible:
- the overdraft/negative scenarios **undo** the movement inside the step, before the assertion fails;
- the phone number is restored; the opening deposit of a new account is transferred back;
- the loan scenario refunds an approved down payment.
Net effect per run: −$8.50 (the small loan's down payment, +$1.50 share profit). Before these fixes a run moved
$108 out, and the defect scenarios once left an account at −$14.50.

## Probing safely
Before writing assertions the real behaviour was probed with curl: one request per second (rate limit), and every
balance changed by a probe was restored. One probe ($100,000 withdrawal) overdrew a CI customer; it was restored
immediately. Lesson: probe with amounts relative to the balance.

## Design notes
- `raw(method, pathAndQuery)` returns the response unchecked for negative tests; everything else asserts 200.
- `AccountType.code()` = ordinal: the API takes 0/1/2 although the spec's description says CHECKING/SAVINGS/LOAN.
- Transaction dates are stored as midnight of the server's day: the date checks use the transaction's own date,
  not the test machine's clock (which differs around midnight).
- New positions are found by set difference of position ids before/after, like new accounts in the UI tests.
