package io.github.thegoyalyogesh.qa.core.report;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EmbedEvent;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.WriteEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cucumber plugin that writes an Extent Spark report: one test per scenario, one log line per step,
 * Base64 screenshots from {@code scenario.attach}, text from {@code scenario.log}, and a "Known defect"
 * category for failed scenarios tagged {@value #KNOWN_DEFECT_TAG}.
 * <p>
 * Usage: {@code plugin = "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"}.
 * Several runners in one JVM that use the same path share one report.
 */
public final class ExtentCucumberPlugin implements ConcurrentEventListener {

    public static final String KNOWN_DEFECT_TAG = "@knownDefect";
    public static final String KNOWN_DEFECT_CATEGORY = "Known defect";

    private static final ConcurrentHashMap<Path, ExtentReports> REPORTS = new ConcurrentHashMap<>();

    private final ExtentReports extent;
    private final ConcurrentHashMap<UUID, ExtentTest> tests = new ConcurrentHashMap<>();

    public ExtentCucumberPlugin(String outputFile) {
        Path path = Path.of(outputFile).toAbsolutePath().normalize();
        this.extent = REPORTS.computeIfAbsent(path, ExtentCucumberPlugin::createReports);
    }

    private static ExtentReports createReports(Path file) {
        String title = System.getProperty("report.title", "Automation Report");
        ExtentSparkReporter spark = new ExtentSparkReporter(file.toString());
        spark.config().setOfflineMode(true);
        spark.config().setDocumentTitle(title);
        spark.config().setReportName(title);
        ExtentReports reports = new ExtentReports();
        reports.attachReporter(spark);
        reports.setSystemInfo("OS", System.getProperty("os.name"));
        reports.setSystemInfo("Java", System.getProperty("java.version"));
        return reports;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onTestStepFinished);
        publisher.registerHandlerFor(EmbedEvent.class, this::onEmbed);
        publisher.registerHandlerFor(WriteEvent.class, this::onWrite);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, event -> extent.flush());
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        ExtentTest test = extent.createTest(escape(testCase.getName()));
        testCase.getTags().stream().map(tag -> tag.substring(1)).forEach(test::assignCategory);
        tests.put(testCase.getId(), test);
    }

    private void onTestStepFinished(TestStepFinished event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test == null) {
            return;
        }
        Result result = event.getResult();
        if (event.getTestStep() instanceof PickleStepTestStep step) {
            String name = escape(step.getStep().getKeyword() + step.getStep().getText());
            switch (result.getStatus()) {
                case PASSED -> test.pass(name);
                case FAILED -> {
                    test.fail(name);
                    if (result.getError() != null) {
                        test.fail(result.getError());
                    }
                }
                case SKIPPED -> test.skip(name);
                default -> test.warning(name + " (" + result.getStatus() + ")");
            }
        } else if (result.getStatus() == Status.FAILED && result.getError() != null) {
            test.fail(result.getError()); // a failing @Before/@After hook
        }
    }

    private void onEmbed(EmbedEvent event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test == null) {
            return;
        }
        String name = event.getName() == null ? "attachment" : event.getName();
        if (event.getMediaType().startsWith("image/")) {
            String base64 = Base64.getEncoder().encodeToString(event.getData());
            test.info(escape(name), MediaEntityBuilder.createScreenCaptureFromBase64String(base64, name).build());
        } else {
            test.info(escape(name) + ": " + escape(new String(event.getData(), StandardCharsets.UTF_8)));
        }
    }

    private void onWrite(WriteEvent event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test != null) {
            test.info(escape(event.getText()));
        }
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        ExtentTest test = tests.remove(testCase.getId());
        if (test == null) {
            return;
        }
        if (event.getResult().getStatus() == Status.FAILED && testCase.getTags().contains(KNOWN_DEFECT_TAG)) {
            test.assignCategory(KNOWN_DEFECT_CATEGORY);
            test.info("Known defect: this failure is expected until the application bug is fixed.");
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
