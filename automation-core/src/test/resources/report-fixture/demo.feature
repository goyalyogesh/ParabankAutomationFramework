Feature: Report fixture

  Scenario: Passing scenario
    Given a step that passes

  @knownDefect
  Scenario: Failing scenario
    Given a step that passes
    When a step that fails
    Then a step that is skipped
