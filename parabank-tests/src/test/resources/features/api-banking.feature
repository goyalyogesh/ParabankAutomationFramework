@api @apiSuite
Feature: ParaBank REST API - banking operations
  Full API suite (Plan 06). Every amount is checked exactly (BigDecimal) through follow-up API calls.
  Runs in the sequential API block after the browser scenarios; customer U01's main account is the CHECKING
  account created at registration.

  Background:
    Given API customer "U01" is logged in through the REST API

  Scenario: Customer profile returned by the API matches the registration data
    Then the API customer profile matches the Users sheet
    And every account returned for the customer belongs to the customer

  Scenario: Updating the phone number is saved, and the original is restored
    When the API customer's phone number is changed to "555-000-1234"
    Then the API returns phone number "555-000-1234"
    And the API customer's original phone number is restored

  Scenario: Deposit and withdrawal change the balance by exactly the amount
    When 12.34 is deposited into the main account through the API
    Then the main account balance changed by exactly 12.34
    And a "Credit" transaction of 12.34 is found by amount, by today's date, by date range and by type
    When 12.34 is withdrawn from the main account through the API
    Then the main account balance changed by exactly 0.00
    And the newest transaction found by id is a "Debit" of 12.34

  Scenario: Opening a SAVINGS account through the API moves the opening deposit
    When a new SAVINGS account is opened through the API from the main account
    Then the customer has exactly one new account, of type SAVINGS
    And the opening deposit left the main account and is the new account's balance
    # keeps the main balance stable across runs (otherwise every run moves money out)
    When the opening deposit is transferred back to the main account
    Then the main account balance changed by exactly 0.00

  Scenario: A small loan is approved and creates a loan account
    When a loan of 100.00 with a down payment of 10.00 is requested from the main account
    Then the loan is approved with a new LOAN account of balance 100.00
    And the main account balance changed by exactly -10.00

  # Defect: approved anyway, and the down payment overdraws the account (the step refunds the down payment)
  @knownDefect
  Scenario: A loan whose down payment exceeds the balance is denied
    When a loan of 1000.00 with a down payment of the main balance plus 1.00 is requested
    Then the loan is denied with message "error.insufficient.funds.for.down.payment"
    And the main account balance changed by exactly 0.00

  Scenario: Buying and selling shares
    When 3 shares of "QAPB" are bought at 4.50 each from the main account
    Then the customer holds 3 "QAPB" shares, and the position is found by its id
    And the main account balance changed by exactly -13.50
    When the 3 "QAPB" shares are sold at 5.00 each
    Then the customer no longer holds "QAPB" shares
    And the main account balance changed by exactly 1.50

  # Defect: every history request answers 400 "Could not find position", even for ParaBank's own demo data
  # (position 12345 of demo customer john). Read-only, so it uses that seeded position.
  @knownDefect
  Scenario: The price history of a position can be read
    Then the price history of position 12345 can be read for the last 30 days
