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
