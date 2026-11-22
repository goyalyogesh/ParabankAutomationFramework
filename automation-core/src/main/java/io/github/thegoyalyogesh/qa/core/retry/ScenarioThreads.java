package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.ITestContext;

/**
 * How many scenarios run at the same time, taken from {@code -Dthreads} (default 3).
 * <p>
 * Maven passes the value to TestNG itself (Surefire's {@code dataproviderthreadcount}), but an IDE that starts TestNG
 * directly (Eclipse, IntelliJ) does not, and TestNG would then use its own default of 10 parallel browsers, enough to
 * get rate-limited by the public site. Setting it on the suite before the class's tests start makes every launcher
 * behave the same.
 */
public final class ScenarioThreads {

    static final int DEFAULT = 3;

    private ScenarioThreads() {
    }

    static int resolve(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            int threads = Integer.parseInt(value.trim());
            if (threads >= 1) {
                return threads;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error below
        }
        throw new IllegalArgumentException("-Dthreads must be a whole number of 1 or more, got: " + value);
    }

    /** Applies {@code -Dthreads} to the running suite's parallel data providers. */
    public static void apply(ITestContext context) {
        context.getSuite().getXmlSuite().setDataProviderThreadCount(resolve(System.getProperty("threads")));
    }
}
