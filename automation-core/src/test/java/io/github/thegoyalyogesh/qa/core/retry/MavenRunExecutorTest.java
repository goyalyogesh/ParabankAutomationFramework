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
                "mvn", "-B", "test", "-Dui.engine=selenium", "-Dbrowser=chrome", "-Dheadless=false", "-Dreport.title=Run 1 - chrome",
                "-Dthreads=2", "-Dinject.failure=U01");

        List<ScenarioResult> retry = List.of(
                new ScenarioResult("f.feature:3", "F", "U01 logs in (fast)", Set.of(), false),
                new ScenarioResult("g.feature:9", "G", "Pay $5", Set.of(), false));
        assertThat(executor.command(2, new BrowserChoice("firefox", true), retry))
                .containsExactly("mvn", "-B", "test", "-Dui.engine=selenium", "-Dbrowser=firefox", "-Dheadless=true",
                        "-Dreport.title=Run 2 - firefox (headless)",
                        "-Dretry.scenarios=f.feature:3,g.feature:9",
                        "-Dthreads=2");

        assertThat(executor.command(3, new BrowserChoice("playwright", "chrome", true), retry))
                .contains("-Dui.engine=playwright", "-Dbrowser=chrome", "-Dreport.title=Run 3 - playwright chrome (headless)");
    }

    @Test
    public void demoFailureIsInjectedIntoTheFirstNRunsOnly() {
        assertThat(RetryOrchestrator.demoArguments(null, 2).apply(1)).isEmpty();
        assertThat(RetryOrchestrator.demoArguments("U0[1-6]", 1).apply(1)).containsExactly("-Dinject.failure=U0[1-6]");
        assertThat(RetryOrchestrator.demoArguments("U0[1-6]", 1).apply(2)).isEmpty();
        assertThat(RetryOrchestrator.demoArguments("U0[1-6]", 2).apply(2)).containsExactly("-Dinject.failure=U0[1-6]");
        assertThat(RetryOrchestrator.demoArguments("U0[1-6]", 2).apply(3)).isEmpty();
    }
}
