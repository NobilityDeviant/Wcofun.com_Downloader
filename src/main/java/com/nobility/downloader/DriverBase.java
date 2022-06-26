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

    protected DriverBase(Model model) {
        this.model = model;
        setupDriver();
    }

    protected void setupDriver() {
        if (isSetup) {
            return;
        }
        options.setHeadless(true);
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments("--no-sandbox"); //https://stackoverflow.com/a/50725918/1689770
        options.addArguments("--disable-infobars"); //https://stackoverflow.com/a/43840128/1689770
        options.addArguments("--disable-dev-shm-usage"); //https://stackoverflow.com/a/50725918/1689770
        options.addArguments("--disable-browser-side-navigation"); //https://stackoverflow.com/a/49123152/1689770
        options.addArguments("--disable-gpu");
        options.addArguments("enable-automation");
        options.addArguments("--mute-audio");
        options.addArguments("user-agent=" + model.getRandomUserAgent());
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
