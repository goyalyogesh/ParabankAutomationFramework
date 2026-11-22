package io.github.thegoyalyogesh.qa.parabank.context;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import io.github.thegoyalyogesh.qa.parabank.config.ParaBankConfig;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;
import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.pages.AccountsOverviewPage;
import io.github.thegoyalyogesh.qa.parabank.pages.FooterComponent;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;
import io.github.thegoyalyogesh.qa.parabank.pages.RegisterPage;

/**
 * Per-scenario state shared between hooks and step classes. Cucumber's PicoContainer creates one instance
 * per scenario and injects it into every constructor that asks for it, so parallel scenarios never share state.
 */
public class TestContext {

    private static final ParaBankConfig CONFIG = ParaBankConfig.get();
    private static final TestDataRepository REPOSITORY =
            TestDataRepository.fromTemplate(CONFIG.testDataFile(), CONFIG.testDataTemplate());
    private static final ParaBankApi API = new ParaBankApi(CONFIG.apiUrl());

    private UiDriver ui;
    private ParaBankUser currentUser;
    private LoginCase loginCase;
    private String loginObservation = "";
    private boolean resultRecorded;
    private String parentWindow;

    public ParaBankConfig config() {
        return CONFIG;
    }

    public TestDataRepository repository() {
        return REPOSITORY;
    }

    public ParaBankApi api() {
        return API;
    }

    public UiDriver ui() {
        if (ui == null) {
            throw new IllegalStateException("Browser not started; the @Before hook should have done this");
        }
        return ui;
    }

    public boolean hasUi() {
        return ui != null;
    }

    public void setUi(UiDriver ui) {
        this.ui = ui;
    }

    public LoginPage loginPage() {
        return new LoginPage(ui(), CONFIG.appUrl(), CONFIG.timeout());
    }

    public RegisterPage registerPage() {
        return new RegisterPage(ui(), CONFIG.appUrl(), CONFIG.timeout());
    }

    public AccountsOverviewPage overviewPage() {
        return new AccountsOverviewPage(ui());
    }

    public FooterComponent footer() {
        return new FooterComponent(ui());
    }

    /** Customer from this run's registry, or from Excel when a suite runs on its own; must be registered. */
    public ParaBankUser registeredUser(String userId) {
        ParaBankUser user = UserRegistry.find(userId).orElseGet(() -> REPOSITORY.user(userId));
        if (!user.isRegistered()) {
            throw new IllegalStateException("Customer " + userId + " must be registered first (run the registration suite)");
        }
        return user;
    }

    public ParaBankUser currentUser() {
        return currentUser;
    }

    public void setCurrentUser(ParaBankUser currentUser) {
        this.currentUser = currentUser;
    }

    public LoginCase loginCase() {
        return loginCase;
    }

    public void setLoginCase(LoginCase loginCase) {
        this.loginCase = loginCase;
    }

    public String loginObservation() {
        return loginObservation;
    }

    public void setLoginObservation(String loginObservation) {
        this.loginObservation = loginObservation;
    }

    public boolean resultRecorded() {
        return resultRecorded;
    }

    public void markResultRecorded() {
        this.resultRecorded = true;
    }

    public String parentWindow() {
        return parentWindow;
    }

    public void setParentWindow(String parentWindow) {
        this.parentWindow = parentWindow;
    }
}
