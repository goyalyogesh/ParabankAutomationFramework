package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;
import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "browser")
public class SeleniumUiDriverBrowserTest {

    private static final String PAGE = """
            <html><head><title>Ui Test</title></head><body>
            <input id="name" value="old">
            <button id="btn" onclick="document.getElementById('out').textContent='Hello ' + document.getElementById('name').value">Go</button>
            <div id="out"></div>
            <select id="sel"></select>
            <div id="hidden" style="display:none">secret</div>
            <div id="late"></div>
            <script>
              setTimeout(function () {
                var sel = document.getElementById('sel');
                ['Alpha', 'Beta'].forEach(function (t) { var o = document.createElement('option'); o.text = t; sel.add(o); });
                document.getElementById('late').textContent = 'ready';
              }, 700);
            </script>
            </body></html>
            """;

    private UiDriver ui;
    private String pageUrl;

    @BeforeClass(alwaysRun = true)
    public void startBrowser() throws IOException {
        Path page = Files.createTempFile("ui-test", ".html");
        Files.writeString(page, PAGE);
        pageUrl = page.toUri().toString();
        DriverManager.start(BrowserType.CHROME, true);
        ui = new SeleniumUiDriver(DriverManager.get(), Duration.ofSeconds(5));
    }

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

    @AfterClass(alwaysRun = true)
    public void quitBrowser() {
        DriverManager.quit();
    }
}
