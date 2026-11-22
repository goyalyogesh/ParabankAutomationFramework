package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenRunExecutorTest {

    @Test
    public void commandSelectsBrowserTitleScenariosAndRunSpecificArguments() {
        MavenRunExecutor executor = new MavenRunExecutor(Path.of("module"), Path.of("reports"), "mvn",
                List.of("-Dthreads=2"), run -> run == 1 ? List.of("-Dinject.failure=U01") : List.of());

        assertThat(executor.command(1, new BrowserChoice("chrome", false), List.of())).containsExactly(
                "mvn", "-B", "test", "-Dbrowser=chrome", "-Dheadless=false", "-Dreport.title=Run 1 - chrome",
                "-Dthreads=2", "-Dinject.failure=U01");

        List<ScenarioResult> retry = List.of(
                new ScenarioResult("f.feature:3", "F", "U01 logs in (fast)", Set.of(), false),
                new ScenarioResult("g.feature:9", "G", "Pay $5", Set.of(), false));
        assertThat(executor.command(2, new BrowserChoice("firefox", true), retry))
                .containsExactly("mvn", "-B", "test", "-Dbrowser=firefox", "-Dheadless=true",
                        "-Dreport.title=Run 2 - firefox (headless)",
                        "-Dretry.scenarios=f.feature:3,g.feature:9",
                        "-Dthreads=2");
    }
}
