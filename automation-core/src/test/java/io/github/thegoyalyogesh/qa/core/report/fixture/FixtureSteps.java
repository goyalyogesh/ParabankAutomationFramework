package io.github.thegoyalyogesh.qa.core.report.fixture;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Base64;

public class FixtureSteps {

    /** 1x1 transparent PNG. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Given("a step that passes")
    public void passes() {
    }

    @When("a step that fails")
    public void fails() {
        throw new AssertionError("boom: expected failure");
    }

    @Then("a step that is skipped")
    public void skipped() {
    }

    @After
    public void afterScenario(Scenario scenario) {
        scenario.log("browser: chrome");
        if (scenario.isFailed()) {
            scenario.attach(PNG, "image/png", "failure-shot");
        }
    }
}
