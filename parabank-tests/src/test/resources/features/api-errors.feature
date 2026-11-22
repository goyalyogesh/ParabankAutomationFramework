@api @apiNegative
Feature: ParaBank REST API - errors and validation
  Invalid requests must be rejected with a clear error and must not change any balance.
  {customerId} and {accountId} are replaced by API customer U01's ids, {username} by its username.

  Background:
    Given API customer "U01" is logged in through the REST API

  Scenario Outline: <case> is rejected with HTTP <status>
    When the API request "<request>" is sent
    Then the response status is <status> and the body contains "<message>"

    Examples:
      | case                    | request                                    | status | message                                 |
      | Unknown customer        | GET /customers/99999999                    | 400    | Could not find customer #99999999       |
      | Unknown account         | GET /accounts/99999999                     | 400    | Could not find account #99999999        |
      | Unknown transaction     | GET /transactions/999999999                | 400    | Could not find transaction #999999999   |
      | Wrong password          | GET /login/{username}/WrongPass99          | 400    | Invalid username and/or password        |
      | Non-numeric account id  | GET /accounts/abc                          | 404    |                                         |
      | Deposit, unknown account| POST /deposit?accountId=99999999&amount=10 | 400    | Could not find account number 99999999  |

    @knownDefect
    Examples: Known defect - invalid input crashes the server (HTTP 500 HTML page instead of 400)
      | case                 | request                                                                       | status | message |
      | Invalid account type | POST /createAccount?customerId={customerId}&newAccountType=7&fromAccountId={accountId} | 400    |         |

  # The next three succeed on ParaBank today (real defects). The step undoes any money movement immediately.
  @knownDefect
  Scenario Outline: <case> is rejected and the balance is unchanged
    When the API customer sends a <operation> of <amount> on the main account
    Then the operation was rejected with HTTP 400
    And the main account balance changed by exactly 0.00

    Examples:
      | case                                    | operation  | amount            |
      | Withdrawing more than the balance       | withdrawal | balance plus 1.00 |
      | Withdrawing a negative amount           | withdrawal | -5.00             |
      | Transferring a negative amount          | transfer   | -1.00             |
