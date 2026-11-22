package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.time.Duration;
import java.util.Optional;

/**
 * Fails fast with a clear message when a ParaBank page does not load, in particular when Cloudflare in front of the
 * public demo site rate-limits this machine (plain-text page "error code: 1015", HTTP 429).
 */
final class SiteGuard {

    static final String RATE_LIMITED = "xpath=//body[contains(normalize-space(.),'error code: 1015')]";

    private SiteGuard() {
    }

    static void awaitPage(UiDriver ui, Duration timeout, String expectedLocator, String pageName) {
        Optional<String> found = ui.waitForAnyVisible(timeout, expectedLocator, RATE_LIMITED);
        if (found.isEmpty()) {
            throw new IllegalStateException(pageName + " did not load within " + timeout.toSeconds() + " s");
        }
        if (found.get().equals(RATE_LIMITED)) {
            throw new IllegalStateException("ParaBank is rate-limiting this machine (Cloudflare error 1015 / HTTP 429). "
                    + "Wait a few minutes, then run again with fewer threads (-Dthreads=2).");
        }
    }
}
