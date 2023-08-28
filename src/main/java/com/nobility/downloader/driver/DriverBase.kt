package com.nobility.downloader.driver

import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.Proxy
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.edge.EdgeDriver
import org.openqa.selenium.edge.EdgeOptions
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile
import org.openqa.selenium.safari.SafariDriver
import org.openqa.selenium.safari.SafariOptions
import java.time.Duration


abstract class DriverBase {

    protected lateinit var model: Model
    private val chromeOptions = ChromeOptions()
    private val firefoxOptions = FirefoxOptions()
    private val safariOptions = SafariOptions()
    private var _driver: WebDriver? = null
    protected val driver get() = _driver!!
    private var isSetup = false
    protected var userAgent: String? = null
    private var headless = true

    //needed for series details controller.
    protected constructor()

    protected constructor(model: Model) {
        this.model = model
        setupDriver()
    }

    /**
     * Used to set up the web driver.
     * It can be any of the browsers that the user can change in the settings.
     * This should only be called once unless you are debugging.
     */
    protected fun setupDriver() {
        if (isSetup) {
            return
        }
        userAgent = model.randomUserAgent
        val driverDefaults = DriverDefaults.driverForName(model.settings().stringSetting(Defaults.DRIVER))
        when (driverDefaults) {
            DriverDefaults.CHROME -> WebDriverManager.chromedriver().setup()
            DriverDefaults.OPERA -> WebDriverManager.operadriver().setup()
            DriverDefaults.EDGE -> WebDriverManager.edgedriver().setup()
            DriverDefaults.CHROMIUM -> WebDriverManager.chromiumdriver().setup()
            DriverDefaults.FIREFOX -> WebDriverManager.firefoxdriver().setup()
            DriverDefaults.SAFARI -> WebDriverManager.safaridriver().setup()
        }
        if (DriverDefaults.isChromium(driverDefaults)) {
            chromeOptions.setHeadless(headless)
            chromeOptions.setPageLoadStrategy(PageLoadStrategy.EAGER)
            chromeOptions.addArguments("--no-sandbox")
            chromeOptions.addArguments("--disable-infobars")
            chromeOptions.addArguments("--disable-dev-shm-usage")
            chromeOptions.addArguments("--disable-browser-side-navigation")
            chromeOptions.addArguments("--disable-gpu")
            chromeOptions.addArguments("--mute-audio")
            chromeOptions.addArguments("user-agent=$userAgent")
            if (model.settings().stringSetting(Defaults.PROXY).isNotEmpty()
                && model.settings().booleanSetting(Defaults.ENABLEPROXY)) {
                chromeOptions.addArguments(
                    "--proxy-server=" + model.settings().stringSetting(Defaults.PROXY)
                )
            }
            _driver = if (driverDefaults == DriverDefaults.EDGE) {
                val edgeOptions = EdgeOptions()
                edgeOptions.setHeadless(headless)
                edgeOptions.setPageLoadStrategy(PageLoadStrategy.EAGER)
                edgeOptions.addArguments("--no-sandbox")
                edgeOptions.addArguments("--disable-infobars")
                edgeOptions.addArguments("--disable-dev-shm-usage")
                edgeOptions.addArguments("--disable-browser-side-navigation")
                edgeOptions.addArguments("--disable-gpu")
                edgeOptions.addArguments("--mute-audio")
                edgeOptions.addArguments("--user-agent=$userAgent")
                if (model.settings().stringSetting(Defaults.PROXY).isNotEmpty()
                    && model.settings().booleanSetting(Defaults.ENABLEPROXY)) {
                    edgeOptions.addArguments(
                        "--proxy-server=" + model.settings().stringSetting(Defaults.PROXY)
                    )
                }
                EdgeDriver(edgeOptions)
            } else {
                ChromeDriver(chromeOptions)
            }
        } else if (driverDefaults == DriverDefaults.FIREFOX) {
            firefoxOptions.setHeadless(headless)
            val profile = FirefoxProfile()
            profile.setPreference("media.volume_scale", "0.0")
            profile.setPreference("general.useragent.override", userAgent)
            if (model.settings().stringSetting(Defaults.PROXY).isNotEmpty()
                && model.settings().booleanSetting(Defaults.ENABLEPROXY)) {
                //not sure if this works
                val proxy = Proxy()
                proxy.isAutodetect = true
                proxy.httpProxy = model.settings().stringSetting(Defaults.PROXY)
                proxy.socksProxy = model.settings().stringSetting(Defaults.PROXY)
                firefoxOptions.setProxy(proxy)
            }
            firefoxOptions.profile = profile
            firefoxOptions.setPageLoadStrategy(PageLoadStrategy.EAGER)
            _driver = FirefoxDriver(firefoxOptions)
        } else if (driverDefaults == DriverDefaults.SAFARI) {
            //not sure if user agent is changable
            if (model.settings().stringSetting(Defaults.PROXY).isNotEmpty()
                && model.settings().booleanSetting(Defaults.ENABLEPROXY)) {
                val proxy = Proxy()
                proxy.isAutodetect = true
                proxy.httpProxy = model.settings().stringSetting(Defaults.PROXY)
                proxy.socksProxy = model.settings().stringSetting(Defaults.PROXY)
                safariOptions.setProxy(proxy)
            }
            safariOptions.setPageLoadStrategy(PageLoadStrategy.EAGER)
            _driver = SafariDriver(safariOptions)
        }
        driver.manage().timeouts().implicitlyWait(
            Duration.ofSeconds(
                model.settings()
                    .integerSetting(Defaults.TIMEOUT).toLong()
            )
        )
        driver.manage().timeouts().pageLoadTimeout(
            Duration.ofSeconds(
                model.settings()
                    .integerSetting(Defaults.TIMEOUT).toLong()
            )
        )
        driver.manage().timeouts().scriptTimeout(
            Duration.ofSeconds(
                model.settings()
                    .integerSetting(Defaults.TIMEOUT).toLong()
            )
        )
        model.runningDrivers.add(driver)
        isSetup = true
    }

    /**
     * Used when cloudflare shows the challenge so the user can manually solve it.
     * Currently unused for now.
     */
    @Suppress("UNUSED")
    protected fun relaunchDebug() {
        headless = false
        killDriver()
        _driver = null
        isSetup = false
        setupDriver()
    }

    fun killDriver() {
        try {
            if (_driver != null) {
                model.runningDrivers.remove(driver)
                driver.quit()
            }
        } catch (ignored: Exception) {
        }
    }
}