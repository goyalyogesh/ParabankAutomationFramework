package io.github.thegoyalyogesh.qa.parabank.api;

import io.github.thegoyalyogesh.qa.core.api.ApiClient;
import io.github.thegoyalyogesh.qa.core.api.OpenApiContract;
import io.github.thegoyalyogesh.qa.parabank.model.Account;
import io.github.thegoyalyogesh.qa.parabank.model.AccountType;
import io.github.thegoyalyogesh.qa.parabank.model.Customer;
import io.github.thegoyalyogesh.qa.parabank.model.LoanResult;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.Position;
import io.github.thegoyalyogesh.qa.parabank.model.Transaction;
import io.restassured.filter.Filter;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Client for the ParaBank REST API ({@code services/bank}). Every call goes through {@link ApiClient#send}
 * (HTTP 429 retries). {@link #validating(OpenApiContract)} returns a copy that checks every request and response
 * against the OpenAPI contract.
 * <p>
 * Not covered on purpose: {@code /cleanDB}, {@code /initializeDB}, {@code /setParameter}, {@code /startupJmsListener},
 * {@code /shutdownJmsListener}. They reset or reconfigure the whole public site for every user
 * ({@link #EXCLUDED_OPERATIONS}; a unit test checks nothing calls them).
 */
public final class ParaBankApi {

    // Path templates, one per operation of the spec (ApiCoverageTest compares them with the spec)
    static final String LOGIN = "/login/{username}/{password}";
    static final String CUSTOMER = "/customers/{customerId}";
    static final String UPDATE_CUSTOMER = "/customers/update/{customerId}";
    static final String CUSTOMER_ACCOUNTS = "/customers/{customerId}/accounts";
    static final String CUSTOMER_POSITIONS = "/customers/{customerId}/positions";
    static final String BUY_POSITION = "/customers/{customerId}/buyPosition";
    static final String SELL_POSITION = "/customers/{customerId}/sellPosition";
    static final String POSITION = "/positions/{positionId}";
    static final String POSITION_HISTORY = "/positions/{positionId}/{startDate}/{endDate}";
    static final String ACCOUNT = "/accounts/{accountId}";
    static final String CREATE_ACCOUNT = "/createAccount";
    static final String DEPOSIT = "/deposit";
    static final String WITHDRAW = "/withdraw";
    static final String TRANSFER = "/transfer";
    static final String BILL_PAY = "/billpay";
    static final String REQUEST_LOAN = "/requestLoan";
    static final String TRANSACTION = "/transactions/{transactionId}";
    static final String TRANSACTIONS = "/accounts/{accountId}/transactions";
    static final String TRANSACTIONS_BY_AMOUNT = "/accounts/{accountId}/transactions/amount/{amount}";
    static final String TRANSACTIONS_ON_DATE = "/accounts/{accountId}/transactions/onDate/{onDate}";
    static final String TRANSACTIONS_BETWEEN = "/accounts/{accountId}/transactions/fromDate/{fromDate}/toDate/{toDate}";
    static final String TRANSACTIONS_BY_MONTH_AND_TYPE = "/accounts/{accountId}/transactions/month/{month}/type/{type}";

    /** Every operation this client implements, in the spec's notation. */
    public static final Set<String> OPERATIONS = Set.of(
            "GET " + LOGIN, "GET " + CUSTOMER, "POST " + UPDATE_CUSTOMER, "GET " + CUSTOMER_ACCOUNTS,
            "GET " + CUSTOMER_POSITIONS, "POST " + BUY_POSITION, "POST " + SELL_POSITION, "GET " + POSITION,
            "GET " + POSITION_HISTORY, "GET " + ACCOUNT, "POST " + CREATE_ACCOUNT, "POST " + DEPOSIT,
            "POST " + WITHDRAW, "POST " + TRANSFER, "POST " + BILL_PAY, "POST " + REQUEST_LOAN, "GET " + TRANSACTION,
            "GET " + TRANSACTIONS, "GET " + TRANSACTIONS_BY_AMOUNT, "GET " + TRANSACTIONS_ON_DATE,
            "GET " + TRANSACTIONS_BETWEEN, "GET " + TRANSACTIONS_BY_MONTH_AND_TYPE);

    /** Operations of the spec that tests must never call: they affect the shared public site for everybody. */
    public static final Set<String> EXCLUDED_OPERATIONS = Set.of(
            "POST /cleanDB", "POST /initializeDB", "POST /setParameter/{name}/{value}",
            "POST /startupJmsListener", "POST /shutdownJmsListener");

    /** ParaBank's date format in paths, e.g. {@code 11-22-2026}. */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** Parse JSON numbers as BigDecimal so money keeps exact cents. */
    private static final JsonPathConfig MONEY = new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

    private final ApiClient client;
    private final Filter contractFilter; // null = no contract validation

    public ParaBankApi(String baseUrl) {
        this(new ApiClient(baseUrl), null);
    }

    private ParaBankApi(ApiClient client, Filter contractFilter) {
        this.client = client;
        this.contractFilter = contractFilter;
    }

    /** Same client, but every call fails if its request or response breaks the OpenAPI contract. */
    public ParaBankApi validating(OpenApiContract contract) {
        return new ParaBankApi(client, contract.filter());
    }

    private Response send(Function<RequestSpecification, Response> call) {
        return client.send(request -> call.apply(contractFilter == null ? request : request.filter(contractFilter)));
    }

    // ---------------------------------------------------------------- customers

    /** Customer id if the credentials work, otherwise empty. The API, unlike the UI, rejects bad credentials. */
    public Optional<Long> login(String username, String password) {
        Response response = send(request -> request
                .pathParam("username", username)
                .pathParam("password", password)
                .get(LOGIN));
        boolean ok = response.statusCode() == 200 && String.valueOf(response.contentType()).contains("json");
        return ok ? Optional.of(response.jsonPath().getLong("id")) : Optional.empty();
    }

    public Customer customer(long customerId) {
        Response response = send(request -> request.pathParam("customerId", customerId).get(CUSTOMER));
        response.then().statusCode(200);
        Map<String, Object> json = response.jsonPath().getMap("$");
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) json.get("address");
        return new Customer(number(json, "id"), text(json, "firstName"), text(json, "lastName"),
                text(address, "street"), text(address, "city"), text(address, "state"), text(address, "zipCode"),
                text(json, "phoneNumber"), text(json, "ssn"));
    }

    /** {@code POST /customers/update/{id}}: every field is required, so the whole profile is sent. */
    public String updateCustomer(long customerId, ParaBankUser user, String phone) {
        return send(request -> request
                        .pathParam("customerId", customerId)
                        .queryParam("firstName", user.firstName())
                        .queryParam("lastName", user.lastName())
                        .queryParam("street", user.street())
                        .queryParam("city", user.city())
                        .queryParam("state", user.state())
                        .queryParam("zipCode", user.zipCode())
                        .queryParam("phoneNumber", phone)
                        .queryParam("ssn", user.ssn())
                        .queryParam("username", user.username())
                        .queryParam("password", user.password())
                        .post(UPDATE_CUSTOMER))
                .then().statusCode(200)
                .extract().asString();
    }

    // ---------------------------------------------------------------- accounts

    public List<Account> accounts(long customerId) {
        return rows(send(request -> request.pathParam("customerId", customerId).get(CUSTOMER_ACCOUNTS)))
                .stream().map(ParaBankApi::toAccount).toList();
    }

    public List<Long> accountIds(long customerId) {
        return accounts(customerId).stream().map(Account::id).toList();
    }

    public Account account(long accountId) {
        Response response = send(request -> request.pathParam("accountId", accountId).get(ACCOUNT));
        response.then().statusCode(200);
        return toAccount(response.jsonPath(MONEY).getMap("$"));
    }

    public BigDecimal balance(long accountId) {
        return account(accountId).balance();
    }

    /** {@code POST /createAccount}: opens an account funded from {@code fromAccount}; returns the new account. */
    public Account createAccount(long customerId, AccountType type, long fromAccount) {
        Response response = send(request -> request
                .queryParam("customerId", customerId)
                .queryParam("newAccountType", type.code())
                .queryParam("fromAccountId", fromAccount)
                .post(CREATE_ACCOUNT));
        response.then().statusCode(200);
        return toAccount(response.jsonPath(MONEY).getMap("$"));
    }

    public String deposit(long accountId, BigDecimal amount) {
        return moneyCall(DEPOSIT, accountId, amount);
    }

    public String withdraw(long accountId, BigDecimal amount) {
        return moneyCall(WITHDRAW, accountId, amount);
    }

    private String moneyCall(String path, long accountId, BigDecimal amount) {
        return send(request -> request
                        .queryParam("accountId", accountId)
                        .queryParam("amount", amount.toPlainString())
                        .post(path))
                .then().statusCode(200)
                .extract().asString();
    }

    /**
     * {@code POST /transfer}. Finding: ParaBank does not check who owns the accounts and needs no authentication,
     * so money can be moved between different customers (see guide 09).
     */
    public String transfer(long fromAccount, long toAccount, BigDecimal amount) {
        return send(request -> request
                        .queryParam("fromAccountId", fromAccount)
                        .queryParam("toAccountId", toAccount)
                        .queryParam("amount", amount.toPlainString())
                        .post(TRANSFER))
                .then().statusCode(200)
                .extract().asString();
    }

    /**
     * {@code POST /billpay}: pays {@code payee} (sent as JSON) from {@code fromAccount}. Debits the payer only;
     * the payee counts as an external company.
     */
    public String billPay(long fromAccount, ParaBankUser payee, BigDecimal amount) {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("street", payee.street());
        address.put("city", payee.city());
        address.put("state", payee.state());
        address.put("zipCode", payee.zipCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", payee.fullName());
        body.put("address", address);
        body.put("phoneNumber", payee.phone());
        body.put("accountNumber", payee.accountNumber());
        return send(request -> request
                        .queryParam("accountId", fromAccount)
                        .queryParam("amount", amount.toPlainString())
                        .contentType(ContentType.JSON)
                        .body(body)
                        .post(BILL_PAY))
                .then().statusCode(200)
                .extract().asString();
    }

    public LoanResult requestLoan(long customerId, BigDecimal amount, BigDecimal downPayment, long fromAccount) {
        Response response = send(request -> request
                .queryParam("customerId", customerId)
                .queryParam("amount", amount.toPlainString())
                .queryParam("downPayment", downPayment.toPlainString())
                .queryParam("fromAccountId", fromAccount)
                .post(REQUEST_LOAN));
        response.then().statusCode(200);
        Map<String, Object> json = response.jsonPath().getMap("$");
        Object accountId = json.get("accountId");
        return new LoanResult(Boolean.TRUE.equals(json.get("approved")), text(json, "message"),
                accountId == null ? Optional.empty() : Optional.of(((Number) accountId).longValue()),
                text(json, "loanProviderName"));
    }

    // ---------------------------------------------------------------- positions (shares)

    public List<Position> buyPosition(long customerId, long accountId, String name, String symbol, int shares,
                                      BigDecimal pricePerShare) {
        return rows(send(request -> request
                .pathParam("customerId", customerId)
                .queryParam("accountId", accountId)
                .queryParam("name", name)
                .queryParam("symbol", symbol)
                .queryParam("shares", shares)
                .queryParam("pricePerShare", pricePerShare.toPlainString())
                .post(BUY_POSITION))).stream().map(ParaBankApi::toPosition).toList();
    }

    public List<Position> sellPosition(long customerId, long accountId, long positionId, int shares,
                                       BigDecimal pricePerShare) {
        return rows(send(request -> request
                .pathParam("customerId", customerId)
                .queryParam("accountId", accountId)
                .queryParam("positionId", positionId)
                .queryParam("shares", shares)
                .queryParam("pricePerShare", pricePerShare.toPlainString())
                .post(SELL_POSITION))).stream().map(ParaBankApi::toPosition).toList();
    }

    public List<Position> positions(long customerId) {
        return rows(send(request -> request.pathParam("customerId", customerId).get(CUSTOMER_POSITIONS)))
                .stream().map(ParaBankApi::toPosition).toList();
    }

    public Position position(long positionId) {
        Response response = send(request -> request.pathParam("positionId", positionId).get(POSITION));
        response.then().statusCode(200);
        return toPosition(response.jsonPath(MONEY).getMap("$"));
    }

    /** Closing prices of the position's symbol between two dates (one entry per day with a price). */
    public List<Map<String, Object>> positionHistory(long positionId, LocalDate from, LocalDate to) {
        return rows(send(request -> request
                .pathParam("positionId", positionId)
                .pathParam("startDate", DATE.format(from))
                .pathParam("endDate", DATE.format(to))
                .get(POSITION_HISTORY)));
    }

    // ---------------------------------------------------------------- transactions

    public Transaction transaction(long transactionId) {
        Response response = send(request -> request.pathParam("transactionId", transactionId).get(TRANSACTION));
        response.then().statusCode(200);
        return toTransaction(response.jsonPath(MONEY).getMap("$"));
    }

    public List<Transaction> transactions(long accountId) {
        return transactionList(request -> request.pathParam("accountId", accountId).get(TRANSACTIONS));
    }

    public List<Transaction> transactionsByAmount(long accountId, BigDecimal amount) {
        return transactionList(request -> request.pathParam("accountId", accountId)
                .pathParam("amount", amount.toPlainString()).get(TRANSACTIONS_BY_AMOUNT));
    }

    public List<Transaction> transactionsOn(long accountId, LocalDate date) {
        return transactionList(request -> request.pathParam("accountId", accountId)
                .pathParam("onDate", DATE.format(date)).get(TRANSACTIONS_ON_DATE));
    }

    public List<Transaction> transactionsBetween(long accountId, LocalDate from, LocalDate to) {
        return transactionList(request -> request.pathParam("accountId", accountId)
                .pathParam("fromDate", DATE.format(from)).pathParam("toDate", DATE.format(to))
                .get(TRANSACTIONS_BETWEEN));
    }

    /** {@code month}: {@code All}, {@code January}...; {@code type}: {@code All}, {@code Credit}, {@code Debit}. */
    public List<Transaction> transactionsByMonthAndType(long accountId, String month, String type) {
        return transactionList(request -> request.pathParam("accountId", accountId)
                .pathParam("month", month).pathParam("type", type).get(TRANSACTIONS_BY_MONTH_AND_TYPE));
    }

    private List<Transaction> transactionList(Function<RequestSpecification, Response> call) {
        return rows(send(call)).stream().map(ParaBankApi::toTransaction).toList();
    }

    // ---------------------------------------------------------------- raw access for negative tests

    /**
     * Sends a request exactly as given and returns the response unchecked (no status assertion, no contract
     * validation): negative tests assert status and message themselves. {@code pathAndQuery} is relative to the
     * API base URL, e.g. {@code /accounts/99999999}.
     */
    public Response raw(String method, String pathAndQuery) {
        return client.send(request -> request.urlEncodingEnabled(false).request(method, pathAndQuery));
    }

    // ---------------------------------------------------------------- JSON mapping

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
                LocalDate.ofInstant(Instant.ofEpochMilli(number(json, "date")), ZoneOffset.UTC),
                new BigDecimal(String.valueOf(json.get("amount"))), String.valueOf(json.get("description")));
    }

    private static Position toPosition(Map<String, ?> json) {
        return new Position(number(json, "positionId"), number(json, "customerId"), text(json, "name"),
                text(json, "symbol"), (int) number(json, "shares"),
                new BigDecimal(String.valueOf(json.get("purchasePrice"))));
    }

    private static long number(Map<String, ?> json, String key) {
        return ((Number) json.get(key)).longValue();
    }

    private static String text(Map<String, ?> json, String key) {
        Object value = json.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
