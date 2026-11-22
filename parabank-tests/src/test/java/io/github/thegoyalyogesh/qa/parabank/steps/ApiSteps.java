package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.core.api.OpenApiContract;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.model.Account;
import io.github.thegoyalyogesh.qa.parabank.model.AccountType;
import io.github.thegoyalyogesh.qa.parabank.model.Customer;
import io.github.thegoyalyogesh.qa.parabank.model.LoanResult;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.Position;
import io.github.thegoyalyogesh.qa.parabank.model.Transaction;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps of the REST API suite (features api-*.feature). No browser: these scenarios are tagged {@code @api}. */
public class ApiSteps {

    /** Loaded once: parsing the spec takes a moment and it never changes during a run. */
    private static final OpenApiContract CONTRACT = OpenApiContract.load("openapi/parabank-openapi.yaml");

    private final TestContext ctx;

    private ParaBankUser user;
    private long customerId;
    private long mainAccount;
    private BigDecimal mainBalanceAtStart;
    private String originalPhone;
    private Set<Long> accountsBefore;
    private Account openedAccount;
    private LoanResult loan;
    private Position boughtPosition;
    private Response response;

    public ApiSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    private ParaBankApi api() {
        return ctx.api();
    }

    // ------------------------------------------------------------------ setup

    @Given("API customer {string} is logged in through the REST API")
    public void apiCustomerLoggedIn(String userId) {
        user = ctx.registeredUser(userId);
        customerId = api().login(user.username(), user.password())
                .orElseThrow(() -> new AssertionError("REST login failed for " + userId + " (" + user.username() + ")"));
        // The registration account is the customer's first CHECKING account (lowest id)
        mainAccount = api().accounts(customerId).stream()
                .filter(account -> "CHECKING".equals(account.type()))
                .map(Account::id)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new AssertionError(userId + " has no CHECKING account"));
        mainBalanceAtStart = api().balance(mainAccount);
    }

    @Then("the main account balance changed by exactly {bigdecimal}")
    public void mainBalanceChangedBy(BigDecimal expectedChange) {
        assertThat(api().balance(mainAccount))
                .as("main account %d: start %s + change %s", mainAccount, mainBalanceAtStart, expectedChange)
                .isEqualByComparingTo(mainBalanceAtStart.add(expectedChange));
    }

    // ------------------------------------------------------------------ customer

    @Then("the API customer profile matches the Users sheet")
    public void profileMatchesSheet() {
        Customer customer = api().customer(customerId);
        assertThat(customer.id()).isEqualTo(customerId);
        assertThat(customer.firstName()).isEqualTo(user.firstName());
        assertThat(customer.lastName()).isEqualTo(user.lastName());
        assertThat(customer.street()).isEqualTo(user.street());
        assertThat(customer.city()).isEqualTo(user.city());
        assertThat(customer.state()).isEqualTo(user.state());
        assertThat(customer.zipCode()).isEqualTo(user.zipCode());
        assertThat(customer.ssn()).isEqualTo(user.ssn());
    }

    @Then("every account returned for the customer belongs to the customer")
    public void accountsBelongToCustomer() {
        List<Account> accounts = api().accounts(customerId);
        assertThat(accounts).isNotEmpty()
                .allSatisfy(account -> assertThat(account.customerId()).isEqualTo(customerId));
        assertThat(accounts).extracting(Account::id).contains(mainAccount).doesNotHaveDuplicates();
    }

    @When("the API customer's phone number is changed to {string}")
    public void changePhone(String phone) {
        originalPhone = api().customer(customerId).phone();
        assertThat(api().updateCustomer(customerId, user, phone)).containsIgnoringCase("updated");
    }

    @Then("the API returns phone number {string}")
    public void apiReturnsPhone(String phone) {
        assertThat(api().customer(customerId).phone()).isEqualTo(phone);
    }

    @Then("the API customer's original phone number is restored")
    public void restorePhone() {
        api().updateCustomer(customerId, user, originalPhone);
        assertThat(api().customer(customerId).phone()).isEqualTo(originalPhone);
    }

    // ------------------------------------------------------------------ deposit / withdraw / transactions

    @When("{bigdecimal} is deposited into the main account through the API")
    public void deposit(BigDecimal amount) {
        assertThat(api().deposit(mainAccount, amount)).contains("Successfully deposited");
    }

    @When("{bigdecimal} is withdrawn from the main account through the API")
    public void withdraw(BigDecimal amount) {
        assertThat(api().withdraw(mainAccount, amount)).contains("Successfully withdrew");
    }

    @Then("a {string} transaction of {bigdecimal} is found by amount, by today's date, by date range and by type")
    public void transactionFoundEveryWay(String type, BigDecimal amount) {
        List<Transaction> byAmount = api().transactionsByAmount(mainAccount, amount);
        Transaction newest = byAmount.stream()
                .filter(t -> type.equals(t.type()))
                .max(Comparator.comparingLong(Transaction::id))
                .orElseThrow(() -> new AssertionError("No " + type + " of " + amount + " in " + byAmount));
        assertThat(newest.amount()).isEqualByComparingTo(amount);

        // The server stores the date as midnight in its own time zone; use the transaction's own date
        LocalDate date = transactionDate(newest.id());
        assertThat(api().transactionsOn(mainAccount, date)).extracting(Transaction::id).contains(newest.id());
        assertThat(api().transactionsBetween(mainAccount, date.minusDays(1), date.plusDays(1)))
                .extracting(Transaction::id).contains(newest.id());
        assertThat(api().transactionsByMonthAndType(mainAccount, "All", type))
                .extracting(Transaction::id).contains(newest.id())
                .as("type filter").doesNotContainNull();
        assertThat(api().transactionsByMonthAndType(mainAccount, "All", type))
                .allSatisfy(t -> assertThat(t.type()).isEqualTo(type));
    }

    @Then("the newest transaction found by id is a {string} of {bigdecimal}")
    public void newestTransactionById(String type, BigDecimal amount) {
        long newestId = api().transactions(mainAccount).stream().mapToLong(Transaction::id).max().orElseThrow();
        Transaction transaction = api().transaction(newestId);
        assertThat(transaction.accountId()).isEqualTo(mainAccount);
        assertThat(transaction.type()).isEqualTo(type);
        assertThat(transaction.amount()).isEqualByComparingTo(amount);
    }

    /** {@code date} is epoch milliseconds (the contract says date-time string: a known defect, see api-contract). */
    private LocalDate transactionDate(long transactionId) {
        long epochMillis = api().raw("GET", "/transactions/" + transactionId).jsonPath().getLong("date");
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    // ------------------------------------------------------------------ accounts

    @When("a new {word} account is opened through the API from the main account")
    public void openAccount(String type) {
        accountsBefore = new HashSet<>(api().accountIds(customerId));
        openedAccount = api().createAccount(customerId, AccountType.valueOf(type), mainAccount);
    }

    @Then("the customer has exactly one new account, of type {word}")
    public void exactlyOneNewAccount(String type) {
        Set<Long> added = new HashSet<>(api().accountIds(customerId));
        added.removeAll(accountsBefore); // Set difference: after − before
        assertThat(added).containsExactly(openedAccount.id());
        assertThat(api().account(openedAccount.id()).type()).isEqualTo(type);
        assertThat(openedAccount.customerId()).isEqualTo(customerId);
    }

    @Then("the opening deposit left the main account and is the new account's balance")
    public void openingDepositMoved() {
        BigDecimal deposit = api().balance(openedAccount.id());
        assertThat(deposit).isPositive();
        mainBalanceChangedBy(deposit.negate());
    }

    @When("the opening deposit is transferred back to the main account")
    public void transferDepositBack() {
        api().transfer(openedAccount.id(), mainAccount, api().balance(openedAccount.id()));
    }

    // ------------------------------------------------------------------ loans

    @When("a loan of {bigdecimal} with a down payment of {bigdecimal} is requested from the main account")
    public void requestLoan(BigDecimal amount, BigDecimal downPayment) {
        loan = api().requestLoan(customerId, amount, downPayment, mainAccount);
    }

    @When("a loan of {bigdecimal} with a down payment of the main balance plus {bigdecimal} is requested")
    public void requestLoanTooBig(BigDecimal amount, BigDecimal extra) {
        BigDecimal downPayment = mainBalanceAtStart.max(BigDecimal.ZERO).add(extra);
        loan = api().requestLoan(customerId, amount, downPayment, mainAccount);
        if (loan.approved()) {
            api().deposit(mainAccount, downPayment); // defect: approved anyway; refund so the account isn't overdrawn
        }
    }

    @Then("the loan is approved with a new LOAN account of balance {bigdecimal}")
    public void loanApproved(BigDecimal amount) {
        assertThat(loan.approved()).as("approved (message: %s)", loan.message()).isTrue();
        long loanAccount = loan.loanAccountId().orElseThrow(() -> new AssertionError("Approved loan without account"));
        Account account = api().account(loanAccount);
        assertThat(account.type()).isEqualTo("LOAN");
        assertThat(account.customerId()).isEqualTo(customerId);
        assertThat(account.balance()).isEqualByComparingTo(amount);
    }

    @Then("the loan is denied with message {string}")
    public void loanDenied(String message) {
        assertThat(loan.approved()).isFalse();
        assertThat(loan.message()).isEqualTo(message);
        assertThat(loan.loanAccountId()).isEmpty();
    }

    // ------------------------------------------------------------------ positions

    @When("{int} shares of {string} are bought at {bigdecimal} each from the main account")
    public void buyShares(int shares, String symbol, BigDecimal price) {
        Set<Long> before = api().positions(customerId).stream().map(Position::positionId).collect(Collectors.toSet());
        List<Position> after = api().buyPosition(customerId, mainAccount, "QA Parabank", symbol, shares, price);
        boughtPosition = after.stream()
                .filter(position -> !before.contains(position.positionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No new position after buying " + symbol + ": " + after));
    }

    @Then("the customer holds {int} {string} shares, and the position is found by its id")
    public void holdsShares(int shares, String symbol) {
        assertThat(boughtPosition.symbol()).isEqualTo(symbol);
        assertThat(boughtPosition.shares()).isEqualTo(shares);
        assertThat(api().positions(customerId)).contains(boughtPosition);
        Position byId = api().position(boughtPosition.positionId());
        assertThat(byId.symbol()).isEqualTo(symbol);
        assertThat(byId.shares()).isEqualTo(shares);
        assertThat(byId.purchasePrice()).isEqualByComparingTo(boughtPosition.purchasePrice());
    }

    @Then("the price history of position {long} can be read for the last {int} days")
    public void priceHistory(long positionId, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(api().positionHistory(positionId, today.minusDays(days), today)).isNotNull();
    }

    @When("the {int} {string} shares are sold at {bigdecimal} each")
    public void sellShares(int shares, String symbol, BigDecimal price) {
        api().sellPosition(customerId, mainAccount, boughtPosition.positionId(), shares, price);
    }

    @Then("the customer no longer holds {string} shares")
    public void noLongerHolds(String symbol) {
        assertThat(api().positions(customerId))
                .noneSatisfy(position -> {
                    assertThat(position.positionId()).isEqualTo(boughtPosition.positionId());
                    assertThat(position.shares()).isPositive();
                });
    }

    // ------------------------------------------------------------------ contract

    @When("{string} is called with contract validation")
    public void callWithContract(String operation) {
        ParaBankApi validating = api().validating(CONTRACT);
        switch (operation) {
            case "login" -> validating.login(user.username(), user.password());
            case "customer" -> validating.customer(customerId);
            case "customer accounts" -> validating.accounts(customerId);
            case "account" -> validating.account(mainAccount);
            case "customer positions" -> validating.positions(customerId);
            case "account transactions" -> {
                // make sure there is at least one transaction to validate (net change zero)
                api().deposit(mainAccount, new BigDecimal("0.01"));
                api().withdraw(mainAccount, new BigDecimal("0.01"));
                validating.transactions(mainAccount);
            }
            default -> throw new IllegalArgumentException("Unknown operation in feature file: " + operation);
        }
    }

    @Then("the response matched the contract")
    public void responseMatchedContract() {
        // The validation filter throws inside the When step if anything doesn't match; reaching here means it did.
    }

    // ------------------------------------------------------------------ errors and defects

    @When("the API request {string} is sent")
    public void sendRequest(String request) {
        String method = request.substring(0, request.indexOf(' '));
        String path = request.substring(request.indexOf(' ') + 1)
                .replace("{customerId}", String.valueOf(customerId))
                .replace("{accountId}", String.valueOf(mainAccount))
                .replace("{username}", user.username());
        response = api().raw(method, path);
    }

    @Then("the response status is {int} and the body contains {string}")
    public void statusAndBody(int status, String message) {
        assertThat(response.statusCode()).as("status; body: %s", abbreviate(response.asString())).isEqualTo(status);
        assertThat(response.asString()).contains(message);
    }

    /**
     * Sends a request that ParaBank should reject. Today it succeeds (defect), so any money it moved is moved back
     * immediately, before the assertion fails: the test data stays intact either way.
     */
    @When("^the API customer sends a (withdrawal|transfer) of (balance plus )?(-?[0-9.]+) on the main account$")
    public void sendInvalidMoneyOperation(String operation, String balancePlus, String amountText) {
        BigDecimal amount = new BigDecimal(amountText);
        if (balancePlus != null) {
            amount = mainBalanceAtStart.max(BigDecimal.ZERO).add(amount);
        }
        long otherAccount = api().accountIds(customerId).stream()
                .filter(id -> id != mainAccount).findFirst().orElse(mainAccount);
        String path = "withdrawal".equals(operation)
                ? "/withdraw?accountId=" + mainAccount + "&amount=" + amount.toPlainString()
                : "/transfer?fromAccountId=" + mainAccount + "&toAccountId=" + otherAccount + "&amount=" + amount.toPlainString();
        response = api().raw("POST", path);
        if (response.statusCode() == 200) {
            undo(operation, amount, otherAccount);
        }
    }

    /** Reverses whatever an accepted invalid operation did (amount may be negative). */
    private void undo(String operation, BigDecimal amount, long otherAccount) {
        if ("withdrawal".equals(operation)) {
            if (amount.signum() > 0) {
                api().deposit(mainAccount, amount);              // money left main: put it back
            } else {
                api().withdraw(mainAccount, amount.negate());    // a negative withdrawal added money: take it out
            }
        } else if (otherAccount != mainAccount) {
            if (amount.signum() > 0) {
                api().transfer(otherAccount, mainAccount, amount);
            } else {
                api().transfer(mainAccount, otherAccount, amount.negate()); // negative transfer moved money INTO main
            }
        }
    }

    @Then("the operation was rejected with HTTP {int}")
    public void operationRejected(int status) {
        assertThat(response.statusCode())
                .as("ParaBank accepted it: %s", abbreviate(response.asString()))
                .isEqualTo(status);
    }

    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
