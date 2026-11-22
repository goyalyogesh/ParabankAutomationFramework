package io.github.thegoyalyogesh.qa.parabank.api;

import io.github.thegoyalyogesh.qa.core.api.ApiClient;
import io.github.thegoyalyogesh.qa.parabank.model.Account;
import io.github.thegoyalyogesh.qa.parabank.model.Transaction;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The few ParaBank REST calls the UI tests need (the full API suite comes in Plan 06). */
public final class ParaBankApi {

    /** Parse JSON numbers as BigDecimal so money keeps exact cents. */
    private static final JsonPathConfig MONEY = new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

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

    public List<Account> accounts(long customerId) {
        return rows(client.request().pathParam("customerId", customerId).get("/customers/{customerId}/accounts"))
                .stream().map(ParaBankApi::toAccount).toList();
    }

    public List<Long> accountIds(long customerId) {
        return accounts(customerId).stream().map(Account::id).toList();
    }

    public Account account(long accountId) {
        Response response = client.request().pathParam("accountId", accountId).get("/accounts/{accountId}");
        response.then().statusCode(200);
        return toAccount(response.jsonPath(MONEY).getMap("$"));
    }

    public BigDecimal balance(long accountId) {
        return account(accountId).balance();
    }

    public List<Transaction> transactions(long accountId) {
        return rows(client.request().pathParam("accountId", accountId).get("/accounts/{accountId}/transactions"))
                .stream().map(ParaBankApi::toTransaction).toList();
    }

    /**
     * {@code POST /transfer}. Finding: ParaBank does not check who owns the accounts and needs no authentication,
     * so money can be moved between different customers (see guide 09).
     */
    public String transfer(long fromAccount, long toAccount, BigDecimal amount) {
        return client.request()
                .queryParam("fromAccountId", fromAccount)
                .queryParam("toAccountId", toAccount)
                .queryParam("amount", amount.toPlainString())
                .post("/transfer")
                .then().statusCode(200)
                .extract().asString();
    }

    private static List<Map<String, Object>> rows(Response response) {
        response.then().statusCode(200);
        return response.jsonPath(MONEY).getList("$");
    }

    private static Account toAccount(Map<String, ?> json) {
        return new Account(number(json, "id"), number(json, "customerId"), String.valueOf(json.get("type")),
                new BigDecimal(String.valueOf(json.get("balance"))));
    }

    private static Transaction toTransaction(Map<String, ?> json) {
        return new Transaction(number(json, "id"), number(json, "accountId"), String.valueOf(json.get("type")),
                new BigDecimal(String.valueOf(json.get("amount"))), String.valueOf(json.get("description")));
    }

    private static long number(Map<String, ?> json, String key) {
        return ((Number) json.get(key)).longValue();
    }
}
