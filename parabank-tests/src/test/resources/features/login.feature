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

    @knownDefect
    Examples: Known defect - the UI accepts any credentials (the REST API rejects them correctly)
      | caseId |
      | N01    |
      | N02    |

    Examples: Input validation
      | caseId |
      | N03    |
      | N04    |
      | N05    |
      | N06    |
      | N07    |
