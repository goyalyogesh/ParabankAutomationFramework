package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UiSessionTest {

    @Test
    public void quitWithoutStartIsSafe() {
        UiSession.quit();
        assertThat(UiSession.isStarted()).isFalse();
    }

    @Test
    public void getWithoutStartExplainsWhatToDo() {
        assertThatThrownBy(UiSession::get).isInstanceOf(IllegalStateException.class).hasMessageContaining("start");
    }

    @Test(groups = "browser")
    public void startsPlaywrightPerThreadAndRefusesASecondStart() {
        UiOptions options = new UiOptions(UiEngine.PLAYWRIGHT, BrowserType.CHROME, true, Duration.ofSeconds(5), null);
        try {
            UiDriver ui = UiSession.start(options);
            assertThat(ui).isInstanceOf(PlaywrightUiDriver.class).isSameAs(UiSession.get());
            assertThat(UiSession.isStarted()).isTrue();
            assertThatThrownBy(() -> UiSession.start(options)).isInstanceOf(IllegalStateException.class);
        } finally {
            UiSession.quit();
        }
        assertThat(UiSession.isStarted()).isFalse();
    }
}
