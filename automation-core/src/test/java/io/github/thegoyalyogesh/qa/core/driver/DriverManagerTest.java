package io.github.thegoyalyogesh.qa.core.driver;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DriverManagerTest {

    @Test
    public void browserTypeParsesCaseInsensitively() {
        assertThat(BrowserType.from(" Chrome ")).isEqualTo(BrowserType.CHROME);
        assertThat(BrowserType.from("FIREFOX")).isEqualTo(BrowserType.FIREFOX);
    }

    @Test
    public void unsupportedBrowserListsSupportedOnes() {
        assertThatThrownBy(() -> BrowserType.from("ie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ie")
                .hasMessageContaining("CHROME");
    }

    @Test
    public void getWithoutStartExplainsWhatToDo() {
        assertThat(DriverManager.isStarted()).isFalse();
        assertThatThrownBy(DriverManager::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DriverManager.start");
    }

    @Test
    public void quitWithoutStartIsSafe() {
        DriverManager.quit();
        assertThat(DriverManager.isStarted()).isFalse();
    }
}
