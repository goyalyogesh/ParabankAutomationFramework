package io.github.thegoyalyogesh.qa.core.api;

import com.sun.net.httpserver.HttpServer;
import io.restassured.response.Response;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the JDK's built-in HTTP server: answers 429 a given number of times, then 200. */
public class ApiClientRetryTest {

    private HttpServer server;
    private String baseUri;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int rateLimitedAnswers;
    private volatile String retryAfter;
    private final List<Duration> waits = new ArrayList<>();

    @BeforeMethod
    public void startServer() throws IOException {
        hits.set(0);
        waits.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            boolean limited = hits.incrementAndGet() <= rateLimitedAnswers;
            byte[] body = (limited ? "slow down" : "{\"ok\":true}").getBytes(StandardCharsets.UTF_8);
            if (limited && retryAfter != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            exchange.sendResponseHeaders(limited ? 429 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterMethod(alwaysRun = true)
    public void stopServer() {
        server.stop(0);
    }

    @Test
    public void waitsForRetryAfterHeaderThenSucceeds() {
        rateLimitedAnswers = 2;
        retryAfter = "7";
        ApiClient client = new ApiClient(baseUri, 3, Duration.ofSeconds(30), waits::add);

        Response response = client.send(request -> request.get("/ping"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(hits.get()).isEqualTo(3);
        assertThat(waits).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(7));
    }

    @Test
    public void withoutRetryAfterWaitsLongerEachTime() {
        rateLimitedAnswers = 2;
        retryAfter = null;
        ApiClient client = new ApiClient(baseUri, 3, Duration.ofSeconds(30), waits::add);

        assertThat(client.send(request -> request.get("/ping")).statusCode()).isEqualTo(200);
        assertThat(waits).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    @Test
    public void givesUpAfterMaxRetriesAndReturnsThe429() {
        rateLimitedAnswers = 100;
        retryAfter = null;
        ApiClient client = new ApiClient(baseUri, 2, Duration.ofSeconds(1), waits::add);

        assertThat(client.send(request -> request.get("/ping")).statusCode()).isEqualTo(429);
        assertThat(hits.get()).isEqualTo(3);
        assertThat(waits).hasSize(2);
    }

    @Test
    public void successIsNotRetried() {
        rateLimitedAnswers = 0;
        ApiClient client = new ApiClient(baseUri, 3, Duration.ofSeconds(1), waits::add);

        assertThat(client.send(request -> request.get("/ping")).statusCode()).isEqualTo(200);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(waits).isEmpty();
    }
}
