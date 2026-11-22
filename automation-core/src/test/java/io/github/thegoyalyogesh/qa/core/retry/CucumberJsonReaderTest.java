package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CucumberJsonReaderTest {

    private static final String LOGIN_JSON = """
            [{"uri": "classpath:features/login.feature", "name": "Login", "elements": [
              {"type": "background", "line": 4, "name": "", "steps": [{"result": {"status": "passed"}}]},
              {"type": "scenario", "line": 21, "name": "U01 logs in", "tags": [{"name": "@login"}],
               "before": [{"result": {"status": "passed"}}],
               "steps": [{"result": {"status": "passed"}}, {"result": {"status": "passed"}}],
               "after": [{"result": {"status": "passed"}}]},
              {"type": "scenario", "line": 35, "name": "N01 rejected", "tags": [{"name": "@login"}, {"name": "@knownDefect"}],
               "steps": [{"result": {"status": "passed"}}, {"result": {"status": "failed"}}, {"result": {"status": "skipped"}}]},
              {"type": "scenario", "line": 40, "name": "Injected", "tags": [],
               "before": [{"result": {"status": "failed"}}],
               "steps": [{"result": {"status": "skipped"}}]}
            ]}]
            """;

    @Test
    public void readsScenariosWithIdsTagsAndStatus() throws IOException {
        Path dir = Files.createTempDirectory("cucumber");
        Files.writeString(dir.resolve("login.json"), LOGIN_JSON);
        Files.writeString(dir.resolve("empty.json"), "");
        Files.writeString(dir.resolve("login.html"), "<html>not json</html>");

        List<ScenarioResult> results = CucumberJsonReader.readDirectory(dir);

        assertThat(results).extracting(ScenarioResult::id).containsExactly(
                "classpath:features/login.feature:21",
                "classpath:features/login.feature:35",
                "classpath:features/login.feature:40");
        assertThat(results).extracting(ScenarioResult::passed).containsExactly(true, false, false);
        assertThat(results.get(1).tags()).containsExactly("@login", "@knownDefect");
        assertThat(results.get(0).feature()).isEqualTo("Login");
        assertThat(results.get(0).name()).isEqualTo("U01 logs in");
    }

    @Test
    public void missingDirectoryMeansNoResults() {
        assertThat(CucumberJsonReader.readDirectory(Path.of("does-not-exist-" + System.nanoTime()))).isEmpty();
    }
}
