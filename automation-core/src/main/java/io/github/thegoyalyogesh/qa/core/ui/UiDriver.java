package io.github.thegoyalyogesh.qa.core.ui;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /** Current value of an input field (DOM property {@code value}). */
    String value(String locator);

    /** Attribute exactly as written in the HTML, e.g. {@code href}. */
    String attribute(String locator, String name);

    /** Waits until the element is visible. */
    void waitVisible(String locator);

    /** Waits until any locator is visible; returns the first visible one (in argument order) or empty on timeout. */
    Optional<String> waitForAnyVisible(Duration timeout, String... locators);

    /** Selenium 4 relative locator: types into the nearest {@code input} below the anchor element. */
    void typeBelow(String anchorLocator, String text);

    /** Opens a new tab (Selenium 4 {@code WindowType.TAB}), loads the URL, switches to it and returns its handle. */
    String openInNewTab(String url);

    String currentWindow();

    /** All open tab/window handles; a Set because handles are unique. */
    Set<String> windowHandles();

    void switchToWindow(String handle);

    /** Closes only the current tab. Call {@link #switchToWindow} afterwards. */
    void closeCurrentWindow();
}
