package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.Transaction;
import io.github.thegoyalyogesh.qa.parabank.model.TransferCase;
import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;
import io.github.thegoyalyogesh.qa.parabank.model.TransferType;
import io.github.thegoyalyogesh.qa.parabank.pages.AccountActivityPage;
import io.github.thegoyalyogesh.qa.parabank.pages.AccountServicesMenu;
import io.github.thegoyalyogesh.qa.parabank.pages.AccountServicesMenu.Link;
import io.github.thegoyalyogesh.qa.parabank.pages.BillPayPage;
import io.github.thegoyalyogesh.qa.parabank.pages.FindTransactionsPage;
import io.github.thegoyalyogesh.qa.parabank.pages.FindTransactionsPage.Row;
import io.github.thegoyalyogesh.qa.parabank.pages.FindTransactionsPage.Search;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;
import io.github.thegoyalyogesh.qa.parabank.pages.OpenAccountPage;
import io.github.thegoyalyogesh.qa.parabank.pages.TransferFundsPage;
import io.github.thegoyalyogesh.qa.parabank.service.TransferLedger;
import io.github.thegoyalyogesh.qa.parabank.service.TransferQueue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Money movement: own-account transfer (UI), Bill Pay to another customer (UI), transfer chain (REST API). */
public class TransferSteps {

    private final TestContext ctx;
    private Scenario scenario;

    private TransferCase transfer;
    private ParaBankUser payer;
    private ParaBankUser payee;
    private long mainAccount;
    private long otherAccount;
    private Set<Long> accountsBeforeOpening;
    private BigDecimal fromBefore;
    private BigDecimal toBefore;
    private String confirmation = "";
    private final Map<String, String> excelValues = new LinkedHashMap<>();

    private List<Row> found = List.of();
    private Transaction transactionUnderTest;
    private Optional<String> findMessage = Optional.empty();

    private List<TransferInstruction> chain;
    private Map<Long, BigDecimal> chainBalancesBefore;
    private Map<String, String> chainConfirmations = Map.of();

    public TransferSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void keepScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    // ---------- shared ----------

    @Given("transfer {string} from the Transfers sheet")
    public void transferFromSheet(String transferId) {
        transfer = ctx.repository().transferCase(transferId);
        scenario.log(transferId + ": " + transfer.description() + " (" + transfer.amount() + ")");
    }

    @Given("the paying customer is logged in")
    public void payingCustomerLoggedIn() {
        customerLoggedIn(transfer.fromUserId());
    }

    // ---------- OWN: open account + transfer ----------

    @When("the customer opens a new {string} account funded from the main account")
    public void openNewAccount(String accountType) {
        accountsBeforeOpening = new HashSet<>(ctx.api().accountIds(customerId(payer)));
        menu().go(Link.OPEN_NEW_ACCOUNT);
        otherAccount = new OpenAccountPage(ctx.ui()).open(accountType, mainAccount);
        scenario.log("New " + accountType + " account: " + otherAccount);
    }

    @Then("exactly one new account appears in the customer's account list")
    public void exactlyOneNewAccount() {
        Set<Long> added = new HashSet<>(ctx.api().accountIds(customerId(payer)));
        added.removeAll(accountsBeforeOpening); // Set difference: after − before
        assertThat(added).as("accounts created by this step").containsExactly(otherAccount);

        menu().go(Link.ACCOUNTS_OVERVIEW);
        assertThat(ctx.overviewPage().waitUntilLoaded().accountIds()).contains(otherAccount);
    }

    @When("the customer transfers the amount from the main account to the new account")
    public void transferToNewAccount() {
        rememberBalancesBefore();
        menu().go(Link.TRANSFER_FUNDS);
        confirmation = new TransferFundsPage(ctx.ui()).transfer(transfer.amount(), mainAccount, otherAccount);
        scenario.log("Confirmation: " + confirmation);
    }

    @Then("the transfer is confirmed on screen")
    public void transferConfirmed() {
        assertThat(confirmation)
                .contains(money(transfer.amount()))
                .contains("#" + mainAccount)
                .contains("#" + otherAccount);
    }

    @Then("both balances change by exactly the amount")
    public void bothBalancesChange() {
        BigDecimal fromAfter = ctx.api().balance(mainAccount);
        BigDecimal toAfter = ctx.api().balance(otherAccount);
        rememberBalancesAfter(fromAfter, toAfter);
        assertThat(fromAfter).as("main account %d", mainAccount).isEqualByComparingTo(fromBefore.subtract(transfer.amount()));
        assertThat(toAfter).as("new account %d", otherAccount).isEqualByComparingTo(toBefore.add(transfer.amount()));
    }

    @Then("the new account's activity shows {string} for the amount")
    public void newAccountActivity(String description) {
        menu().go(Link.ACCOUNTS_OVERVIEW);
        ctx.overviewPage().waitUntilLoaded().openActivity(otherAccount);
        assertThat(new AccountActivityPage(ctx.ui()).transactionDescriptions()).contains(description);

        assertThat(ctx.api().transactions(otherAccount))
                .as("API transactions of %d", otherAccount)
                .anySatisfy(t -> {
                    assertThat(t.type()).isEqualTo("Credit");
                    assertThat(t.description()).isEqualTo(description);
                    assertThat(t.amount()).isEqualByComparingTo(transfer.amount());
                });
    }

    // ---------- BILLPAY: customer A pays customer B ----------

    @When("the customer pays the other customer through Bill Pay")
    public void payOtherCustomer() {
        payee = ctx.registeredUser(transfer.toUserId());
        otherAccount = Long.parseLong(payee.accountNumber());
        rememberBalancesBefore();
        menu().go(Link.BILL_PAY);
        confirmation = new BillPayPage(ctx.ui()).pay(payee, transfer.amount(), mainAccount);
        scenario.log("Confirmation: " + confirmation);
    }

    @When("the paying customer pays the other customer through the REST Bill Pay endpoint")
    public void payOtherCustomerThroughApi() {
        payer = ctx.registeredUser(transfer.fromUserId());
        payee = ctx.registeredUser(transfer.toUserId());
        mainAccount = Long.parseLong(payer.accountNumber());
        otherAccount = Long.parseLong(payee.accountNumber());
        rememberBalancesBefore();
        confirmation = ctx.api().billPay(mainAccount, payee, transfer.amount());
        scenario.log("API response: " + confirmation);
    }

    @Then("the payment to the other customer is confirmed on screen")
    public void paymentConfirmed() {
        assertThat(confirmation)
                .contains("Bill Payment to " + payee.fullName())
                .contains(money(transfer.amount()))
                .contains(String.valueOf(mainAccount));
    }

    @Then("the payer's balance goes down by exactly the amount")
    public void payerBalanceDown() {
        BigDecimal fromAfter = ctx.api().balance(mainAccount);
        BigDecimal toAfter = ctx.api().balance(otherAccount);
        rememberBalancesAfter(fromAfter, toAfter);
        assertThat(fromAfter).isEqualByComparingTo(fromBefore.subtract(transfer.amount()));
    }

    @Then("the payee's balance does not change, because Bill Pay treats the payee as an external company")
    public void payeeBalanceUnchanged() {
        assertThat(ctx.api().balance(otherAccount)).isEqualByComparingTo(toBefore);
    }

    @Then("the payer's transactions include the bill payment")
    public void payerTransactionsIncludeBillPayment() {
        List<Transaction> transactions = ctx.api().transactions(mainAccount);
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.type()).isEqualTo("Debit");
            assertThat(t.description()).isEqualTo("Bill Payment to " + payee.fullName());
            assertThat(t.amount()).isEqualByComparingTo(transfer.amount());
        });
    }

    // ---------- CHAIN: REST API, different customers, strict order ----------

    @Given("the CHAIN transfers from the Transfers sheet are queued in sheet order")
    public void queueChain() {
        chain = ctx.repository().transferCases(TransferType.CHAIN).stream()
                .map(t -> new TransferInstruction(t.transferId(), accountOf(t.fromUserId()), accountOf(t.toUserId()), t.amount()))
                .toList();
        assertThat(chain).as("CHAIN rows in the Transfers sheet").isNotEmpty();
        chainBalancesBefore = balances(TransferLedger.expectedNetChange(chain).keySet());
        scenario.log("Queue: " + chain.stream().map(TransferInstruction::transferId).toList()
                + " | balances before: " + chainBalancesBefore);
    }

    @When("the queue is processed through the REST API")
    public void processQueue() {
        TransferQueue queue = new TransferQueue();
        chain.forEach(queue::add);
        chainConfirmations = queue.runAll(ctx.api()::transfer);
        chainConfirmations.forEach((id, text) -> scenario.log(id + ": " + text));
    }

    @Then("every account's balance changed by its expected net amount")
    public void netChangeMatches() {
        Map<Long, BigDecimal> expected = TransferLedger.expectedNetChange(chain);
        Map<Long, BigDecimal> after = balances(expected.keySet());
        expected.forEach((account, delta) -> assertThat(after.get(account).subtract(chainBalancesBefore.get(account)))
                .as("net change of account %d", account)
                .isEqualByComparingTo(delta));
    }

    // ---------- FIND TRANSACTIONS: the transfer just made must be found by every search ----------

    @Given("customer {string} is logged in")
    public void customerLoggedIn(String userId) {
        payer = ctx.registeredUser(userId);
        mainAccount = Long.parseLong(payer.accountNumber());
        LoginPage.Outcome outcome = ctx.loginPage().open().login(payer.username(), payer.password());
        assertThat(outcome).as("login of %s", userId).isEqualTo(LoginPage.Outcome.LOGGED_IN);
        ctx.overviewPage().waitUntilLoaded();
    }

    @Then("searching the new account by amount finds exactly the transfer, as a credit")
    public void findNewAccountByAmount() {
        List<Row> rows = findTransactions(otherAccount).byAmount(transfer.amount());
        scenario.log("By amount " + transfer.amount() + " on " + otherAccount + ": " + rows);
        assertThat(rows).as("the new account has exactly one transaction of this amount").singleElement()
                .satisfies(row -> {
                    assertThat(row.description()).isEqualTo("Funds Transfer Received");
                    assertThat(row.credit()).isEqualTo("$" + money(transfer.amount()));
                    assertThat(row.debit()).isEmpty();
                });
    }

    @Then("searching the main account by the transfer's transaction id finds it, as a debit")
    public void findMainAccountById() {
        Transaction sent = sentTransfer();
        List<Row> rows = findTransactions(mainAccount).byId(sent.id());
        scenario.log("By id " + sent.id() + ": " + rows);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.description()).isEqualTo("Funds Transfer Sent");
            assertThat(row.debit()).isEqualTo("$" + money(transfer.amount()));
            assertThat(row.credit()).isEmpty();
        });
    }

    @Then("searching the new account by its booking date, and by a date range around it, finds the transfer")
    public void findNewAccountByDates() {
        LocalDate booked = receivedTransfer().date();
        scenario.log("Booking date from the API: " + booked);
        assertThat(findTransactions(otherAccount).onDate(booked))
                .as("search on %s", booked).anySatisfy(this::isReceivedTransfer);
        assertThat(findTransactions(otherAccount).between(booked.minusDays(1), booked.plusDays(1)))
                .as("search from %s to %s", booked.minusDays(1), booked.plusDays(1)).anySatisfy(this::isReceivedTransfer);
    }

    @Then("Find Transactions refuses malformed input with a message:")
    public void findRejectsMalformedInput(DataTable cases) {
        for (Map<String, String> c : cases.asMaps()) {
            Search search = Search.valueOf(c.get("search"));
            Optional<String> message = findTransactions(mainAccount).validationMessage(search, c.get("input"));
            assertThat(message).as("%s with \"%s\"", search, c.get("input")).contains(c.get("message"));
        }
    }

    @When("the customer searches the main account by the date {string}")
    public void searchByRawDate(String date) {
        findMessage = findTransactions(mainAccount).validationMessage(Search.BY_DATE, date);
        scenario.log("Message shown: " + findMessage.orElse("(none - the search ran and returned results)"));
    }

    @Then("Find Transactions says {string}")
    public void findSays(String message) {
        assertThat(findMessage).as("message for an impossible date").contains(message);
    }

    @When("the customer deposits {string} through the API and looks that transaction up by its id")
    public void depositAndLookUpById(String amount) {
        ctx.api().deposit(mainAccount, new BigDecimal(amount));
        Transaction latest = ctx.api().transactions(mainAccount).stream()
                .max(Comparator.comparingLong(Transaction::id))
                .orElseThrow(() -> new AssertionError("API shows no transaction on " + mainAccount + " after a deposit"));
        transactionUnderTest = latest;
        found = findTransactions(mainAccount).byId(latest.id());
        scenario.log("API: " + latest + " | page: " + found);
    }

    @Then("the date shown is the booking date from the API")
    public void dateShownIsBookingDate() {
        assertThat(found).singleElement().extracting(Row::date)
                .isEqualTo(FindTransactionsPage.DATE.format(transactionUnderTest.date()));
    }

    // ---------- Excel write-back (runs for passed and failed scenarios) ----------

    @After(value = "@ownTransfer or @findTransfer or @billPay or @apiBillPay or @apiTransfer", order = 50)
    public void recordInExcel(Scenario finished) {
        String result = finished.isFailed() ? "FAIL" : "PASS";
        if (transfer != null) {
            excelValues.put("Result", result);
            excelValues.put("FromAccount", String.valueOf(mainAccount));
            excelValues.put("ToAccount", otherAccount == 0 ? "" : String.valueOf(otherAccount));
            excelValues.put("Confirmation", confirmation);
            excelValues.put("Browser", finished.getSourceTagNames().contains("@api") ? "REST API" : ctx.config().runLabel());
            ctx.repository().recordTransfer(transfer.transferId(), excelValues);
        }
        if (chain != null) {
            for (TransferInstruction t : chain) {
                Map<String, String> values = new LinkedHashMap<>();
                values.put("Result", chainConfirmations.containsKey(t.transferId()) ? result : "NOT RUN");
                values.put("FromAccount", String.valueOf(t.fromAccount()));
                values.put("ToAccount", String.valueOf(t.toAccount()));
                values.put("Confirmation", chainConfirmations.getOrDefault(t.transferId(), ""));
                values.put("Browser", "REST API");
                ctx.repository().recordTransfer(t.transferId(), values);
            }
        }
    }

    // ---------- helpers ----------

    private FindTransactionsPage findTransactions(long account) {
        menu().go(Link.FIND_TRANSACTIONS);
        return new FindTransactionsPage(ctx.ui(), ctx.config().timeout()).selectAccount(account);
    }

    /** The debit this scenario created on the main account: newest "Funds Transfer Sent" of the amount. */
    private Transaction sentTransfer() {
        return ctx.api().transactionsByAmount(mainAccount, transfer.amount()).stream()
                .filter(t -> t.type().equals("Debit") && t.description().equals("Funds Transfer Sent"))
                .max(Comparator.comparingLong(Transaction::id))
                .orElseThrow(() -> new AssertionError("API shows no transfer of " + transfer.amount() + " from " + mainAccount));
    }

    /** The credit on the new account (created by this scenario, so it is the only one of this amount). */
    private Transaction receivedTransfer() {
        return ctx.api().transactionsByAmount(otherAccount, transfer.amount()).stream()
                .filter(t -> t.type().equals("Credit")).findFirst()
                .orElseThrow(() -> new AssertionError("API shows no transfer of " + transfer.amount() + " into " + otherAccount));
    }

    private void isReceivedTransfer(Row row) {
        assertThat(row.description()).isEqualTo("Funds Transfer Received");
        assertThat(row.credit()).isEqualTo("$" + money(transfer.amount()));
    }

    private AccountServicesMenu menu() {
        return new AccountServicesMenu(ctx.ui(), ctx.config().timeout());
    }

    private void rememberBalancesBefore() {
        fromBefore = ctx.api().balance(mainAccount);
        toBefore = ctx.api().balance(otherAccount);
        excelValues.put("FromBalanceBefore", fromBefore.toPlainString());
        excelValues.put("ToBalanceBefore", toBefore.toPlainString());
    }

    private void rememberBalancesAfter(BigDecimal fromAfter, BigDecimal toAfter) {
        excelValues.put("FromBalanceAfter", fromAfter.toPlainString());
        excelValues.put("ToBalanceAfter", toAfter.toPlainString());
    }

    private long accountOf(String userId) {
        return Long.parseLong(ctx.registeredUser(userId).accountNumber());
    }

    private Map<Long, BigDecimal> balances(Set<Long> accounts) {
        return accounts.stream().collect(Collectors.toMap(Function.identity(), ctx.api()::balance));
    }

    private static long customerId(ParaBankUser user) {
        return Long.parseLong(user.customerId());
    }

    /** "25" → "25.00", the way ParaBank prints amounts. */
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
