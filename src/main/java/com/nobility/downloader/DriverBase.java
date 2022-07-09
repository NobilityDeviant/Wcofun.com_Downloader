package com.nobility.downloader;

import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.StringChecker;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

public abstract class DriverBase {

    protected final Model model;
    private final ChromeOptions options = new ChromeOptions();
    protected WebDriver driver = null;
    private boolean isSetup = false;
    protected String userAgent;

    protected DriverBase(Model model) {
        this.model = model;
        setupDriver();
    }

    private void setupDriver() {
        if (isSetup) {
            return;
        }
        userAgent = model.getRandomUserAgent();
        options.setHeadless(true);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-browser-side-navigation");
        options.addArguments("--disable-gpu");
        options.addArguments("enable-automation");
        options.addArguments("--mute-audio");
        options.addArguments("user-agent=" + userAgent);
        if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.PROXY))) {
            options.addArguments("--proxy-server=" + model.settings().getString(Defaults.PROXY));
        }
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT)));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT)));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT)));
        model.getRunningDrivers().add(driver);
        isSetup = true;
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
