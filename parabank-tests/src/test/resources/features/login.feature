@login
Feature: Login
  Registered customers (from Excel) can log in and out.
  Invalid logins must be rejected; cases come from the LoginNegative sheet and results are written back.

  Scenario Outline: Registered customer <userId> can log in and out
    Given registered customer "<userId>" from the Users sheet
    When the customer logs in
    Then the Accounts Overview page shows the customer's name
    And the customer can log out

    Examples:
      | userId |
      | U01    |
      | U02    |
      | U03    |
      | U04    |
      | U05    |
      | U06    |
      | U07    |
      | U08    |
      | U09    |
      | U10    |

  @negativeLogin
  Scenario Outline: Login is rejected - <caseId>
    Given login case "<caseId>" from the LoginNegative sheet
    When the user tries to log in with those credentials
    Then the user is not logged in
    And the expected error message is shown

    # Known defect: the UI accepts wrong credentials (the REST API rejects them). Looked fixed on 2026-11-22 morning
    # (4 clean runs, tag removed), then REGRESSED the same afternoon: failed on Sauce Labs Edge and on local Chrome.
    @knownDefect
    Examples: Wrong credentials are rejected (known defect, regressed 2026-11-22)
      | caseId |
      | N01    |
      | N02    |
      | N07    |

    Examples: Input validation
      | caseId |
      | N03    |
      | N04    |
      | N05    |
      | N06    |
