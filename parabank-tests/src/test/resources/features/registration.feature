@registration
Feature: Register bank customers from Excel
  Customers come from testdata/ParaBankData.xlsx, sheet "Users".
  Blank usernames and passwords are generated, and every result is written back to the same row.
  Registration is idempotent: a customer whose Excel credentials already work (checked through the REST API)
  is not registered again.

  Scenario Outline: Register customer <userId>
    Given customer "<userId>" from the Users sheet
    When the customer is registered on ParaBank
    Then the registration is acknowledged
    And the customer exists in the ParaBank backend
    And the registration result is written back to Excel

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
