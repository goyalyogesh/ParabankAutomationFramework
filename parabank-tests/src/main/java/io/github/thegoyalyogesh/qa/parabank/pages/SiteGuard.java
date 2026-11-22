package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Waits for a ParaBank page and survives Cloudflare's rate limit on the shared public site.
 * <p>
 * When this machine sends too many requests, Cloudflare answers every URL with "Error 1015 - You are being rate
 * limited" (browser) or "error code: 1015" (plain text) for about two minutes. Instead of failing, the guard waits
 * 30 s, 60 s, 90 s (reloading the same URL after each wait) and only then gives up with a clear message.
 */
final class SiteGuard {

    static final String RATE_LIMITED = "xpath=//body[contains(normalize-space(.),'1015')"
            + " and (contains(.,'rate limited') or contains(.,'error code'))]";

    private static final Logger LOG = LogManager.getLogger(SiteGuard.class);
    private static final int MAX_WAITS = 3;
    private static final Duration FIRST_WAIT = Duration.ofSeconds(30);

    private SiteGuard() {
    }

    /** Waits until {@code expectedLocator} is visible; waits out a rate limit; fails if the page never appears. */
    static void awaitPage(UiDriver ui, Duration timeout, String expectedLocator, String pageName) {
        for (int attempt = 1; ; attempt++) {
            Optional<String> found = ui.waitForAnyVisible(timeout, expectedLocator, RATE_LIMITED);
            if (found.isEmpty()) {
                throw new IllegalStateException(pageName + " did not load within " + timeout.toSeconds() + " s");
            }
            if (found.get().equals(expectedLocator)) {
                return;
            }
            if (attempt > MAX_WAITS) {
                throw new IllegalStateException("ParaBank is still rate-limiting this machine (Cloudflare error 1015) after "
                        + MAX_WAITS + " waits. Try again later with fewer threads (-Dthreads=2).");
            }
            Duration wait = FIRST_WAIT.multipliedBy(attempt);
            LOG.warn("Rate limited by Cloudflare on {} - waiting {} s, then reloading (wait {}/{})",
                    pageName, wait.toSeconds(), attempt, MAX_WAITS);
            pause(wait);
            ui.open(ui.currentUrl());
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the rate limit to pass", e);
        }
    }
}
