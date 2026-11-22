# 01 — Project Overview

## What this project is
An automation testing framework for **ParaBank** (https://parabank.parasoft.com/parabank), a public demo
banking website. It tests the UI with **Selenium** (and **Playwright** as a second engine), describes tests in plain English with **Cucumber
(Gherkin)**, runs them in parallel with **TestNG**, reads/writes test data in **Excel (Apache POI)**,
checks the backend with **Rest Assured**, and runs in **Jenkins**.

## The story the framework tells (end-to-end)
1. Read 10 users from `testdata/ParaBankData.xlsx` (sheet `Users`). Blank username/password → generate one.
2. Register each user on ParaBank. Write the result back into Excel (status, site message, account number).
3. Log in with each user (positive) and with bad inputs (negative).
4. Move money: between a user's own accounts (Transfer Funds) and from user A to user B (Bill Pay).
5. Confirm balances through the REST API (hybrid UI + API check).
6. Test the REST API on its own: 22 operations, each response checked against the OpenAPI spec (guide 12).
7. If fewer than 85% of tests pass, re-run only the failures on the next browser: Selenium Chrome → Selenium Firefox
   → Playwright Chrome, at most 3 runs (guides 10, 13).
8. Produce one report per run plus a combined report. Jenkins runs it all (guide 11).

## Two modules
| Module | What lives there | Why separate |
|---|---|---|
| `automation-core` | Generic tools: config, Excel, data generator, browser setup, UI actions (Selenium + Playwright), API client, OpenAPI contract, reports, retry engine | Reusable by any project; to be published to JFrog Artifactory |
| `parabank-tests` | ParaBank pages, Gherkin features, step definitions, runners | Only meaningful for this one application |

## Important facts about the site (checked 2026-11-21)
- It is shared by everyone on the internet and is reset from time to time → usernames must be unique,
  registration must be repeatable.
- **Real defect (until 2026-11-22):** the UI logged in with *any* username/password, while the REST API correctly
  rejected them. The tests were tagged `@knownDefect`. ParaBank fixed it, and the tag was removed after 4 clean runs
  (guide 08, D65). The API defects that are still open are listed in guide 12.
