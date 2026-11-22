package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.CucumberOptions;
import io.github.thegoyalyogesh.qa.core.retry.RetryAwareCucumberTests;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@transfers and not @api",
        plugin = {
                "summary",
                "html:target/cucumber/transfers.html",
                "json:target/cucumber/transfers.json",
                "rerun:target/rerun/transfers.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class TransferRunner extends RetryAwareCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
