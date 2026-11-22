package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.util.List;

public final class AccountsOverviewPage {

    static final String TITLE = "xpath=//h1[normalize-space()='Accounts Overview']";
    static final String WELCOME = "css=#leftPanel p.smallText";
    static final String LOG_OUT = "link=Log Out";
    static final String ACCOUNT_LINKS = "css=#accountTable tbody a";

    private final UiDriver ui;

    public AccountsOverviewPage(UiDriver ui) {
        this.ui = ui;
    }

    public AccountsOverviewPage waitUntilLoaded() {
        ui.waitVisible(TITLE);
        return this;
    }

    /** e.g. "Welcome Aarav Sharma". */
    public String welcomeText() {
        return ui.text(WELCOME);
    }

    public void logOut() {
        ui.click(LOG_OUT);
    }

    /** Account numbers in the overview table (filled by JavaScript). */
    public List<Long> accountIds() {
        return ui.texts(ACCOUNT_LINKS).stream().map(Long::parseLong).toList();
    }

    /** Scrolls to the account's link (Actions API) and opens its activity page. */
    public void openActivity(long accountId) {
        String link = "link=" + accountId;
        ui.scrollTo(link);
        ui.click(link);
    }
}
