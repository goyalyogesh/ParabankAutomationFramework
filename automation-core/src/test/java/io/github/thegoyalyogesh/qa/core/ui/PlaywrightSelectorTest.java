package io.github.thegoyalyogesh.qa.core.ui;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Our {@code strategy=value} locators translated to Playwright selectors (no browser needed). */
public class PlaywrightSelectorTest {

    private static String selector(String raw) {
        return PlaywrightUiDriver.selector(Locator.parse(raw));
    }

    @Test
    public void idAndNameBecomeAttributeSelectorsSoDotsAreNotClasses() {
        assertThat(selector("id=customer.firstName")).isEqualTo("css=[id=\"customer.firstName\"]");
        assertThat(selector("name=username")).isEqualTo("css=[name=\"username\"]");
    }

    @Test
    public void cssAndXpathPassThrough() {
        assertThat(selector("css=#rightPanel h1")).isEqualTo("css=#rightPanel h1");
        assertThat(selector("xpath=//input[@value='Log In']")).isEqualTo("xpath=//input[@value='Log In']");
    }

    @Test
    public void linkTextIsAnExactTextMatchOnAnchors() {
        assertThat(selector("link=Log Out")).isEqualTo("css=a:text-is(\"Log Out\")");
    }

    @Test
    public void quotesAndBackslashesAreEscaped() {
        assertThat(selector("id=a\"b")).isEqualTo("css=[id=\"a\\\"b\"]");
        assertThat(selector("link=Say \"hi\"")).isEqualTo("css=a:text-is(\"Say \\\"hi\\\"\")");
        assertThat(selector("name=a\\b")).isEqualTo("css=[name=\"a\\\\b\"]");
    }
}
