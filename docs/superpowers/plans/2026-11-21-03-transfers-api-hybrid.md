# Plan 03 — Transfers (UI + Rest Assured hybrid) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** End-to-end money movement from Excel: a customer opens a savings account and transfers money into it (UI),
customer A pays customer B through Bill Pay (UI), and a chain of transfers between different customers runs in order
through the REST API. Every balance is verified through the API with `BigDecimal`, and results are written back to Excel.

**Architecture:** `automation-core` gains Actions API helpers (`hover`, `scrollTo`), `texts`, `waitForNonBlankText`,
and `ExcelUtil.sheetNames/readHeaders`. `parabank-tests` gains money models, API calls (`account`, `transactions`,
`transfer`), four page objects, `TransferQueue` (ArrayDeque) and `TransferLedger` (Streams). A third TestNG block
runs `TransferRunner` after login. The git-tracked workbook becomes a template; tests write to a local copy.

**Tech Stack:** as Plan 02. No new dependencies.

**Spec items:** F7 (own-account transfer), F8 (A → B), F11 (ArrayDeque, Set, Streams), F12 (Actions API), E6 (Rest Assured hybrid).

**Verified site facts (2026-11-21, logged in as a test customer):**
- Left panel links: "Open New Account", "Accounts Overview", "Transfer Funds", "Bill Pay".
- Open account: `select#type` (CHECKING/SAVINGS), `select#fromAccountId` (filled by JS), button `Open New Account`,
  result `a#newAccountId`. "A minimum of $90.00 must be deposited" from the chosen account.
- Transfer: `input#amount`, `select#fromAccountId`, `select#toAccountId`, button `Transfer`; result `#showResult h1`
  "Transfer Complete!", `p` "$25.00 has been transferred from account #X to account #Y."
- Bill pay: `name=payee.name|payee.address.street|city|state|zipCode|payee.phoneNumber|payee.accountNumber`,
  `name=verifyAccount`, `name=amount`, `select name=fromAccountId`, button `Send Payment`; result `#billpayResult h1`
  "Bill Payment Complete", `p` "Bill Payment to <name> in the amount of $X from account N was successful."
- Overview: `#accountTable tbody a` = account links (`activity.htm?id=N`). Activity: `#transactionTable`, column 2 = description.
- REST: `GET /accounts/{id}` → `{"id","customerId","type","balance"}`; `GET /accounts/{id}/transactions` →
  `[{"id","accountId","type":"Credit|Debit","amount","description"}]`; `POST /transfer?fromAccountId&toAccountId&amount`
  → "Successfully transferred $5 from account #A to account #B".
- **Bill Pay debits the payer but does not credit the payee account** (payee = external company).
- **Finding:** `POST /transfer` moves money between accounts of *different* customers with no authentication.

**Parallel safety:** every scenario uses its own customers (OWN: U01–U03, BILLPAY: U04→U05, U06→U07, CHAIN: U08–U10),
so balance checks can't be disturbed by another thread.

---

## File map

| File | Change |
|---|---|
| `CORE/.../core/ui/UiDriver.java`, `SeleniumUiDriver.java` | + `hover`, `scrollTo`, `texts`, `waitForNonBlankText` |
| `CORE/.../core/excel/ExcelUtil.java` | + `sheetNames`, `readHeaders` |
| `PB/main/.../model/{TransferType,TransferCase,Account,Transaction,TransferInstruction}.java` | new |
| `PB/main/.../api/ParaBankApi.java` | + `accounts`, `account`, `balance`, `transactions`, `transfer` |
| `PB/main/.../data/TestDataRepository.java` | + Transfers sheet, `fromTemplate` |
| `PB/main/.../pages/{AccountServicesMenu,OpenAccountPage,TransferFundsPage,BillPayPage,AccountActivityPage}.java` | new |
| `PB/main/.../pages/AccountsOverviewPage.java` | + `accountIds`, `openActivity` |
| `PB/main/.../service/{TransferQueue,TransferLedger}.java` | new |
| `PB/test/.../context/TestContext.java`, `hooks/Hooks.java`, `steps/LoginSteps.java` | template copy, `registeredUser`, `not @api` browser hook |
| `PB/test/.../steps/TransferSteps.java`, `runners/TransferRunner.java`, `features/transfers.feature` | new |
| `PB/test/.../tools/CreateTestDataWorkbook.java` | + Transfers sheet; adds missing sheets to an existing file |
| `testdata/ParaBankData-template.xlsx` | renamed from `ParaBankData.xlsx`; working copy is git-ignored |

---

### Task 1: Actions API and text helpers in UiDriver

- [ ] **Step 1: Failing browser tests.** In `SeleniumUiDriverBrowserTest`, add to `PAGE` before `<script>`:

```html
            <div id="hoverTarget" onmouseover="this.textContent='hovered'">hover me</div>
            <ul><li class="item">One</li><li class="item"> Two </li></ul>
            <div id="lateText"></div>
            <div style="height:3000px"></div>
            <div id="far">far away</div>
```

and inside the existing `setTimeout` function: `document.getElementById('lateText').textContent = 'filled';`.
Add tests (import `org.openqa.selenium.JavascriptExecutor`):

```java
    @Test
    public void hoverFiresMouseOver() {
        ui.hover("id=hoverTarget");
        assertThat(ui.text("id=hoverTarget")).isEqualTo("hovered");
    }

    @Test
    public void scrollToMovesThePage() {
        ui.scrollTo("id=far");
        Number scrollY = (Number) ((JavascriptExecutor) DriverManager.get()).executeScript("return window.scrollY");
        assertThat(scrollY.longValue()).isPositive();
    }

    @Test
    public void textsReturnsAllMatchesTrimmed() {
        assertThat(ui.texts("css=li.item")).containsExactly("One", "Two");
    }

    @Test
    public void waitForNonBlankTextWaitsForJavascript() {
        assertThat(ui.waitForNonBlankText("id=lateText")).isEqualTo("filled");
    }
```

- [ ] **Step 2:** Run `mvn -B -pl automation-core test -Dtest=SeleniumUiDriverBrowserTest -Dgroups=browser -Dcore.excludedGroups=none` → `cannot find symbol: method hover`.

- [ ] **Step 3: `UiDriver` additions**

```java
    /** Moves the mouse over the element (Selenium Actions API). */
    void hover(String locator);

    /** Scrolls the page until the element is in view (Selenium 4 Actions {@code scrollToElement}). */
    void scrollTo(String locator);

    /** Trimmed texts of all elements matching the locator; waits until at least one exists. */
    List<String> texts(String locator);

    /** Waits until the element has non-blank text (e.g. filled by JavaScript) and returns it. */
    String waitForNonBlankText(String locator);
```

- [ ] **Step 4: `SeleniumUiDriver` implementation** (import `org.openqa.selenium.interactions.Actions`)

```java
    @Override
    public void hover(String locator) {
        new Actions(driver).moveToElement(visible(locator)).perform();
    }

    @Override
    public void scrollTo(String locator) {
        By by = by(locator);
        WebElement element = wait.until(d -> d.findElement(by));
        new Actions(driver).scrollToElement(element).perform();
    }

    @Override
    public List<String> texts(String locator) {
        By by = by(locator);
        List<WebElement> elements = wait.until(d -> {
            List<WebElement> found = d.findElements(by);
            return found.isEmpty() ? null : found;
        });
        return elements.stream().map(WebElement::getText).map(String::trim).toList();
    }

    @Override
    public String waitForNonBlankText(String locator) {
        By by = by(locator);
        return wait.until(d -> {
            String text = d.findElement(by).getText().trim();
            return text.isEmpty() ? null : text;
        });
    }
```

- [ ] **Step 5:** Same command → `Tests run: 13, Failures: 0`. Commit `feat(core): Actions API hover/scroll, texts and waitForNonBlankText`.

---

### Task 2: ExcelUtil sheet names and headers

- [ ] **Step 1: Failing test `CORE/test/.../core/excel/ExcelUtilSheetInfoTest.java`**

```java
package io.github.thegoyalyogesh.qa.core.excel;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilSheetInfoTest {

    @Test
    public void listsSheetNamesInWorkbookOrderAndHeadersInColumnOrder() throws IOException {
        Path file = Files.createTempDirectory("info").resolve("w.xlsx");
        ExcelUtil.writeSheet(file, "Users", List.of("Id", "Name", "Empty"), List.of(Map.of("Id", "1", "Name", "Ann")));
        ExcelUtil.writeSheet(file, "Cases", List.of("CaseId"), List.of());

        assertThat(ExcelUtil.sheetNames(file)).containsExactly("Users", "Cases");
        assertThat(ExcelUtil.readHeaders(file, "Users")).containsExactly("Id", "Name", "Empty");
        assertThat(ExcelUtil.readHeaders(file, "Cases")).containsExactly("CaseId");
    }

    @Test
    public void unknownSheetGivesClearError() throws IOException {
        Path file = Files.createTempDirectory("info").resolve("w.xlsx");
        ExcelUtil.writeSheet(file, "Users", List.of("Id"), List.of());
        assertThatThrownBy(() -> ExcelUtil.readHeaders(file, "Nope")).hasMessageContaining("Nope");
    }
}
```

- [ ] **Step 2:** `mvn -B -pl automation-core test -Dtest=ExcelUtilSheetInfoTest` → `cannot find symbol: method sheetNames`.

- [ ] **Step 3: Add to `ExcelUtil`** (reuse the existing private helpers `open`, `requireSheet`, `headers` and the same exception style as `readSheet`):

```java
    /** Sheet names in workbook order. */
    public static List<String> sheetNames(Path file) {
        try (Workbook workbook = open(file)) {
            return IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }

    /** Header row of a sheet, in column order (also works for a sheet without data rows). */
    public static List<String> readHeaders(Path file, String sheetName) {
        try (Workbook workbook = open(file)) {
            return headers(requireSheet(workbook, sheetName, file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }
```

- [ ] **Step 4:** Run → PASS; `mvn -B -pl automation-core -am install` (all core tests). Commit `feat(core): ExcelUtil.sheetNames and readHeaders`.

---

### Task 3: Money models, ledger and ArrayDeque transfer queue

- [ ] **Step 1: Failing test `PB/test/.../parabank/unit/TransferQueueTest.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;
import io.github.thegoyalyogesh.qa.parabank.service.TransferLedger;
import io.github.thegoyalyogesh.qa.parabank.service.TransferQueue;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransferQueueTest {

    private static final List<TransferInstruction> CHAIN = List.of(
            new TransferInstruction("C01", 8, 9, new BigDecimal("20")),
            new TransferInstruction("C02", 9, 10, new BigDecimal("15.50")),
            new TransferInstruction("C03", 10, 8, new BigDecimal("5")));

    @Test
    public void runsTransfersInFifoOrderAndEmptiesTheQueue() {
        TransferQueue queue = new TransferQueue();
        CHAIN.forEach(queue::add);
        List<String> calls = new ArrayList<>();

        Map<String, String> confirmations = queue.runAll((from, to, amount) -> {
            calls.add(from + "->" + to);
            return "ok " + amount;
        });

        assertThat(calls).containsExactly("8->9", "9->10", "10->8");
        assertThat(confirmations.keySet()).containsExactly("C01", "C02", "C03");
        assertThat(confirmations.get("C02")).isEqualTo("ok 15.50");
        assertThat(queue.size()).isZero();
    }

    @Test
    public void failureStopsTheQueueAndKeepsTheRestPending() {
        TransferQueue queue = new TransferQueue();
        CHAIN.forEach(queue::add);

        assertThatThrownBy(() -> queue.runAll((from, to, amount) -> {
            if (from == 9) {
                throw new IllegalStateException("bank down");
            }
            return "ok";
        })).hasMessageContaining("bank down");

        assertThat(queue.remaining()).extracting(TransferInstruction::transferId).containsExactly("C03");
    }

    @Test
    public void ledgerNetsOutEveryAccount() {
        Map<Long, BigDecimal> net = TransferLedger.expectedNetChange(CHAIN);

        assertThat(net.keySet()).containsExactly(8L, 9L, 10L);
        assertThat(net.get(8L)).isEqualByComparingTo("-15");
        assertThat(net.get(9L)).isEqualByComparingTo("4.50");
        assertThat(net.get(10L)).isEqualByComparingTo("10.50");
        assertThat(net.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
    }
}
```

- [ ] **Step 2:** `mvn -B -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml` → compile failure.

- [ ] **Step 3: Models** (`PB/main/.../parabank/model/`)

```java
package io.github.thegoyalyogesh.qa.parabank.model;

/** Kind of row in the Transfers sheet. */
public enum TransferType {
    /** UI: open a savings account, then move money into it. */
    OWN,
    /** UI: pay another customer's account through Bill Pay. */
    BILLPAY,
    /** REST API: one link of a chain of transfers between different customers, run in sheet order. */
    CHAIN
}
```

```java
package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** One row of the Transfers sheet. {@code toUserId} is blank for OWN transfers. */
public record TransferCase(String transferId, TransferType type, String fromUserId, String toUserId,
                           BigDecimal amount, String description) {
}
```

```java
package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** A bank account as returned by the REST API. Money is BigDecimal, never double. */
public record Account(long id, long customerId, String type, BigDecimal balance) {
}
```

```java
package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** One account transaction from the REST API; {@code type} is "Credit" or "Debit". */
public record Transaction(long id, long accountId, String type, BigDecimal amount, String description) {
}
```

```java
package io.github.thegoyalyogesh.qa.parabank.model;

import java.math.BigDecimal;

/** A transfer with resolved account numbers, ready to execute. */
public record TransferInstruction(String transferId, long fromAccount, long toAccount, BigDecimal amount) {
}
```

- [ ] **Step 4: `PB/main/.../parabank/service/TransferQueue.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes transfers strictly first-in, first-out.
 * <p>
 * {@link ArrayDeque}: O(1) add at the tail and remove at the head, no synchronisation overhead (one scenario thread
 * owns the queue). Unlike {@code LinkedList} it allocates no node per element, and unlike {@code Stack} it is not
 * synchronised. If a transfer fails, the queue stops and the transfers not yet started stay in {@link #remaining()}.
 */
public final class TransferQueue {

    /** Something that can move money; {@code ParaBankApi::transfer} fits. */
    @FunctionalInterface
    public interface Gateway {
        String transfer(long fromAccount, long toAccount, BigDecimal amount);
    }

    private final Deque<TransferInstruction> pending = new ArrayDeque<>();

    public TransferQueue add(TransferInstruction transfer) {
        pending.offerLast(transfer);
        return this;
    }

    public int size() {
        return pending.size();
    }

    public List<TransferInstruction> remaining() {
        return List.copyOf(pending);
    }

    /** Runs every queued transfer in order. Returns confirmations by transfer id, in execution order. */
    public Map<String, String> runAll(Gateway gateway) {
        Map<String, String> confirmations = new LinkedHashMap<>();
        TransferInstruction next;
        while ((next = pending.pollFirst()) != null) {
            confirmations.put(next.transferId(), gateway.transfer(next.fromAccount(), next.toAccount(), next.amount()));
        }
        return confirmations;
    }
}
```

- [ ] **Step 5: `PB/main/.../parabank/service/TransferLedger.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.parabank.model.TransferInstruction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Expected balance effects of a set of transfers, computed with Streams. */
public final class TransferLedger {

    private TransferLedger() {
    }

    /**
     * Net change per account if every transfer succeeds: each transfer is split into (sender, -amount) and
     * (receiver, +amount), then grouped by account and summed. Sorted by account number (TreeMap).
     */
    public static Map<Long, BigDecimal> expectedNetChange(Collection<TransferInstruction> transfers) {
        return transfers.stream()
                .flatMap(t -> Stream.of(
                        Map.entry(t.fromAccount(), t.amount().negate()),
                        Map.entry(t.toAccount(), t.amount())))
                .collect(Collectors.groupingBy(Map.Entry::getKey, TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Map.Entry::getValue, BigDecimal::add)));
    }
}
```

- [ ] **Step 6:** Unit suite → all pass (11 + 3 = 14). Commit `feat(parabank): money models, Streams ledger and ArrayDeque transfer queue`.

---

### Task 4: Transfers sheet, template workbook, API money calls

- [ ] **Step 1: Extend `TestDataRepositoryTest`** — in `setUp()` add after the LoginNegative sheet:

```java
        ExcelUtil.writeSheet(file, TestDataRepository.TRANSFERS,
                List.of("TransferId", "Type", "FromUser", "ToUser", "Amount", "Description", "Result", "Confirmation", "ExecutedAt"),
                List.of(Map.of("TransferId", "T01", "Type", "OWN", "FromUser", "U01", "Amount", "25", "Description", "to savings"),
                        Map.of("TransferId", "C01", "Type", "chain", "FromUser", "U01", "ToUser", "U02", "Amount", "12.5"),
                        Map.of("TransferId", "C02", "Type", "CHAIN", "FromUser", "U02", "ToUser", "U01", "Amount", "2")));
```

and tests (imports `TransferCase`, `TransferType`, `java.math.BigDecimal`):

```java
    @Test
    public void readsTransferCasesByTypeInSheetOrder() {
        assertThat(repository.transferCases(TransferType.CHAIN)).extracting(TransferCase::transferId)
                .containsExactly("C01", "C02");
        TransferCase own = repository.transferCase("T01");
        assertThat(own.type()).isEqualTo(TransferType.OWN);
        assertThat(own.amount()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(own.toUserId()).isEmpty();
    }

    @Test
    public void recordTransferWritesResultAndTimestamp() {
        repository.recordTransfer("C02", Map.of("Result", "PASS", "Confirmation", "done"));
        Map<String, String> row = ExcelUtil.readSheet(file, TestDataRepository.TRANSFERS).get(2);
        assertThat(row).containsEntry("Result", "PASS").containsEntry("Confirmation", "done");
        assertThat(row.get("ExecutedAt")).isNotBlank();
    }

    @Test
    public void fromTemplateCopiesOnFirstUseAndAddsSheetsMissingFromAnOlderCopy() throws IOException {
        Path dir = Files.createTempDirectory("tpl");
        Path working = dir.resolve("work.xlsx");
        TestDataRepository.fromTemplate(working, file);
        assertThat(ExcelUtil.sheetNames(working)).containsExactlyElementsOf(ExcelUtil.sheetNames(file));

        Path older = dir.resolve("older.xlsx");
        ExcelUtil.writeSheet(older, TestDataRepository.USERS, List.of("UserId", "Username"),
                List.of(Map.of("UserId", "U01", "Username", "keep_me")));
        TestDataRepository.fromTemplate(older, file);
        assertThat(ExcelUtil.sheetNames(older)).contains(TestDataRepository.TRANSFERS, TestDataRepository.LOGIN_NEGATIVE);
        assertThat(ExcelUtil.readSheet(older, TestDataRepository.USERS).get(0)).containsEntry("Username", "keep_me");
    }
```

Run unit suite → compile failure (`TRANSFERS`).

- [ ] **Step 2: Add to `TestDataRepository`** (imports `TransferCase`, `TransferType`, `java.math.BigDecimal`, `java.io.IOException`, `java.io.UncheckedIOException`, `java.nio.file.Files`, `java.util.Locale`):

```java
    public static final String TRANSFERS = "Transfers";

    /**
     * Repository on a working copy of the template. The template (tracked in git) is never written to.
     * First use copies it; an existing copy from an older version gets any sheet it is missing, and keeps its data.
     */
    public static TestDataRepository fromTemplate(Path workingFile, Path template) {
        try {
            if (Files.notExists(workingFile)) {
                Path parent = workingFile.toAbsolutePath().getParent();
                Files.createDirectories(parent);
                Files.copy(template, workingFile);
            } else {
                List<String> present = ExcelUtil.sheetNames(workingFile);
                ExcelUtil.sheetNames(template).stream()
                        .filter(sheet -> !present.contains(sheet))
                        .forEach(sheet -> ExcelUtil.writeSheet(workingFile, sheet,
                                ExcelUtil.readHeaders(template, sheet), ExcelUtil.readSheet(template, sheet)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + workingFile + " from " + template, e);
        }
        return new TestDataRepository(workingFile);
    }

    public List<TransferCase> transferCases(TransferType type) {
        return ExcelUtil.readSheet(file, TRANSFERS).stream()
                .map(TestDataRepository::toTransferCase)
                .filter(transfer -> transfer.type() == type)
                .toList();
    }

    public TransferCase transferCase(String transferId) {
        return ExcelUtil.readSheet(file, TRANSFERS).stream()
                .filter(row -> transferId.equals(row.get("TransferId")))
                .map(TestDataRepository::toTransferCase)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No transfer '" + transferId + "' in sheet " + TRANSFERS + " of " + file.toAbsolutePath()));
    }

    private static TransferCase toTransferCase(Map<String, String> row) {
        return new TransferCase(
                row.get("TransferId"),
                TransferType.valueOf(row.get("Type").trim().toUpperCase(Locale.ROOT)),
                row.getOrDefault("FromUser", "").trim(),
                row.getOrDefault("ToUser", "").trim(),
                new BigDecimal(row.get("Amount").trim()),
                row.getOrDefault("Description", ""));
    }

    public void recordTransfer(String transferId, Map<String, String> values) {
        Map<String, String> all = new LinkedHashMap<>(values);
        all.put("ExecutedAt", now());
        ExcelUtil.updateRow(file, TRANSFERS, "TransferId", transferId, all);
    }
```

- [ ] **Step 3: Add to `ParaBankApi`** (imports `Account`, `Transaction`, `io.restassured.path.json.config.JsonPathConfig`, `java.math.BigDecimal`, `java.util.Map`); replace `accountIds`:

```java
    /** Parse JSON numbers as BigDecimal so money keeps exact cents. */
    private static final JsonPathConfig MONEY = new JsonPathConfig(JsonPathConfig.NumberReturnType.BIG_DECIMAL);

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
```

- [ ] **Step 4: Template workbook.** `git mv testdata/ParaBankData.xlsx testdata/ParaBankData-template.xlsx`; add
`testdata/ParaBankData.xlsx` to `.gitignore`; in `config.properties` add
`testdata.template=../testdata/ParaBankData-template.xlsx`; in `ParaBankConfig` add:

```java
    public Path testDataTemplate() {
        return Path.of(config.get("testdata.template"));
    }
```

In `TestContext` replace the repository line with:

```java
    private static final TestDataRepository REPOSITORY =
            TestDataRepository.fromTemplate(CONFIG.testDataFile(), CONFIG.testDataTemplate());
```

- [ ] **Step 5: `CreateTestDataWorkbook`** — default path `testdata/ParaBankData-template.xlsx`; existing files get only
missing sheets. Replace `main` and add the Transfers data:

```java
    static final List<String> TRANSFER_HEADERS = List.of("TransferId", "Type", "FromUser", "ToUser", "Amount",
            "Description", "Result", "FromAccount", "ToAccount", "FromBalanceBefore", "FromBalanceAfter",
            "ToBalanceBefore", "ToBalanceAfter", "Confirmation", "ExecutedAt", "Browser");

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "testdata/ParaBankData-template.xlsx");
        if (Files.exists(file) && Boolean.getBoolean("overwrite")) {
            Files.delete(file);
        }
        List<String> present = Files.exists(file) ? ExcelUtil.sheetNames(file) : List.of();
        writeIfMissing(file, present, TestDataRepository.USERS, USER_HEADERS, users());
        writeIfMissing(file, present, TestDataRepository.LOGIN_NEGATIVE, NEGATIVE_HEADERS, negativeCases());
        writeIfMissing(file, present, TestDataRepository.TRANSFERS, TRANSFER_HEADERS, transfers());
        System.out.println("Workbook ready: " + file.toAbsolutePath() + " " + ExcelUtil.sheetNames(file));
    }

    private static void writeIfMissing(Path file, List<String> present, String sheet, List<String> headers,
                                       List<Map<String, String>> rows) {
        if (present.contains(sheet)) {
            System.out.println("Keeping existing sheet " + sheet);
        } else {
            ExcelUtil.writeSheet(file, sheet, headers, rows);
        }
    }

    /** Each scenario uses its own customers, so parallel threads never touch the same account. */
    static List<Map<String, String>> transfers() {
        return List.of(
                transfer("T01", "OWN", "U01", "", "25", "Move money into a new savings account"),
                transfer("T02", "OWN", "U02", "", "40.50", "Move money into a new savings account"),
                transfer("T03", "OWN", "U03", "", "10", "Move money into a new savings account"),
                transfer("B01", "BILLPAY", "U04", "U05", "30", "Pay another customer through Bill Pay"),
                transfer("B02", "BILLPAY", "U06", "U07", "12.75", "Pay another customer through Bill Pay"),
                transfer("C01", "CHAIN", "U08", "U09", "20", "API chain step 1"),
                transfer("C02", "CHAIN", "U09", "U10", "15", "API chain step 2"),
                transfer("C03", "CHAIN", "U10", "U08", "5", "API chain step 3"));
    }

    private static Map<String, String> transfer(String id, String type, String from, String to, String amount,
                                                String description) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("TransferId", id);
        row.put("Type", type);
        row.put("FromUser", from);
        row.put("ToUser", to);
        row.put("Amount", amount);
        row.put("Description", description);
        return row;
    }
```

Run the tool (command in its Javadoc, `-Dexec.args=testdata/ParaBankData-template.xlsx`) → "Keeping existing sheet Users",
"Keeping existing sheet LoginNegative", sheets `[Users, LoginNegative, Transfers]`.

- [ ] **Step 6:** Unit suite → 17 pass. Commit `feat(parabank): Transfers sheet, template workbook with working copy, API money calls`.

---

### Task 5: Page objects

- [ ] **Step 1: `AccountServicesMenu.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

/** "Account Services" links in the left panel. Hovers first (Actions API), then clicks, like a real user. */
public final class AccountServicesMenu {

    private final UiDriver ui;

    public AccountServicesMenu(UiDriver ui) {
        this.ui = ui;
    }

    public void go(String linkText) {
        String link = "xpath=//div[@id='leftPanel']//a[normalize-space()='" + linkText + "']";
        ui.hover(link);
        ui.click(link);
    }
}
```

- [ ] **Step 2: `OpenAccountPage.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

/** "Open New Account": choose a type and the account that pays the $90 minimum deposit. */
public final class OpenAccountPage {

    static final String TYPE = "id=type";
    static final String FROM_ACCOUNT = "id=fromAccountId";
    static final String OPEN_BUTTON = "css=input[value='Open New Account']";
    static final String NEW_ACCOUNT = "id=newAccountId";

    private final UiDriver ui;

    public OpenAccountPage(UiDriver ui) {
        this.ui = ui;
    }

    /** Returns the new account number shown on the confirmation. */
    public long open(String accountType, long fundingAccount) {
        ui.selectByVisibleText(TYPE, accountType);
        ui.selectByVisibleText(FROM_ACCOUNT, String.valueOf(fundingAccount));
        ui.click(OPEN_BUTTON);
        return Long.parseLong(ui.waitForNonBlankText(NEW_ACCOUNT));
    }
}
```

- [ ] **Step 3: `TransferFundsPage.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.math.BigDecimal;

/** "Transfer Funds" between the logged-in customer's own accounts. */
public final class TransferFundsPage {

    static final String AMOUNT = "id=amount";
    static final String FROM_ACCOUNT = "id=fromAccountId";
    static final String TO_ACCOUNT = "id=toAccountId";
    static final String TRANSFER_BUTTON = "css=input[value='Transfer']";
    static final String RESULT_TITLE = "xpath=//div[@id='showResult']/h1";
    static final String RESULT_TEXT = "xpath=//div[@id='showResult']/p[1]";

    private final UiDriver ui;

    public TransferFundsPage(UiDriver ui) {
        this.ui = ui;
    }

    /** Returns the confirmation sentence, e.g. "$25.00 has been transferred from account #1 to account #2." */
    public String transfer(BigDecimal amount, long fromAccount, long toAccount) {
        ui.selectByVisibleText(FROM_ACCOUNT, String.valueOf(fromAccount));
        ui.selectByVisibleText(TO_ACCOUNT, String.valueOf(toAccount));
        ui.type(AMOUNT, amount.toPlainString());
        ui.click(TRANSFER_BUTTON);
        ui.waitForText(RESULT_TITLE, "Transfer Complete!");
        return ui.text(RESULT_TEXT);
    }
}
```

- [ ] **Step 4: `BillPayPage.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;

import java.math.BigDecimal;

/** "Bill Pay": the payee's details come from the other customer's Excel row. */
public final class BillPayPage {

    static final String SEND_BUTTON = "css=input[value='Send Payment']";
    static final String RESULT_TITLE = "xpath=//div[@id='billpayResult']/h1";
    static final String RESULT_TEXT = "xpath=//div[@id='billpayResult']/p[1]";

    private final UiDriver ui;

    public BillPayPage(UiDriver ui) {
        this.ui = ui;
    }

    /** Returns e.g. "Bill Payment to Priya Patel in the amount of $30.00 from account 27219 was successful." */
    public String pay(ParaBankUser payee, BigDecimal amount, long fromAccount) {
        ui.type("name=payee.name", payee.fullName());
        ui.type("name=payee.address.street", payee.street());
        ui.type("name=payee.address.city", payee.city());
        ui.type("name=payee.address.state", payee.state());
        ui.type("name=payee.address.zipCode", payee.zipCode());
        ui.type("name=payee.phoneNumber", payee.phone());
        ui.type("name=payee.accountNumber", payee.accountNumber());
        ui.type("name=verifyAccount", payee.accountNumber());
        ui.type("name=amount", amount.toPlainString());
        ui.selectByVisibleText("name=fromAccountId", String.valueOf(fromAccount));
        ui.click(SEND_BUTTON);
        ui.waitForText(RESULT_TITLE, "Bill Payment Complete");
        return ui.text(RESULT_TEXT);
    }
}
```

- [ ] **Step 5: `AccountActivityPage.java`** and additions to `AccountsOverviewPage`

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.util.List;

/** "Account Activity" (activity.htm): transactions of one account. */
public final class AccountActivityPage {

    static final String DESCRIPTIONS = "css=#transactionTable tbody td:nth-child(2)";

    private final UiDriver ui;

    public AccountActivityPage(UiDriver ui) {
        this.ui = ui;
    }

    public List<String> transactionDescriptions() {
        return ui.texts(DESCRIPTIONS);
    }
}
```

`AccountsOverviewPage` (import `java.util.List`):

```java
    static final String ACCOUNT_LINKS = "css=#accountTable tbody a";

    /** Account numbers in the overview table (filled by JavaScript). */
    public List<Long> accountIds() {
        return ui.texts(ACCOUNT_LINKS).stream().map(Long::parseLong).toList();
    }

    /** Scrolls to the account's link (Actions API) and opens its activity page. */
    public void openActivity(long accountId) {
        String link = "link=" + accountId;
        ui.scrollTo(link);
        ui.click(link);
    }
```

- [ ] **Step 6:** `mvn -B -pl parabank-tests test-compile` → exit 0. Commit `feat(parabank): page objects for open account, transfer, bill pay and activity`.

---

### Task 6: Transfer steps, feature, runner and suites

- [ ] **Step 1: `TestContext` — shared lookup of a registered customer** (import `UserRegistry`, AssertJ `assertThat`):

```java
    /** Customer from this run's registry, or from Excel when a suite runs on its own; must be registered. */
    public ParaBankUser registeredUser(String userId) {
        ParaBankUser user = UserRegistry.find(userId).orElseGet(() -> REPOSITORY.user(userId));
        if (!user.isRegistered()) {
            throw new IllegalStateException("Customer " + userId + " must be registered first (run the registration suite)");
        }
        return user;
    }
```

In `LoginSteps.registeredCustomer` replace the body with `ctx.setCurrentUser(ctx.registeredUser(userId));`.

- [ ] **Step 2: `Hooks` — no browser for `@api` scenarios.** Change `@Before(order = 0)` to
`@Before(value = "not @api", order = 0)`. (`DriverManager.quit()` already ignores a missing driver.)

- [ ] **Step 3: `PB/test/.../parabank/steps/TransferSteps.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.steps;

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
import io.github.thegoyalyogesh.qa.parabank.pages.BillPayPage;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;
import io.github.thegoyalyogesh.qa.parabank.pages.OpenAccountPage;
import io.github.thegoyalyogesh.qa.parabank.pages.TransferFundsPage;
import io.github.thegoyalyogesh.qa.parabank.service.TransferLedger;
import io.github.thegoyalyogesh.qa.parabank.service.TransferQueue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        payer = ctx.registeredUser(transfer.fromUserId());
        mainAccount = Long.parseLong(payer.accountNumber());
        LoginPage.Outcome outcome = ctx.loginPage().open().login(payer.username(), payer.password());
        assertThat(outcome).as("login of %s", payer.userId()).isEqualTo(LoginPage.Outcome.LOGGED_IN);
        ctx.overviewPage().waitUntilLoaded();
    }

    // ---------- OWN: open account + transfer ----------

    @When("the customer opens a new {string} account funded from the main account")
    public void openNewAccount(String accountType) {
        accountsBeforeOpening = new HashSet<>(ctx.api().accountIds(customerId(payer)));
        menu().go("Open New Account");
        otherAccount = new OpenAccountPage(ctx.ui()).open(accountType, mainAccount);
        scenario.log("New " + accountType + " account: " + otherAccount);
    }

    @Then("exactly one new account appears in the customer's account list")
    public void exactlyOneNewAccount() {
        Set<Long> added = new HashSet<>(ctx.api().accountIds(customerId(payer)));
        added.removeAll(accountsBeforeOpening); // Set difference: after − before
        assertThat(added).as("accounts created by this step").containsExactly(otherAccount);

        menu().go("Accounts Overview");
        assertThat(ctx.overviewPage().waitUntilLoaded().accountIds()).contains(otherAccount);
    }

    @When("the customer transfers the amount from the main account to the new account")
    public void transferToNewAccount() {
        rememberBalancesBefore();
        menu().go("Transfer Funds");
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
        menu().go("Accounts Overview");
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
        menu().go("Bill Pay");
        confirmation = new BillPayPage(ctx.ui()).pay(payee, transfer.amount(), mainAccount);
        scenario.log("Confirmation: " + confirmation);
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

    // ---------- Excel write-back (runs for passed and failed scenarios) ----------

    @After(value = "@ownTransfer or @billPay or @apiTransfer", order = 50)
    public void recordInExcel(Scenario finished) {
        String result = finished.isFailed() ? "FAIL" : "PASS";
        if (transfer != null) {
            excelValues.put("Result", result);
            excelValues.put("FromAccount", String.valueOf(mainAccount));
            excelValues.put("ToAccount", otherAccount == 0 ? "" : String.valueOf(otherAccount));
            excelValues.put("Confirmation", confirmation);
            excelValues.put("Browser", ctx.config().browser().name());
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

    private AccountServicesMenu menu() {
        return new AccountServicesMenu(ctx.ui());
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
```

- [ ] **Step 4: `features/transfers.feature`**

```gherkin
@transfers
Feature: Money transfers
  Transfers come from testdata/ParaBankData.xlsx, sheet "Transfers"; results are written back to the same rows.
  Every balance is checked through the REST API with exact decimal amounts.
  Each scenario uses its own customers, so scenarios can run in parallel without touching the same account.

  @ownTransfer
  Scenario Outline: <transferId> - customer moves money into a new savings account
    Given transfer "<transferId>" from the Transfers sheet
    And the paying customer is logged in
    When the customer opens a new "SAVINGS" account funded from the main account
    Then exactly one new account appears in the customer's account list
    When the customer transfers the amount from the main account to the new account
    Then the transfer is confirmed on screen
    And both balances change by exactly the amount
    And the new account's activity shows "Funds Transfer Received" for the amount

    Examples:
      | transferId |
      | T01        |
      | T02        |
      | T03        |

  @billPay
  Scenario Outline: <transferId> - customer pays another customer through Bill Pay
    Given transfer "<transferId>" from the Transfers sheet
    And the paying customer is logged in
    When the customer pays the other customer through Bill Pay
    Then the payment to the other customer is confirmed on screen
    And the payer's balance goes down by exactly the amount
    And the payee's balance does not change, because Bill Pay treats the payee as an external company
    And the payer's transactions include the bill payment

    Examples:
      | transferId |
      | B01        |
      | B02        |

  @apiTransfer @api
  Scenario: Chain of transfers between different customers through the REST API, in order
    Given the CHAIN transfers from the Transfers sheet are queued in sheet order
    When the queue is processed through the REST API
    Then every account's balance changed by its expected net amount
```

- [ ] **Step 5: `TransferRunner.java`** (same as `LoginRunner` with `tags = "@transfers"` and plugin files
`transfers.html/json`, `rerun:target/rerun/transfers.txt`, same Extent path). Suites: add to `testng-parabank.xml`

```xml
    <test name="3 - Transfers">
        <classes>
            <class name="io.github.thegoyalyogesh.qa.parabank.runners.TransferRunner"/>
        </classes>
    </test>
```

and create `testng-transfers.xml` with only that block.

- [ ] **Step 6: Live runs.** `-DsuiteXmlFile=testng-transfers.xml -Dheadless=true` → 6/6 pass; then the full suite on
Chrome and Firefox → 34 scenarios, 31 pass, 3 `@knownDefect`. Transfers sheet rows show Result, accounts, balances,
confirmation. Commit `feat(parabank): own-account, bill pay and API chain transfers with balance checks`.

---

### Task 7: Documentation and push
- [ ] Guide `09-transfers-and-api-hybrid.md`; decisions D32+; Q&A (ArrayDeque, Set difference, BigDecimal, Actions API,
  hybrid testing, data partitioning for parallel runs, the `/transfer` security finding); README; BUILD_LOG.
- [ ] Commit, push, republish the docs viewer.

---

## Execution notes (what changed while building, 2026-11-21)

- **Rate limit:** the first full run got this machine blocked by Cloudflare. Added `ApiClient.send` (retries 429,
  honours `Retry-After`), `ApiClient.Sleeper`, a 4-argument constructor, `ApiClientRetryTest`; `SiteGuard` now matches
  the browser's "Error 1015 / rate limited" page and waits 30/60/90 s with reloads; `AccountServicesMenu` takes a
  timeout and an enum `Link` and waits for the target page title.
- **UI Bill Pay defect:** B01/B02 tagged `@knownDefect`. Added B03 (REST Bill Pay, `ParaBankApi.billPay`, Gson 2.13.1
  declared), `@apiBillPay @api` scenario, `ApiRunner` (sequential) as TestNG block 4; `TransferRunner` tags
  `@transfers and not @api`.
- `TestDataRepository.fromTemplate` also adds template **rows** missing from the working copy (matched by first column).
- Final: 35 scenarios, 30 pass, 5 `@knownDefect` failures, on Chrome and Firefox.
