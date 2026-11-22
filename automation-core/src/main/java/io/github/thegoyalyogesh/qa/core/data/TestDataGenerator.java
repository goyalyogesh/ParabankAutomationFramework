package io.github.thegoyalyogesh.qa.core.data;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Generates unique, valid-looking test data. Thread-safe. */
public final class TestDataGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final AtomicInteger sequence = new AtomicInteger();
    private final Clock clock;
    private final Random random;

    public TestDataGenerator() {
        this(Clock.systemDefaultZone(), new SecureRandom());
    }

    public TestDataGenerator(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    /** e.g. {@code qa_20261121183405_007}: timestamp separates runs, sequence separates threads. */
    public String uniqueUsername(String prefix) {
        String timestamp = LocalDateTime.now(clock).format(TIMESTAMP);
        int next = sequence.incrementAndGet() % 1000;
        return "%s_%s_%03d".formatted(prefix, timestamp, next);
    }

    /** 10 chars: 3 upper + 4 lower + 3 digits (ambiguous characters like O/0/l/1 excluded). */
    public String password() {
        return pick(UPPER, 3) + pick(LOWER, 4) + pick(DIGITS, 3);
    }

    public String ssn() {
        return "%03d-%02d-%04d".formatted(100 + random.nextInt(800), 10 + random.nextInt(90), 1000 + random.nextInt(9000));
    }

    public String phone() {
        return "555-%03d-%04d".formatted(100 + random.nextInt(900), random.nextInt(10000));
    }

    public String zipCode() {
        return "%05d".formatted(10000 + random.nextInt(90000));
    }

    private String pick(String alphabet, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
