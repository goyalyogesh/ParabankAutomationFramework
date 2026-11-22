package io.github.thegoyalyogesh.qa.core.ui;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link UiDriver} contract as tests: every engine must pass the same checks on the same page. Subclasses only
 * start and stop their engine, which proves page objects can switch engines without changes.
 */
@Test(groups = "browser")
public abstract class UiDriverContractTest {

    private static final String PAGE = """
            <html><head><title>Ui Test</title></head><body>
            <input id="name" value="old">
            <button id="btn" onclick="document.getElementById('out').textContent='Hello ' + document.getElementById('name').value">Go</button>
            <div id="out"></div>
            <select id="sel"></select>
            <div id="hidden" style="display:none">secret</div>
            <div id="late"></div>
            <div><label>First</label> <input id="first"></div>
            <div><label>Last</label> <input id="last"></div>
            <a id="lnk" href="https://example.invalid/x">external</a>
            <div id="hoverTarget" onmouseover="this.textContent='hovered'">hover me</div>
            <ul><li class="item">One</li><li class="item"> Two </li></ul>
            <div id="lateText"></div>
            <div style="height:3000px"></div>
            <div id="far">far away</div>
            <script>
              setTimeout(function () {
                var sel = document.getElementById('sel');
                ['Alpha', 'Beta'].forEach(function (t) { var o = document.createElement('option'); o.text = t; sel.add(o); });
                document.getElementById('late').textContent = 'ready';
                document.getElementById('lateText').textContent = 'filled';
              }, 700);
            </script>
            </body></html>
            """;

    private UiDriver ui;
    private String pageUrl;
    private String secondUrl;

    @BeforeClass(alwaysRun = true)
    public void startBrowser() throws IOException {
        Path page = Files.createTempFile("ui-test", ".html");
        Files.writeString(page, PAGE);
        pageUrl = page.toUri().toString();
        Path second = Files.createTempFile("ui-second", ".html");
        Files.writeString(second, "<html><head><title>Second</title></head><body>2</body></html>");
        secondUrl = second.toUri().toString();
        ui = start(Duration.ofSeconds(5));
    }

    /** Starts a headless Chrome with this engine. */
    protected abstract UiDriver start(Duration timeout);

    protected abstract void stop();

    /** {@code window.scrollY} through the engine's own JavaScript API (not part of UiDriver). */
    protected abstract long scrollY();

    @BeforeMethod(alwaysRun = true)
    public void openPage() {
        ui.open(pageUrl);
    }

    @Test
    public void typeClearsFieldThenClickUpdatesText() {
        ui.type("id=name", "Yogesh");
        ui.click("css=#btn");
        assertThat(ui.text("id=out")).isEqualTo("Hello Yogesh");
    }

    @Test
    public void optionsWaitForJavascriptFilledSelect() {
        assertThat(ui.options("id=sel")).containsExactly("Alpha", "Beta");
        ui.selectByVisibleText("id=sel", "Beta");
        assertThat(ui.selectedOption("id=sel")).isEqualTo("Beta");
    }

    @Test
    public void visibilityChecksNeverThrow() {
        assertThat(ui.isVisible("id=btn")).isTrue();
        assertThat(ui.isVisible("id=hidden")).isFalse();
        assertThat(ui.isVisible("id=missing")).isFalse();
    }

    @Test
    public void waitForTextAndPageInfo() {
        ui.waitForText("id=late", "ready");
        assertThat(ui.title()).isEqualTo("Ui Test");
        assertThat(ui.currentUrl()).startsWith("file:");
    }

    @Test
    public void screenshotIsPng() {
        byte[] png = ui.screenshot();
        assertThat(png).hasSizeGreaterThan(100);
        assertThat(png[1]).isEqualTo((byte) 'P');
    }

    @Test
    public void typeBelowFindsTheNearestInputUnderTheAnchor() {
        ui.type("id=first", "Ann");
        ui.typeBelow("id=first", "Smith");
        assertThat(ui.value("id=first")).isEqualTo("Ann");
        assertThat(ui.value("id=last")).isEqualTo("Smith");
    }

    @Test
    public void attributeReturnsRawHtmlValue() {
        assertThat(ui.attribute("id=lnk", "href")).isEqualTo("https://example.invalid/x");
    }

    @Test
    public void waitForAnyVisibleReturnsFirstVisibleLocatorOrEmpty() {
        assertThat(ui.waitForAnyVisible(Duration.ofSeconds(2), "id=hidden", "id=btn")).contains("id=btn");
        assertThat(ui.waitForAnyVisible(Duration.ofSeconds(1), "id=missing")).isEmpty();
    }

    @Test
    public void newTabLifecycleWithWindowHandleSet() {
        String parent = ui.currentWindow();
        String child = ui.openInNewTab(secondUrl);

        assertThat(ui.title()).isEqualTo("Second");
        Set<String> handles = ui.windowHandles();
        assertThat(handles).hasSize(2).contains(parent, child);

        ui.closeCurrentWindow();
        ui.switchToWindow(parent);
        assertThat(ui.windowHandles()).containsExactly(parent);
        assertThat(ui.title()).isEqualTo("Ui Test");
    }

    @Test
    public void hoverFiresMouseOver() {
        ui.hover("id=hoverTarget");
        assertThat(ui.text("id=hoverTarget")).isEqualTo("hovered");
    }

    @Test
    public void scrollToMovesThePage() {
        ui.scrollTo("id=far");
        assertThat(scrollY()).isPositive();
    }

    @Test
    public void textsReturnsAllMatchesTrimmed() {
        assertThat(ui.texts("css=li.item")).containsExactly("One", "Two");
    }

    @Test
    public void waitForNonBlankTextWaitsForJavascript() {
        assertThat(ui.waitForNonBlankText("id=lateText")).isEqualTo("filled");
    }

    @AfterClass(alwaysRun = true)
    public void quitBrowser() {
        stop();
    }
}
