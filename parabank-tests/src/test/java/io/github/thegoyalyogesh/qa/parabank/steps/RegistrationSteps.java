package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.core.data.TestDataGenerator;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.service.RegistrationService;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistrationSteps {

    static final String SUCCESS_MESSAGE = "Your account was created successfully. You are now logged in.";

    private final TestContext ctx;
    private RegistrationResult result;

    public RegistrationSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("customer {string} from the Users sheet")
    public void customerFromUsersSheet(String userId) {
        ctx.setCurrentUser(ctx.repository().user(userId));
    }

    @When("the customer is registered on ParaBank")
    public void registerCustomer() {
        RegistrationService service = new RegistrationService(ctx.registerPage(), ctx.api(),
                new TestDataGenerator(), ctx.config().usernamePrefix());
        result = service.register(ctx.currentUser());
        ctx.setCurrentUser(result.user());
        // Write back immediately, so generated credentials are never lost even if a later check fails.
        ctx.repository().recordRegistration(result, ctx.config().runLabel());
        ctx.markResultRecorded();
        UserRegistry.register(result.user());
    }

    @Then("the registration is acknowledged")
    public void registrationIsAcknowledged() {
        assertThat(result.status()).isIn(RegistrationStatus.REGISTERED, RegistrationStatus.EXISTING);
        if (result.status() == RegistrationStatus.REGISTERED) {
            assertThat(result.acknowledgement()).isEqualTo(SUCCESS_MESSAGE);
        }
    }

    @Then("the customer exists in the ParaBank backend")
    public void customerExistsInBackend() {
        ParaBankUser user = ctx.currentUser();
        assertThat(ctx.api().login(user.username(), user.password()))
                .as("REST login for %s", user.username())
                .contains(Long.parseLong(user.customerId()));
        assertThat(user.accountNumber()).as("first account number").isNotBlank();
    }

    @Then("the registration result is written back to Excel")
    public void resultWrittenBackToExcel() {
        ParaBankUser saved = ctx.repository().user(ctx.currentUser().userId());
        assertThat(saved.username()).isEqualTo(ctx.currentUser().username());
        assertThat(saved.password()).isEqualTo(ctx.currentUser().password());
        assertThat(saved.status()).isEqualTo(result.status().name());
        assertThat(saved.customerId()).isEqualTo(ctx.currentUser().customerId());
        assertThat(saved.accountNumber()).isEqualTo(ctx.currentUser().accountNumber());
    }
}
