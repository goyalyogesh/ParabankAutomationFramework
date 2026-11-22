package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.CucumberOptions;
import io.github.thegoyalyogesh.qa.core.retry.RetryAwareCucumberTests;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@login or @selenium4",
        plugin = {
                "summary",
                "html:target/cucumber/login.html",
                "json:target/cucumber/login.json",
                "rerun:target/rerun/login.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class LoginRunner extends RetryAwareCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
