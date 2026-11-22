package io.github.thegoyalyogesh.qa.core.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base for REST clients: one shared request specification (base URI, JSON) per API, and
 * {@link #send} which waits and repeats a request that was rejected with HTTP 429 (rate limited).
 */
public class ApiClient {

    /** How the client pauses; replaced by a recording lambda in tests so they don't really wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);

        Sleeper REAL = duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the rate limit to pass", e);
            }
        };
    }

    private static final Logger LOG = LogManager.getLogger(ApiClient.class);
    private static final int TOO_MANY_REQUESTS = 429;

    private final RequestSpecification spec;
    private final int maxRetries;
    private final Duration firstWait;
    private final Sleeper sleeper;

    /** Defaults: up to 3 retries, waiting 30 s, 60 s, 90 s unless the server sends {@code Retry-After}. */
    public ApiClient(String baseUri) {
        this(baseUri, 3, Duration.ofSeconds(30), Sleeper.REAL);
    }

    public ApiClient(String baseUri, int maxRetries, Duration firstWait, Sleeper sleeper) {
        this.spec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setAccept(ContentType.JSON)
                .build();
        this.maxRetries = maxRetries;
        this.firstWait = firstWait;
        this.sleeper = sleeper;
    }

    public RequestSpecification spec() {
        return spec;
    }

    /** New request using the shared spec; request and response are logged only if a validation fails. */
    public RequestSpecification request() {
        return RestAssured.given().spec(spec).log().ifValidationFails();
    }

    /**
     * Sends {@code call} on a fresh request. On HTTP 429 waits ({@code Retry-After} seconds if present, otherwise
     * 1×, 2×, 3× {@code firstWait}) and sends it again, at most {@code maxRetries} times. Safe even for POST:
     * a 429 means the request was rejected before the application processed it.
     * Returns the last response, which is still a 429 if every retry was rate limited.
     */
    public Response send(Function<RequestSpecification, Response> call) {
        Response response = call.apply(request());
        for (int attempt = 1; response.statusCode() == TOO_MANY_REQUESTS && attempt <= maxRetries; attempt++) {
            Duration wait = retryAfter(response).orElse(firstWait.multipliedBy(attempt));
            LOG.warn("HTTP 429 (rate limited), waiting {} s before retry {}/{}", wait.toSeconds(), attempt, maxRetries);
            sleeper.sleep(wait);
            response = call.apply(request());
        }
        return response;
    }

    private static Optional<Duration> retryAfter(Response response) {
        String header = response.getHeader("Retry-After");
        if (header == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(header.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty(); // HTTP-date form: fall back to our own back-off
        }
    }
}
