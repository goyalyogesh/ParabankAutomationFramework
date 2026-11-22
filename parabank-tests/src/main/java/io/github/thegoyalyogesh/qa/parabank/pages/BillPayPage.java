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
