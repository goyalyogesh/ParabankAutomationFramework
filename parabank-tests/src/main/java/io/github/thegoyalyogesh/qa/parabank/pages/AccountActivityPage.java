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
