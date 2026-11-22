package io.github.thegoyalyogesh.qa.core.ui;

import java.util.List;

/**
 * Engine-neutral UI actions used by page objects. Locators are strings like {@code id=username}
 * (see {@link Locator}). Every action waits for the element to be ready before acting.
 */
public interface UiDriver {

    void open(String url);

    void click(String locator);

    /** Clears the field, then types the text. */
    void type(String locator, String text);

    /** Visible text of the element, trimmed. */
    String text(String locator);

    /** True if at least one matching element is displayed right now; never throws or waits. */
    boolean isVisible(String locator);

    void selectByVisibleText(String locator, String optionText);

    /** Visible text of the currently selected option. */
    String selectedOption(String locator);

    /** Texts of all options; waits until the list has at least one option (lists filled by JavaScript). */
    List<String> options(String locator);

    /** Waits until the element's text contains {@code expected}. */
    void waitForText(String locator, String expected);

    String title();

    String currentUrl();

    /** PNG screenshot of the current page. */
    byte[] screenshot();
}
