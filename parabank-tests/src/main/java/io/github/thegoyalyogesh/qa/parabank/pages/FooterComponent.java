package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

/** Footer links shown on every ParaBank page. */
public final class FooterComponent {

    private final UiDriver ui;

    public FooterComponent(UiDriver ui) {
        this.ui = ui;
    }

    public String linkUrl(String linkText) {
        return ui.attribute("xpath=//div[@id='footerPanel']//a[normalize-space()='" + linkText + "']", "href");
    }
}
