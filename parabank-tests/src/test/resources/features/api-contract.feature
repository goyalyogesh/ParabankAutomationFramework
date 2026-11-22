@api @apiContract
Feature: ParaBank REST API - OpenAPI contract
  Each response is validated against the published OpenAPI spec (local copy:
  src/test/resources/openapi/parabank-openapi.yaml): status, content type and JSON schema.

  Background:
    Given API customer "U01" is logged in through the REST API

  Scenario Outline: <operation> matches the OpenAPI contract
    When "<operation>" is called with contract validation
    Then the response matched the contract

    Examples:
      | operation          |
      | login              |
      | customer           |
      | customer accounts  |
      | account            |
      | customer positions |

    # Transaction.date is a date-time string in the spec, but the API returns a number (epoch milliseconds)
    @knownDefect
    Examples: Known defect - transaction dates are numbers, not date-time strings
      | operation            |
      | account transactions |
