package io.github.thegoyalyogesh.qa.core.driver;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "browser")
public class DriverSmokeTest {

    @DataProvider
    public Object[][] browsers() {
        return new Object[][]{{BrowserType.CHROME}, {BrowserType.FIREFOX}};
    }

    @Test(dataProvider = "browsers")
    public void startsHeadlessBrowserAndOpensPage(BrowserType type) throws IOException {
        Path page = Files.createTempFile("smoke", ".html");
        Files.writeString(page, "<html><head><title>smoke-ok</title></head><body>hi</body></html>");

        DriverManager.start(type, true);
        DriverManager.get().get(page.toUri().toString());

        assertThat(DriverManager.get().getTitle()).isEqualTo("smoke-ok");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        DriverManager.quit();
    }
}
