# 05 — Browser, Driver and UI Layer

Package: `automation-core/.../core/driver` and `.../core/ui`

## The layers (bottom to top)
```
Page objects (Plan 02)      LoginPage.login(user)          ← knows ParaBank, not Selenium
        │ uses
UiDriver (interface)        click("name=username")         ← engine-neutral contract
        │ implemented by
SeleniumUiDriver            WebDriverWait + By.name(...)   ← all Selenium details live here
        │ uses
DriverManager (ThreadLocal) one WebDriver per thread
        │ creates via
DriverFactory               ChromeOptions / FirefoxOptions, headless flag
```

## DriverFactory
- `create(CHROME|FIREFOX, headless)`; drivers are downloaded by **Selenium Manager** (built into Selenium 4.6+).
- Chrome: `--headless=new`, 1920×1080, password-manager pop-ups disabled (they block clicks after login).
- Firefox: `-headless`, 1920×1080, "remember password" disabled.

## DriverManager and ThreadLocal
With 3 parallel threads there are 3 browsers. `ThreadLocal<WebDriver>` gives each thread its own slot, so
`DriverManager.get()` always returns *this* thread's browser. `quit()` calls `remove()` — important because
test runners reuse threads; a leftover driver would leak into the next test.

## Locator strings
`id=…`, `name=…`, `css=…`, `xpath=…`, `link=…`. Parsed once by `Locator.parse` with clear error messages.
Strings (not `By`) keep page objects free of Selenium, so Plan 07 can add a `PlaywrightUiDriver`.

## Waiting strategy
Only **explicit waits** (`WebDriverWait`), never implicit waits — mixing both causes unpredictable delays.
Each action waits for the right condition: clickable before click, visible before type/read, "has
options" before using a dropdown (ParaBank fills account lists with JavaScript after page load).

## Browser tests are opt-in
They are in TestNG group `browser`, excluded by default so `mvn test` is fast and works without browsers:
`mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none`
