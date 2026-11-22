package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final TestContext ctx;
    private Scenario scenario;

    public LoginSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void keepScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("registered customer {string} from the Users sheet")
    public void registeredCustomer(String userId) {
        ctx.setCurrentUser(ctx.registeredUser(userId));
    }

    @When("the customer logs in")
    public void customerLogsIn() {
        ParaBankUser user = ctx.currentUser();
        LoginPage.Outcome outcome = ctx.loginPage().open().login(user.username(), user.password());
        ctx.setLoginObservation(outcome.name());
    }

    @Then("the Accounts Overview page shows the customer's name")
    public void overviewShowsName() {
        String welcome = ctx.overviewPage().waitUntilLoaded().welcomeText();
        assertThat(welcome).isEqualTo("Welcome " + ctx.currentUser().fullName());
    }

    @Then("the customer can log out")
    public void customerCanLogOut() {
        ctx.overviewPage().logOut();
        ctx.ui().waitVisible(LoginPage.USERNAME);
        assertThat(ctx.loginPage().isLoggedIn()).isFalse();
    }

    @Given("login case {string} from the LoginNegative sheet")
    public void loginCase(String caseId) {
        LoginCase loginCase = ctx.repository().loginCase(caseId);
        ctx.setLoginCase(loginCase);
        scenario.log(caseId + ": " + loginCase.description());
    }

    @When("the user tries to log in with those credentials")
    public void tryToLogIn() {
        LoginCase loginCase = ctx.loginCase();
        LoginPage page = ctx.loginPage().open();
        LoginPage.Outcome outcome = page.login(loginCase.username(), loginCase.password());
        String message = page.errorMessage();
        ctx.setLoginObservation(outcome + (message.isBlank() ? "" : ": " + message));
        scenario.log("Observed: " + ctx.loginObservation());
    }

    @Then("the user is not logged in")
    public void userIsNotLoggedIn() {
        assertThat(ctx.loginPage().isLoggedIn())
                .as("Login must be rejected for %s (%s), but the Accounts Overview was shown",
                        ctx.loginCase().caseId(), ctx.loginCase().description())
                .isFalse();
    }

    @Then("the expected error message is shown")
    public void expectedErrorShown() {
        String expected = ctx.loginCase().expectedError();
        if (!expected.isBlank()) {
            assertThat(ctx.loginPage().errorMessage()).contains(expected);
        }
    }
}
