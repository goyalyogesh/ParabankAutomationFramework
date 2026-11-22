package io.github.thegoyalyogesh.qa.core.retry;

/** Decides whether another run is worth it: below the threshold, runs left, and something to retry. */
public record ThresholdPolicy(double thresholdPercent, int maxRuns) {

    public ThresholdPolicy {
        if (thresholdPercent < 0 || thresholdPercent > 100) {
            throw new IllegalArgumentException("Threshold must be 0-100, was " + thresholdPercent);
        }
        if (maxRuns < 1) {
            throw new IllegalArgumentException("maxRuns must be at least 1, was " + maxRuns);
        }
    }

    public boolean anotherRun(RunHistory history, int runsDone) {
        return runsDone < maxRuns && !met(history) && !history.retryCandidates().isEmpty();
    }

    public boolean met(RunHistory history) {
        return history.passRate() >= thresholdPercent;
    }
}
