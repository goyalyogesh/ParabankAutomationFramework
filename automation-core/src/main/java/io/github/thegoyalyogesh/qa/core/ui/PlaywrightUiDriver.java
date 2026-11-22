package io.github.thegoyalyogesh.qa.core.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.github.thegoyalyogesh.qa.core.driver.BrowserType;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link UiDriver} backed by Playwright. Same contract as {@link SeleniumUiDriver}, so page objects don't know which
 * engine runs them:
 * <ul>
 *   <li>single-element actions use the <b>first</b> match (like Selenium's {@code findElement}), so Playwright's
 *       strict mode never fires;</li>
 *   <li>pages get ids ({@code page-1}, {@code page-2} ...) that play the role of Selenium window handles;</li>
 *   <li>{@code chrome} runs on the installed Google Chrome (no download); {@code firefox} uses Playwright's own
 *       Firefox, downloaded into {@link UiOptions#playwrightBrowsersPath()}.</li>
 * </ul>
 * Not thread-safe: one instance per thread (see {@link UiSession}). Close it to stop the browser.
 */
public final class PlaywrightUiDriver implements UiDriver, AutoCloseable {

    private static final Duration POLL = Duration.ofMillis(100);
    private static final Duration NAVIGATION_TIMEOUT = Duration.ofSeconds(60);

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Duration timeout;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private int pageCounter;
    private Page page;

    private PlaywrightUiDriver(Playwright playwright, Browser browser, Duration timeout) {
        this.playwright = playwright;
        this.browser = browser;
        this.timeout = timeout;
        this.context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        context.setDefaultTimeout(timeout.toMillis());
        context.setDefaultNavigationTimeout(NAVIGATION_TIMEOUT.toMillis());
        this.page = register(context.newPage());
    }

    public static PlaywrightUiDriver launch(UiOptions options) {
        Map<String, String> env = new HashMap<>();
        if (options.playwrightBrowsersPath() != null) {
            env.put("PLAYWRIGHT_BROWSERS_PATH", options.playwrightBrowsersPath().toAbsolutePath().toString());
        }
        if (options.browser() == BrowserType.CHROME) {
            env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"); // the installed Chrome is used
        }
        Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
        try {
            com.microsoft.playwright.BrowserType.LaunchOptions launch =
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(options.headless());
            Browser browser = switch (options.browser()) {
                case CHROME -> playwright.chromium().launch(launch.setChannel("chrome"));
                case FIREFOX -> playwright.firefox().launch(launch);
            };
            return new PlaywrightUiDriver(playwright, browser, options.timeout());
        } catch (RuntimeException e) {
            playwright.close();
            throw e;
        }
    }

    /** Our {@code strategy=value} locator as a Playwright selector. */
    static String selector(Locator locator) {
        return switch (locator.strategy()) {
            case ID -> "css=[id=\"" + escape(locator.value()) + "\"]";
            case NAME -> "css=[name=\"" + escape(locator.value()) + "\"]";
            case CSS -> "css=" + locator.value();
            case XPATH -> "xpath=" + locator.value();
            case LINK_TEXT -> "css=a:text-is(\"" + escape(locator.value()) + "\")";
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void open(String url) {
        page.navigate(url);
    }

    @Override
    public void click(String locator) {
        first(locator).click();
    }

    @Override
    public void type(String locator, String text) {
        first(locator).fill(text); // fill() clears the field first
    }

    @Override
    public String text(String locator) {
        return visible(locator).innerText().trim();
    }

    @Override
    public boolean isVisible(String locator) {
        try {
            return all(locator).all().stream().anyMatch(com.microsoft.playwright.Locator::isVisible);
        } catch (PlaywrightException e) {
            return false;
        }
    }

    @Override
    public void selectByVisibleText(String locator, String optionText) {
        waitForOptions(locator);
        first(locator).selectOption(new SelectOption().setLabel(optionText));
    }

    @Override
    public String selectedOption(String locator) {
        Object text = visible(locator)
                .evaluate("s => s.selectedIndex < 0 ? '' : s.options[s.selectedIndex].text");
        return String.valueOf(text).trim();
    }

    @Override
    public List<String> options(String locator) {
        waitForOptions(locator);
        return first(locator).locator("option").allTextContents().stream().map(String::trim).toList();
    }

    @Override
    public void waitForText(String locator, String expected) {
        until(timeout, () -> all(locator).count() > 0 && first(locator).innerText().contains(expected) ? true : null,
                "text '" + expected + "' in " + locator);
    }

    @Override
    public String title() {
        return page.title();
    }

    @Override
    public String currentUrl() {
        return page.url();
    }

    @Override
    public byte[] screenshot() {
        return page.screenshot();
    }

    @Override
    public String value(String locator) {
        return visible(locator).inputValue();
    }

    @Override
    public String attribute(String locator, String name) {
        return visible(locator).getAttribute(name);
    }

    @Override
    public void waitVisible(String locator) {
        visible(locator);
    }

    @Override
    public Optional<String> waitForAnyVisible(Duration waitFor, String... locators) {
        try {
            return Optional.of(until(waitFor, () -> {
                for (String locator : locators) {
                    if (isVisible(locator)) {
                        return locator;
                    }
                }
                return null;
            }, "any of " + String.join(", ", locators)));
        } catch (TimeoutError e) {
            return Optional.empty();
        }
    }

    /**
     * Same idea as Selenium 4's {@code RelativeLocator.with(tagName("input")).below(anchor)}: visible inputs whose top
     * edge is at or below the anchor's bottom edge, nearest one by distance between centres.
     */
    @Override
    public void typeBelow(String anchorLocator, String text) {
        BoundingBox anchor = visible(anchorLocator).boundingBox();
        com.microsoft.playwright.Locator input = until(timeout, () -> page.locator("input").all().stream()
                .filter(com.microsoft.playwright.Locator::isVisible)
                .map(candidate -> Map.entry(candidate, candidate.boundingBox()))
                .filter(entry -> entry.getValue() != null && entry.getValue().y >= anchor.y + anchor.height - 1)
                .min(Comparator.comparingDouble(entry -> distance(anchor, entry.getValue())))
                .map(Map.Entry::getKey)
                .orElse(null), "an input below " + anchorLocator);
        input.fill(text);
    }

    private static double distance(BoundingBox a, BoundingBox b) {
        double dx = (a.x + a.width / 2) - (b.x + b.width / 2);
        double dy = (a.y + a.height / 2) - (b.y + b.height / 2);
        return Math.hypot(dx, dy);
    }

    @Override
    public String openInNewTab(String url) {
        page = register(context.newPage());
        page.navigate(url);
        return currentWindow();
    }

    @Override
    public String currentWindow() {
        return pages.entrySet().stream()
                .filter(entry -> entry.getValue() == page)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The current page was closed; switch to another one"));
    }

    @Override
    public Set<String> windowHandles() {
        pages.values().removeIf(Page::isClosed);
        return new LinkedHashSet<>(pages.keySet());
    }

    @Override
    public void switchToWindow(String handle) {
        Page target = pages.get(handle);
        if (target == null || target.isClosed()) {
            throw new IllegalArgumentException("No open page with handle " + handle + ". Open: " + windowHandles());
        }
        target.bringToFront();
        page = target;
    }

    @Override
    public void closeCurrentWindow() {
        page.close();
        pages.values().removeIf(Page::isClosed);
    }

    @Override
    public void hover(String locator) {
        first(locator).hover();
    }

    @Override
    public void scrollTo(String locator) {
        first(locator).scrollIntoViewIfNeeded();
    }

    @Override
    public List<String> texts(String locator) {
        until(timeout, () -> all(locator).count() > 0 ? true : null, "at least one " + locator);
        return all(locator).allInnerTexts().stream().map(String::trim).toList();
    }

    @Override
    public String waitForNonBlankText(String locator) {
        return until(timeout, () -> {
            if (all(locator).count() == 0) {
                return null;
            }
            String text = first(locator).innerText().trim();
            return text.isEmpty() ? null : text;
        }, "non-blank text in " + locator);
    }

    /** Closes context, browser and the Playwright driver process, in that order, even if one of them fails. */
    @Override
    public void close() {
        try {
            context.close();
        } finally {
            try {
                browser.close();
            } finally {
                playwright.close();
            }
        }
    }

    /** Runs JavaScript in the current page. Package-private: for tests only, page objects never run scripts. */
    Object evaluate(String expression) {
        return page.evaluate(expression);
    }

    private Page register(Page newPage) {
        pages.put("page-" + (++pageCounter), newPage);
        return newPage;
    }

    private com.microsoft.playwright.Locator all(String locator) {
        return page.locator(selector(Locator.parse(locator)));
    }

    private com.microsoft.playwright.Locator first(String locator) {
        return all(locator).first();
    }

    private com.microsoft.playwright.Locator visible(String locator) {
        com.microsoft.playwright.Locator element = first(locator);
        element.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return element;
    }

    private void waitForOptions(String locator) {
        until(timeout, () -> all(locator).count() > 0 && first(locator).locator("option").count() > 0 ? true : null,
                "options in " + locator);
    }

    /**
     * Polls until {@code attempt} returns non-null (like Selenium's {@code WebDriverWait.until}). Playwright errors
     * during an attempt (element detached, page navigating) count as "not yet". Sleeps through
     * {@code page.waitForTimeout} so Playwright keeps processing browser events.
     */
    private <T> T until(Duration limit, Supplier<T> attempt, String description) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (true) {
            T result;
            try {
                result = attempt.get();
            } catch (PlaywrightException e) {
                result = null;
            }
            if (result != null) {
                return result;
            }
            if (System.nanoTime() > deadline) {
                throw new TimeoutError("Timed out after " + limit.toSeconds() + "s waiting for " + description);
            }
            page.waitForTimeout(POLL.toMillis());
        }
    }
}
