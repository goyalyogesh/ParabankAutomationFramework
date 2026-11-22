package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.CucumberOptions;
import io.github.thegoyalyogesh.qa.core.retry.RetryAwareCucumberTests;

/**
 * REST-only scenarios ({@code @api}). No browser. They run one at a time (the default, non-parallel data provider):
 * each takes a second or two, and running them in sequence lets them share customers safely.
 */
@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@api",
        plugin = {
                "summary",
                "html:target/cucumber/api.html",
                "json:target/cucumber/api.json",
                "rerun:target/rerun/api.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class ApiRunner extends RetryAwareCucumberTests {
}
