package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@registration",
        plugin = {
                "summary",
                "html:target/cucumber/registration.html",
                "json:target/cucumber/registration.json",
                "rerun:target/rerun/registration.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class RegistrationRunner extends AbstractTestNGCucumberTests {

    /** parallel = true: each scenario (one customer) runs on its own TestNG data-provider thread. */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
