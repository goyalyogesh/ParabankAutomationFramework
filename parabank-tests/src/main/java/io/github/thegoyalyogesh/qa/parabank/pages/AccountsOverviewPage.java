package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

public final class AccountsOverviewPage {

    static final String TITLE = "xpath=//h1[normalize-space()='Accounts Overview']";
    static final String WELCOME = "css=#leftPanel p.smallText";
    static final String LOG_OUT = "link=Log Out";

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
}
