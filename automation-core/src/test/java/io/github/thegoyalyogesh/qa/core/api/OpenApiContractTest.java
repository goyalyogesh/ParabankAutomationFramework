package io.github.thegoyalyogesh.qa.core.api;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenApiContractTest {

    private static final OpenApiContract CONTRACT = OpenApiContract.load("openapi/mini-bank.yaml");
    private HttpServer server;
    private String baseUri;

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/bank/accounts/1", "{\"id\":1,\"balance\":10.50,\"opened\":\"2026-11-22T10:00:00Z\"}");
        respond("/bank/accounts/2", "{\"id\":2,\"balance\":10.50,\"opened\":1795305600000}");
        server.start();
        baseUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/bank";
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        server.stop(0);
    }

    private void respond(String path, String json) {
        server.createContext(path, exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    public void listsEveryOperationOfTheSpec() {
        assertThat(CONTRACT.operations()).containsExactly("GET /accounts/{accountId}", "POST /deposit");
    }

    @Test
    public void responseThatMatchesTheSchemaPasses() {
        RestAssured.given().baseUri(baseUri).filter(CONTRACT.filter())
                .get("/accounts/1")
                .then().statusCode(200);
    }

    @Test
    public void responseThatBreaksTheSchemaFailsWithTheReason() {
        assertThatThrownBy(() -> RestAssured.given().baseUri(baseUri).filter(CONTRACT.filter()).get("/accounts/2"))
                .hasMessageContaining("opened")
                .hasMessageContaining("integer");
    }

    @Test
    public void missingSpecFailsClearly() {
        assertThatThrownBy(() -> OpenApiContract.load("openapi/does-not-exist.yaml"))
                .hasMessageContaining("does-not-exist.yaml");
    }
}
