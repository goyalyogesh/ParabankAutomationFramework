package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.time.Duration;

/** The login form on the ParaBank home page (left panel). */
public final class LoginPage {

    public static final String USERNAME = "name=username";
    static final String PASSWORD = "name=password";
    static final String LOGIN_BUTTON = "css=input[value='Log In']";
    static final String LOG_OUT = "link=Log Out";
    static final String ERROR = "css=#rightPanel p.error";

    /** What the site did after pressing Log In. */
    public enum Outcome { LOGGED_IN, ERROR_SHOWN, NO_PARABANK_RESPONSE }

    private final UiDriver ui;
    private final String baseUrl;

    public LoginPage(UiDriver ui, String baseUrl) {
        this.ui = ui;
        this.baseUrl = baseUrl;
    }

    public LoginPage open() {
        ui.open(baseUrl + "/index.htm");
        ui.waitVisible(USERNAME);
        return this;
    }

    public Outcome login(String username, String password) {
        ui.type(USERNAME, username);
        ui.type(PASSWORD, password);
        ui.click(LOGIN_BUTTON);
        return ui.waitForAnyVisible(Duration.ofSeconds(15), LOG_OUT, ERROR)
                .map(found -> found.equals(LOG_OUT) ? Outcome.LOGGED_IN : Outcome.ERROR_SHOWN)
                .orElse(Outcome.NO_PARABANK_RESPONSE); // e.g. Cloudflare blocks SQL-like input with a 403 page
    }

    public boolean isLoggedIn() {
        return ui.isVisible(LOG_OUT);
    }

    public String errorMessage() {
        return ui.isVisible(ERROR) ? ui.text(ERROR) : "";
    }
}
