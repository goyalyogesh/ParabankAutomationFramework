package io.github.thegoyalyogesh.qa.parabank.api;

import io.github.thegoyalyogesh.qa.core.api.ApiClient;
import io.restassured.response.Response;

import java.util.List;
import java.util.Optional;

/** The few ParaBank REST calls the UI tests need (the full API suite comes in Plan 06). */
public final class ParaBankApi {

    private final ApiClient client;

    public ParaBankApi(String baseUrl) {
        this.client = new ApiClient(baseUrl);
    }

    /** Customer id if the credentials work, otherwise empty. The API, unlike the UI, rejects bad credentials. */
    public Optional<Long> login(String username, String password) {
        Response response = client.request()
                .pathParam("username", username)
                .pathParam("password", password)
                .get("/login/{username}/{password}");
        boolean ok = response.statusCode() == 200 && String.valueOf(response.contentType()).contains("json");
        return ok ? Optional.of(response.jsonPath().getLong("id")) : Optional.empty();
    }

    public List<Long> accountIds(long customerId) {
        return client.request()
                .pathParam("customerId", customerId)
                .get("/customers/{customerId}/accounts")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class);
    }
}
