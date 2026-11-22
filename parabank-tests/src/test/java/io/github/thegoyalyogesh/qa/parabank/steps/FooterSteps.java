package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FooterSteps {

    private final TestContext ctx;
    private String childWindow;

    public FooterSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("the ParaBank home page is open")
    public void homePageOpen() {
        ctx.loginPage().open();
        ctx.setParentWindow(ctx.ui().currentWindow());
    }

    @When("the {string} footer link is opened in a new tab")
    public void openFooterLinkInNewTab(String linkText) {
        String url = ctx.footer().linkUrl(linkText);
        childWindow = ctx.ui().openInNewTab(url);
    }

    @Then("the new tab shows a page titled with {string}")
    public void newTabTitle(String expected) {
        assertThat(ctx.ui().currentWindow()).isEqualTo(childWindow);
        assertThat(ctx.ui().title()).containsIgnoringCase(expected);
    }

    @Then("{int} distinct browser tabs are open")
    public void distinctTabs(int count) {
        Set<String> handles = ctx.ui().windowHandles();
        assertThat(handles).hasSize(count).contains(ctx.parentWindow(), childWindow);
    }

    @When("the new tab is closed")
    public void closeNewTab() {
        ctx.ui().closeCurrentWindow();
        ctx.ui().switchToWindow(ctx.parentWindow());
    }

    @Then("the ParaBank page is active again")
    public void paraBankActiveAgain() {
        assertThat(ctx.ui().windowHandles()).containsExactly(ctx.parentWindow());
        assertThat(ctx.ui().title()).contains("ParaBank");
    }
}
