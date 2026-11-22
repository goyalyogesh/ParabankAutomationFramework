package io.github.thegoyalyogesh.qa.core.retry;

/** Numbers of one run, plus the cumulative pass rate after it. */
public record RunSummary(int run, BrowserChoice browser, int executed, int passed, int failed,
                         double cumulativePassRate) {
}
