# 01 — Project Overview

## What this project is
An automation testing framework for **ParaBank** (https://parabank.parasoft.com/parabank), a public demo
banking website. It tests the UI with **Selenium**, describes tests in plain English with **Cucumber
(Gherkin)**, runs them in parallel with **TestNG**, reads/writes test data in **Excel (Apache POI)**,
checks the backend with **Rest Assured**, and runs in **Jenkins**.

## The story the framework tells (end-to-end)
1. Read 10 users from `testdata/ParaBankData.xlsx` (sheet `Users`). Blank username/password → generate one.
2. Register each user on ParaBank. Write the result back into Excel (status, site message, account number).
3. Log in with each user (positive) and with bad inputs (negative).
4. Move money: between a user's own accounts (Transfer Funds) and from user A to user B (Bill Pay).
5. Confirm balances through the REST API (hybrid UI + API check).
6. If fewer than 85% of tests pass, re-run only the failures on another browser — up to 3 runs.
7. Produce one report per run plus a combined report. Jenkins runs it all.

## Two modules
| Module | What lives there | Why separate |
|---|---|---|
| `automation-core` | Generic tools: config, Excel, data generator, browser setup, UI actions | Reusable by any project; published to JFrog Artifactory |
| `parabank-tests` | ParaBank pages, Gherkin features, step definitions, runners | Only meaningful for this one application |

## Important facts about the site (checked 2026-11-21)
- It is shared by everyone on the internet and is reset from time to time → usernames must be unique,
  registration must be repeatable.
- **Real defect:** the UI logs in with *any* username/password. The REST API correctly rejects them.
  Our tests for this are tagged `@knownDefect`.
