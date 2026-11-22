package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Find Transactions" (findtrans.htm): one account, four ways to search (transaction id, date, date range, amount).
 * The page checks the input in the browser, then calls the REST API and shows the result table. After a search the
 * form is hidden, so every search starts from a freshly opened page (see {@code AccountServicesMenu}).
 */
public final class FindTransactionsPage {

    /** The page expects dates as MM-DD-YYYY. */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    static final String ACCOUNT = "id=accountId";
    static final String TRANSACTION_ID = "id=transactionId";
    static final String ON_DATE = "id=transactionDate";
    static final String FROM_DATE = "id=fromDate";
    static final String TO_DATE = "id=toDate";
    static final String AMOUNT = "id=amount";
    static final String RESULTS = "id=resultContainer";
    static final String SERVER_ERROR = "id=errorContainer";
    static final String ROWS = "css=#transactionBody tr";

    /** One search type: its button and the span where the page writes its validation message. */
    public enum Search {
        BY_ID("findById", "transactionIdError"),
        BY_DATE("findByDate", "transactionDateError"),
        BY_DATE_RANGE("findByDateRange", "dateRangeError"),
        BY_AMOUNT("findByAmount", "amountError");

        private final String buttonId;
        private final String errorId;

        Search(String buttonId, String errorId) {
            this.buttonId = buttonId;
            this.errorId = errorId;
        }
    }

    /** One row of the result table, as shown: date "MM-DD-YYYY", description, and "$12.50" in Debit or Credit. */
    public record Row(String date, String description, String debit, String credit) {
    }

    private final UiDriver ui;
    private final Duration timeout;

    public FindTransactionsPage(UiDriver ui, Duration timeout) {
        this.ui = ui;
        this.timeout = timeout;
    }

    public FindTransactionsPage selectAccount(long accountId) {
        ui.selectByVisibleText(ACCOUNT, String.valueOf(accountId));
        return this;
    }

    public List<Row> byId(long transactionId) {
        ui.type(TRANSACTION_ID, String.valueOf(transactionId));
        return submit(Search.BY_ID);
    }

    public List<Row> onDate(LocalDate date) {
        ui.type(ON_DATE, DATE.format(date));
        return submit(Search.BY_DATE);
    }

    public List<Row> between(LocalDate from, LocalDate to) {
        ui.type(FROM_DATE, DATE.format(from));
        ui.type(TO_DATE, DATE.format(to));
        return submit(Search.BY_DATE_RANGE);
    }

    public List<Row> byAmount(BigDecimal amount) {
        ui.type(AMOUNT, amount.toPlainString());
        return submit(Search.BY_AMOUNT);
    }

    /**
     * Types {@code input} into the field(s) of {@code search}, submits, and returns what the page shows instead of
     * results: its validation message, or empty if the search went through to the server.
     */
    public Optional<String> validationMessage(Search search, String input) {
        switch (search) {
            case BY_ID -> ui.type(TRANSACTION_ID, input);
            case BY_DATE -> ui.type(ON_DATE, input);
            case BY_DATE_RANGE -> {
                ui.type(FROM_DATE, input);
                ui.type(TO_DATE, input);
            }
            case BY_AMOUNT -> ui.type(AMOUNT, input);
        }
        ui.click("id=" + search.buttonId);
        String message = "xpath=//span[@id='" + search.errorId + "' and normalize-space()!='']";
        Optional<String> shown = ui.waitForAnyVisible(timeout, message, RESULTS, SERVER_ERROR);
        return shown.filter(message::equals).map(ui::text);
    }

    private List<Row> submit(Search search) {
        ui.click("id=" + search.buttonId);
        Optional<String> shown = ui.waitForAnyVisible(timeout, RESULTS, SERVER_ERROR,
                "xpath=//span[@id='" + search.errorId + "' and normalize-space()!='']");
        if (shown.isEmpty() || !shown.get().equals(RESULTS)) {
            throw new IllegalStateException("Find Transactions " + search + " showed no results: "
                    + shown.map(found -> found.equals(SERVER_ERROR) ? "server error page" : ui.text(found))
                    .orElse("nothing within " + timeout.toSeconds() + " s"));
        }
        return rows();
    }

    private List<Row> rows() {
        if (!ui.isVisible(ROWS)) {
            return List.of(); // "no transactions found" is a valid answer; texts() would wait for a row
        }
        List<String> dates = ui.texts(ROWS + " td:nth-child(1)");
        List<String> descriptions = ui.texts(ROWS + " td:nth-child(2)");
        List<String> debits = ui.texts(ROWS + " td:nth-child(3)");
        List<String> credits = ui.texts(ROWS + " td:nth-child(4)");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            rows.add(new Row(dates.get(i), descriptions.get(i), debits.get(i), credits.get(i)));
        }
        return rows;
    }
}
