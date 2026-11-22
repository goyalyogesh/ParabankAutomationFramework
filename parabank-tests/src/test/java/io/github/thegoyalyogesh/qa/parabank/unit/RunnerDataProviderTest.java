package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.runners.ApiRunner;
import io.github.thegoyalyogesh.qa.parabank.runners.LoginRunner;
import io.github.thegoyalyogesh.qa.parabank.runners.RegistrationRunner;
import io.github.thegoyalyogesh.qa.parabank.runners.TransferRunner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: annotations are not inherited by an overriding method. When the retry base class overrode
 * {@code scenarios()} without {@code @DataProvider}, ApiRunner failed with "requires a @DataProvider named: scenarios"
 * and its scenarios silently disappeared from the run.
 */
public class RunnerDataProviderTest {

    @Test
    public void everyRunnerExposesTheScenariosDataProvider() throws NoSuchMethodException {
        for (Class<?> runner : List.of(RegistrationRunner.class, LoginRunner.class, TransferRunner.class, ApiRunner.class)) {
            assertThat(runner.getMethod("scenarios").isAnnotationPresent(DataProvider.class))
                    .as(runner.getSimpleName() + ".scenarios() must carry @DataProvider")
                    .isTrue();
        }
    }
}
