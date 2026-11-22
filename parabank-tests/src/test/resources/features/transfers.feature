@transfers
Feature: Money transfers
  Transfers come from testdata/ParaBankData.xlsx, sheet "Transfers"; results are written back to the same rows.
  Every balance is checked through the REST API with exact decimal amounts.
  Browser scenarios run in parallel and each uses its own customers; @api scenarios run afterwards, one at a time.

  @ownTransfer
  Scenario Outline: <transferId> - customer moves money into a new savings account
    Given transfer "<transferId>" from the Transfers sheet
    And the paying customer is logged in
    When the customer opens a new "SAVINGS" account funded from the main account
    Then exactly one new account appears in the customer's account list
    When the customer transfers the amount from the main account to the new account
    Then the transfer is confirmed on screen
    And both balances change by exactly the amount
    And the new account's activity shows "Funds Transfer Received" for the amount

    Examples:
      | transferId |
      | T01        |
      | T02        |
      | T03        |

  @findTransfer
  Scenario Outline: <transferId> - customer finds the transfer with every Find Transactions search
    Given transfer "<transferId>" from the Transfers sheet
    And the paying customer is logged in
    When the customer opens a new "SAVINGS" account funded from the main account
    And the customer transfers the amount from the main account to the new account
    Then searching the new account by amount finds exactly the transfer, as a credit
    And searching the main account by the transfer's transaction id finds it, as a debit
    And searching the new account by its booking date, and by a date range around it, finds the transfer
    And Find Transactions refuses malformed input with a message:
      | search        | input      | message                |
      | BY_AMOUNT     | abc        | Invalid amount         |
      | BY_ID         | 12a        | Invalid transaction ID |
      | BY_DATE       | 2026-11-22 | Invalid date format    |
      | BY_DATE_RANGE | 11/22/2026 | Invalid date format    |

    Examples:
      | transferId |
      | F01        |

  # The page checks only the shape MM-DD-YYYY, and the API answers 200 with an empty list for a date that does not
  # exist, so the customer sees "no transactions" instead of an error (found 2026-11-22).
  @findTransactions @knownDefect
  Scenario: Find Transactions rejects a date that does not exist
    Given customer "U09" is logged in
    When the customer searches the main account by the date "02-30-2026"
    Then Find Transactions says "Invalid date format"

  # The API books dates as midnight UTC; the page formats them with the browser's local time zone, so anywhere west of
  # UTC (e.g. Toronto) every transaction is shown one day early: API 2026-11-22, page 11-21-2026 (found 2026-11-22).
  @findTransactions @knownDefect
  Scenario: Find Transactions shows the booking date that the API returns
    Given customer "U10" is logged in
    When the customer deposits "1.11" through the API and looks that transaction up by its id
    Then the date shown is the booking date from the API

  @billPay
  Scenario Outline: <transferId> - customer pays another customer through Bill Pay
    Given transfer "<transferId>" from the Transfers sheet
    And the paying customer is logged in
    When the customer pays the other customer through Bill Pay
    Then the payment to the other customer is confirmed on screen
    And the payer's balance goes down by exactly the amount
    And the payee's balance does not change, because Bill Pay treats the payee as an external company
    And the payer's transactions include the bill payment

    # Known defect: confirmation shown, payer never debited. Looked fixed on 2026-11-22 morning (4 clean runs, tag
    # removed), then REGRESSED the same afternoon: failed on local Chrome, Sauce Safari and Sauce Edge (Jenkins #8).
    @knownDefect
    Examples: UI Bill Pay (known defect, regressed 2026-11-22)
      | transferId |
      | B01        |

    # B02: same defect originally. Passed in 3 clean runs, then failed in Jenkins build 4 with a different symptom
    # (no confirmation within 20 s). Kept tagged until it passes again in a couple of clean runs.
    @knownDefect
    Examples: Known defect (under watch) - the UI showed "Bill Payment Complete" but never debited the payer; see B03 for the REST path
      | transferId |
      | B02        |

  @apiBillPay @api
  Scenario Outline: <transferId> - customer pays another customer through the REST Bill Pay endpoint
    Given transfer "<transferId>" from the Transfers sheet
    When the paying customer pays the other customer through the REST Bill Pay endpoint
    Then the payer's balance goes down by exactly the amount
    And the payee's balance does not change, because Bill Pay treats the payee as an external company
    And the payer's transactions include the bill payment

    Examples:
      | transferId |
      | B03        |

  @apiTransfer @api
  Scenario: Chain of transfers between different customers through the REST API, in order
    Given the CHAIN transfers from the Transfers sheet are queued in sheet order
    When the queue is processed through the REST API
    Then every account's balance changed by its expected net amount
