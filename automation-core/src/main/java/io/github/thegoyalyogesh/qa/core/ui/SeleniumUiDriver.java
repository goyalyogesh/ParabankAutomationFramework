package io.github.thegoyalyogesh.qa.core.ui;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** {@link UiDriver} backed by Selenium WebDriver with explicit waits (no implicit waits anywhere). */
public class SeleniumUiDriver implements UiDriver {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public SeleniumUiDriver(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, timeout);
    }

    @Override
    public void open(String url) {
        driver.get(url);
    }

    @Override
    public void click(String locator) {
        wait.until(ExpectedConditions.elementToBeClickable(by(locator))).click();
    }

    @Override
    public void type(String locator, String text) {
        WebElement element = visible(locator);
        element.clear();
        element.sendKeys(text);
    }

    @Override
    public String text(String locator) {
        return visible(locator).getText().trim();
    }

    @Override
    public boolean isVisible(String locator) {
        try {
            return driver.findElements(by(locator)).stream().anyMatch(WebElement::isDisplayed);
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    @Override
    public void selectByVisibleText(String locator, String optionText) {
        waitForOptions(locator);
        new Select(visible(locator)).selectByVisibleText(optionText);
    }

    @Override
    public String selectedOption(String locator) {
        return new Select(visible(locator)).getFirstSelectedOption().getText().trim();
    }

    @Override
    public List<String> options(String locator) {
        waitForOptions(locator);
        return new Select(visible(locator)).getOptions().stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    @Override
    public void waitForText(String locator, String expected) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(by(locator), expected));
    }

    @Override
    public String title() {
        return driver.getTitle();
    }

    @Override
    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    @Override
    public byte[] screenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    static By toBy(Locator locator) {
        return switch (locator.strategy()) {
            case ID -> By.id(locator.value());
            case NAME -> By.name(locator.value());
            case CSS -> By.cssSelector(locator.value());
            case XPATH -> By.xpath(locator.value());
            case LINK_TEXT -> By.linkText(locator.value());
        };
    }

    private By by(String locator) {
        return toBy(Locator.parse(locator));
    }

    private WebElement visible(String locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by(locator)));
    }

    private void waitForOptions(String locator) {
        By by = by(locator);
        wait.until(d -> !new Select(d.findElement(by)).getOptions().isEmpty());
    }
}
