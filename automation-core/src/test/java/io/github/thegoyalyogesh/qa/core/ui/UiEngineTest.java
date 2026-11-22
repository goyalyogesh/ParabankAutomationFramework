package io.github.thegoyalyogesh.qa.core.ui;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UiEngineTest {

    @Test
    public void parsesCaseInsensitiveAndTrimmed() {
        assertThat(UiEngine.from("Playwright")).isEqualTo(UiEngine.PLAYWRIGHT);
        assertThat(UiEngine.from(" selenium ")).isEqualTo(UiEngine.SELENIUM);
    }

    @Test
    public void unknownEngineListsSupportedValues() {
        assertThatThrownBy(() -> UiEngine.from("cypress"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cypress")
                .hasMessageContaining("SELENIUM")
                .hasMessageContaining("PLAYWRIGHT");
    }
}
