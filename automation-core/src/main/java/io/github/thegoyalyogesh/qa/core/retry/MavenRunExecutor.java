package io.github.thegoyalyogesh.qa.core.retry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Runs {@code mvn test} for a module in a separate process per run, then archives the run's outputs
 * ({@code target/cucumber, reports, screenshots, rerun}) to {@code reportsDir/run-N} and reads the results from there.
 * A fresh JVM per run means no state leaks between runs, exactly like separate Jenkins builds.
 */
public final class MavenRunExecutor implements RunExecutor {

    static final List<String> OUTPUT_DIRS = List.of("cucumber", "reports", "screenshots", "rerun");

    private final Path moduleDir;
    private final Path reportsDir;
    private final String mavenCommand;
    private final List<String> extraArguments;
    private final IntFunction<List<String>> runSpecificArguments;

    public MavenRunExecutor(Path moduleDir, Path reportsDir, String mavenCommand, List<String> extraArguments,
                            IntFunction<List<String>> runSpecificArguments) {
        this.moduleDir = moduleDir;
        this.reportsDir = reportsDir;
        this.mavenCommand = mavenCommand;
        this.extraArguments = List.copyOf(extraArguments);
        this.runSpecificArguments = runSpecificArguments;
    }

    /** Deletes the reports of a previous orchestration. */
    public void cleanReports() {
        deleteRecursively(reportsDir);
    }

    @Override
    public List<ScenarioResult> execute(int run, BrowserChoice browser, List<String> onlyScenarios) {
        Path target = moduleDir.resolve("target");
        OUTPUT_DIRS.forEach(dir -> deleteRecursively(target.resolve(dir)));
        Path runDir = reportsDir.resolve("run-" + run);
        Path log = runDir.resolve("maven.log");
        List<String> command = command(run, browser, onlyScenarios);
        try {
            Files.createDirectories(runDir);
            RetryOrchestrator.say("Starting: %s (log: %s)", String.join(" ", command), log);
            Process process = new ProcessBuilder(command)
                    .directory(moduleDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            int exitCode = process.waitFor();
            RetryOrchestrator.say("Run %d: Maven exit code %d (non-zero is expected when scenarios fail)", run, exitCode);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start Maven: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while run " + run + " was executing", e);
        }
        OUTPUT_DIRS.forEach(dir -> copyRecursively(target.resolve(dir), runDir.resolve(dir)));
        return CucumberJsonReader.readDirectory(runDir.resolve("cucumber"));
    }

    List<String> command(int run, BrowserChoice browser, List<String> onlyScenarios) {
        List<String> command = new ArrayList<>(List.of(mavenCommand, "-B", "test",
                "-Dbrowser=" + browser.browser(),
                "-Dheadless=" + browser.headless(),
                "-Dreport.title=Run " + run + " - " + browser.label()));
        if (!onlyScenarios.isEmpty()) {
            command.add("-Dcucumber.features=" + String.join(",", onlyScenarios));
        }
        command.addAll(extraArguments);
        command.addAll(runSpecificArguments.apply(run));
        return command;
    }

    private static void deleteRecursively(Path path) {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + path, e);
        }
    }

    private static void copyRecursively(Path source, Path destination) {
        if (Files.notExists(source)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path copy = destination.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(p, copy, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + source + " to " + destination, e);
        }
    }
}
