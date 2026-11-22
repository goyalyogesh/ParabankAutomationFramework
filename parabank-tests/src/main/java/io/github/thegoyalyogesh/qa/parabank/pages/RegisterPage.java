package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;

import java.time.Duration;

/** "Signing up is easy!" registration form. */
public final class RegisterPage {

    static final String FIRST_NAME = "id=customer.firstName";
    static final String LAST_NAME = "id=customer.lastName";
    static final String STREET = "id=customer.address.street";
    static final String CITY = "id=customer.address.city";
    static final String STATE = "id=customer.address.state";
    static final String ZIP = "id=customer.address.zipCode";
    static final String PHONE = "id=customer.phoneNumber";
    static final String SSN = "id=customer.ssn";
    static final String USERNAME = "id=customer.username";
    static final String PASSWORD = "id=customer.password";
    static final String CONFIRM = "id=repeatedPassword";
    static final String REGISTER_BUTTON = "css=input[value='Register']";

    static final String WELCOME_TITLE = "xpath=//div[@id='rightPanel']//h1[starts-with(normalize-space(),'Welcome')]";
    static final String SUCCESS_MESSAGE = "xpath=//div[@id='rightPanel']//h1/following-sibling::p[1]";
    static final String USERNAME_ERROR = "id=customer.username.errors";
    static final String ANY_FIELD_ERROR = "css=#rightPanel span.error";

    public enum Outcome { SUCCESS, USERNAME_TAKEN, VALIDATION_ERROR, NO_RESPONSE }

    private final UiDriver ui;
    private final String baseUrl;
    private final Duration timeout;

    public RegisterPage(UiDriver ui, String baseUrl, Duration timeout) {
        this.ui = ui;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    public RegisterPage open() {
        ui.open(baseUrl + "/register.htm");
        SiteGuard.awaitPage(ui, timeout, FIRST_NAME, "Register page");
        return this;
    }

    public Outcome register(ParaBankUser user) {
        ui.type(FIRST_NAME, user.firstName());
        // Selenium 4 relative locator: "Last Name" is the input directly below "First Name".
        ui.typeBelow(FIRST_NAME, user.lastName());
        if (!user.lastName().equals(ui.value(LAST_NAME))) {
            throw new IllegalStateException("Relative locator did not reach the Last Name field");
        }
        ui.type(STREET, user.street());
        ui.type(CITY, user.city());
        ui.type(STATE, user.state());
        ui.type(ZIP, user.zipCode());
        ui.type(PHONE, user.phone());
        ui.type(SSN, user.ssn());
        ui.type(USERNAME, user.username());
        ui.type(PASSWORD, user.password());
        ui.type(CONFIRM, user.password());
        ui.click(REGISTER_BUTTON);

        return ui.waitForAnyVisible(timeout, WELCOME_TITLE, USERNAME_ERROR, ANY_FIELD_ERROR)
                .map(found -> switch (found) {
                    case WELCOME_TITLE -> Outcome.SUCCESS;
                    case USERNAME_ERROR -> Outcome.USERNAME_TAKEN;
                    default -> Outcome.VALIDATION_ERROR;
                })
                .orElse(Outcome.NO_RESPONSE);
    }

    public String welcomeTitle() {
        return ui.text(WELCOME_TITLE);
    }

    public String successMessage() {
        return ui.text(SUCCESS_MESSAGE);
    }

    public String firstFieldError() {
        return ui.isVisible(ANY_FIELD_ERROR) ? ui.text(ANY_FIELD_ERROR) : "";
    }
}
