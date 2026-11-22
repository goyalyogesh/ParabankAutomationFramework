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
