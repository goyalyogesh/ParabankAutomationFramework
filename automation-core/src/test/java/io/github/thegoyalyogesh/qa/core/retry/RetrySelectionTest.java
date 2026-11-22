package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RetrySelectionTest {

    private static final URI LOGIN = URI.create("classpath:features/login.feature");

    @Test
    public void noPropertyMeansEveryScenario() {
        assertThat(RetrySelection.requested(null)).isEmpty();
        assertThat(RetrySelection.requested("  ")).isEmpty();
        assertThat(RetrySelection.selected(Set.of(), LOGIN, 14)).isTrue();
    }

    @Test
    public void selectsExactlyTheListedUriAndLine() {
        Set<String> ids = RetrySelection.requested(
                "classpath:features/login.feature:14, classpath:features/transfers.feature:37");

        assertThat(ids).containsExactlyInAnyOrder(
                "classpath:features/login.feature:14", "classpath:features/transfers.feature:37");
        assertThat(RetrySelection.selected(ids, LOGIN, 14)).isTrue();
        assertThat(RetrySelection.selected(ids, LOGIN, 15)).isFalse();
        assertThat(RetrySelection.selected(ids, URI.create("classpath:features/transfers.feature"), 37)).isTrue();
    }

    @Test
    public void propertyValueIsTheIdsJoinedWithCommas() {
        assertThat(RetrySelection.propertyValue(List.of(
                new ScenarioResult("a.feature:1", "F", "x", Set.of(), false),
                new ScenarioResult("b.feature:2", "F", "y", Set.of(), false))))
                .isEqualTo("a.feature:1,b.feature:2");
    }
}
