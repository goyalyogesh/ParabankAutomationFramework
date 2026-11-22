package io.github.thegoyalyogesh.qa.core.report;

import io.cucumber.core.cli.Main;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtentCucumberPluginTest {

    @Test
    public void writesSparkReportWithStepsScreenshotsAndKnownDefects() throws IOException {
        Path report = Files.createTempDirectory("extent").resolve("spark.html");

        byte exitStatus = Main.run(new String[]{
                "--plugin", ExtentCucumberPlugin.class.getName() + ":" + report,
                "--glue", "io.github.thegoyalyogesh.qa.core.report.fixture",
                "--monochrome",
                "classpath:report-fixture/demo.feature"
        }, Thread.currentThread().getContextClassLoader());

        assertThat(exitStatus).as("one scenario fails on purpose").isEqualTo((byte) 1);
        String html = Files.readString(report);
        assertThat(html)
                .contains("Passing scenario")
                .contains("Failing scenario")
                .contains("a step that passes")
                .contains("boom: expected failure")
                .contains("Known defect")
                .contains("browser: chrome")
                .contains("data:image/png;base64,");
    }
}
