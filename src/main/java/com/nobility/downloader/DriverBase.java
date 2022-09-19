package com.nobility.downloader;

import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.settings.DriverDefaults;
import com.nobility.downloader.utils.StringChecker;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.time.Duration;

public abstract class DriverBase {

    protected Model model;
    private DriverDefaults driverDefaults;
    private final ChromeOptions chromeOptions = new ChromeOptions();
    private final FirefoxOptions firefoxOptions = new FirefoxOptions();
    private final SafariOptions safariOptions = new SafariOptions();
    protected WebDriver driver = null;
    private boolean isSetup = false;
    protected String userAgent;
    private boolean headless = true;

    //needed for series details controller.
    //yes i know its bad, but it lags when launched. we need to load it afterwards.
    protected DriverBase() {}

    protected DriverBase(Model model) {
        this.model = model;
        setupDriver();
    }

    /**
     * Used to set up the web driver.
     * It can be any of the browsers that the user can change in the settings.
     * This should only be called once unless you are debugging.
     */
    protected void setupDriver() {
        if (isSetup) {
            return;
        }
        userAgent = model.getRandomUserAgent();
        driverDefaults = DriverDefaults.driverForName(model.settings().getString(Defaults.DRIVER));

        switch (driverDefaults) {
            case CHROME:
                WebDriverManager.chromedriver().setup();
                break;
            case OPERA:
                WebDriverManager.operadriver().setup();
                break;
            case EDGE:
                WebDriverManager.edgedriver().setup();
                break;
            case CHROMIUM:
                WebDriverManager.chromiumdriver().setup();
                break;
            case FIREFOX:
                WebDriverManager.firefoxdriver().setup();
                break;
            case SAFARI:
                WebDriverManager.safaridriver().setup();
                break;
        }

        if (DriverDefaults.isChromium(driverDefaults)) {
            chromeOptions.setHeadless(headless);
            chromeOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-infobars");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            chromeOptions.addArguments("--disable-browser-side-navigation");
            chromeOptions.addArguments("--disable-gpu");
            chromeOptions.addArguments("enable-automation");
            chromeOptions.addArguments("--mute-audio");
            chromeOptions.addArguments("user-agent=" + userAgent);
            if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
                chromeOptions.addArguments("--proxy-server=" + model.settings().getString(Defaults.PROXY));
            }
            if (driverDefaults == DriverDefaults.EDGE) {
                EdgeOptions edgeOptions = new EdgeOptions();
                edgeOptions.setHeadless(headless);
                edgeOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
                edgeOptions.addArguments("--no-sandbox");
                edgeOptions.addArguments("--disable-infobars");
                edgeOptions.addArguments("--disable-dev-shm-usage");
                edgeOptions.addArguments("--disable-browser-side-navigation");
                edgeOptions.addArguments("--disable-gpu");
                //edgeOptions.addArguments("enable-automation");
                edgeOptions.addArguments("--mute-audio");
                edgeOptions.addArguments("--user-agent=" + userAgent);
                if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
                    edgeOptions.addArguments("--proxy-server=" + model.settings().getString(Defaults.PROXY));
                }
                driver = new EdgeDriver(edgeOptions);
            } else {
                driver = new ChromeDriver(chromeOptions);
            }
        } else if (driverDefaults == DriverDefaults.FIREFOX) {
            firefoxOptions.setHeadless(headless);
            FirefoxProfile profile = new FirefoxProfile();
            profile.setPreference("media.volume_scale", "0.0");
            profile.setPreference("general.useragent.override", userAgent);
            if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
                //not sure if this works
                Proxy proxy = new Proxy();
                proxy.setAutodetect(true);
                proxy.setHttpProxy(model.settings().getString(Defaults.PROXY));
                proxy.setSocksProxy(model.settings().getString(Defaults.PROXY));
                firefoxOptions.setProxy(proxy);
            }
            firefoxOptions.setProfile(profile);
            firefoxOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
            driver = new FirefoxDriver(firefoxOptions);
        } else if (driverDefaults == DriverDefaults.SAFARI) {
            //not sure if user agent is changable
            if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
                Proxy proxy = new Proxy();
                proxy.setAutodetect(true);
                proxy.setHttpProxy(model.settings().getString(Defaults.PROXY));
                proxy.setSocksProxy(model.settings().getString(Defaults.PROXY));
                safariOptions.setProxy(proxy);
            }
            safariOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
            driver = new SafariDriver(safariOptions);
        }
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.TIMEOUT)));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.TIMEOUT)));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.TIMEOUT)));
        model.getRunningDrivers().add(driver);
        isSetup = true;
    }

    /**
     * Used when cloudflare shows the challenge so the user can manually solve it.
     * Currently only used for lihk scraping.
     */
    protected void relaunchDebug() {
        headless = false;
        killDriver();
        driver = null;
        isSetup = false;
        setupDriver();
    }

    protected void killDriver() {
        try {
            if (driver != null) {
                model.getRunningDrivers().remove(driver);
                driver.close();
                driver.quit();
            }
        } catch (Exception ignored) {}
    }
}
