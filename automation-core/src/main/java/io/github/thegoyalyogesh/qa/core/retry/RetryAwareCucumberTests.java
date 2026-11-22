package io.github.thegoyalyogesh.qa.core.retry;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.Pickle;
import io.cucumber.testng.PickleWrapper;
import org.testng.annotations.DataProvider;

import java.util.Arrays;
import java.util.Set;

/**
 * Base class for Cucumber TestNG runners that the retry engine can re-run selectively. Each data-provider row is
 * {@code [PickleWrapper, FeatureWrapper]}; rows not listed in {@code -Dretry.scenarios} are dropped. Without the
 * property it behaves exactly like {@link AbstractTestNGCucumberTests}. Subclasses that run in parallel override
 * {@code scenarios()} with {@code @DataProvider(parallel = true)} and return {@code super.scenarios()}.
 * <p>
 * The {@code @DataProvider} here is required: annotations are not inherited by an overriding method, and without it
 * TestNG fails with "requires a @DataProvider named: scenarios" for runners that don't override it themselves.
 */
public abstract class RetryAwareCucumberTests extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider
    public Object[][] scenarios() {
        Set<String> requested = RetrySelection.requested(System.getProperty(RetrySelection.PROPERTY));
        return Arrays.stream(super.scenarios())
                .filter(row -> {
                    Pickle pickle = ((PickleWrapper) row[0]).getPickle();
                    return RetrySelection.selected(requested, pickle.getUri(), pickle.getLine());
                })
                .toArray(Object[][]::new);
    }
}
