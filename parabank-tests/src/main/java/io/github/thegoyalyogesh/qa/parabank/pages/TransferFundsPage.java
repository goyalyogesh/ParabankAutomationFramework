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
