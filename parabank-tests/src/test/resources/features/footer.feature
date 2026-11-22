@selenium4
Feature: Footer link in a new browser tab
  Uses Selenium 4 WindowType.TAB and a Set of window handles.

  Scenario: The Parasoft link opens in its own tab and the bank page stays intact
    Given the ParaBank home page is open
    When the "www.parasoft.com" footer link is opened in a new tab
    Then the new tab shows a page titled with "Parasoft"
    And 2 distinct browser tabs are open
    When the new tab is closed
    Then the ParaBank page is active again
