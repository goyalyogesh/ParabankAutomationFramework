package io.github.thegoyalyogesh.qa.core.data;

import org.testng.annotations.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDataGeneratorTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T18:34:05Z"), ZoneOffset.UTC);
    private final TestDataGenerator generator = new TestDataGenerator(fixedClock, new Random(1));

    @Test
    public void usernameContainsPrefixTimestampAndSequence() {
        assertThat(generator.uniqueUsername("qa")).matches("qa_20261121183405_\\d{3}");
    }

    @Test
    public void usernamesAreUniqueEvenInParallelWithSameClock() {
        Set<String> names = IntStream.range(0, 500).parallel()
                .mapToObj(i -> generator.uniqueUsername("qa"))
                .collect(Collectors.toSet());
        assertThat(names).hasSize(500);
    }

    @Test
    public void passwordHasUpperLowerAndDigits() {
        assertThat(generator.password()).matches("(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)[A-Za-z\\d]{10}");
    }

    @Test
    public void ssnPhoneAndZipHaveUsFormats() {
        assertThat(generator.ssn()).matches("\\d{3}-\\d{2}-\\d{4}");
        assertThat(generator.phone()).matches("555-\\d{3}-\\d{4}");
        assertThat(generator.zipCode()).matches("\\d{5}");
    }
}
