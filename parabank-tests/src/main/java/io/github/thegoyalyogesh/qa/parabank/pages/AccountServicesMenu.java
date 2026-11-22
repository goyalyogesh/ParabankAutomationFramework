package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.time.Duration;

/**
 * "Account Services" links in the left panel. Hovers first (Actions API), then clicks, like a real user,
 * then waits for the target page's title (surviving a rate limit, see {@link SiteGuard}).
 */
public final class AccountServicesMenu {

    /** Link text and the title of the page it opens. */
    public enum Link {
        OPEN_NEW_ACCOUNT("Open New Account", "Open New Account"),
        ACCOUNTS_OVERVIEW("Accounts Overview", "Accounts Overview"),
        TRANSFER_FUNDS("Transfer Funds", "Transfer Funds"),
        BILL_PAY("Bill Pay", "Bill Payment Service");

        private final String text;
        private final String pageTitle;

        Link(String text, String pageTitle) {
            this.text = text;
            this.pageTitle = pageTitle;
        }
    }

    private final UiDriver ui;
    private final Duration timeout;

    public AccountServicesMenu(UiDriver ui, Duration timeout) {
        this.ui = ui;
        this.timeout = timeout;
    }

    public void go(Link link) {
        String locator = "xpath=//div[@id='leftPanel']//a[normalize-space()='" + link.text + "']";
        ui.hover(locator);
        ui.click(locator);
        SiteGuard.awaitPage(ui, timeout,
                "xpath=//div[@id='rightPanel']//h1[normalize-space()='" + link.pageTitle + "']", link.pageTitle);
    }
}
