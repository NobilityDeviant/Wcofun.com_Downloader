package com.nobility.downloader;

import com.nobility.downloader.scraper.settings.Defaults;
import com.nobility.downloader.utils.StringChecker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

public class SeriesDetailsController implements Initializable {

    private final Model model;
    private final ChromeOptions options = new ChromeOptions();
    private WebDriver driver = null;
    private final String seriesLink;
    private final Stage stage;
    @FXML private ImageView image;
    @FXML private Label title;
    @FXML private TextArea desc;

    public SeriesDetailsController(Model model, Stage stage, String seriesLink) {
        this.model = model;
        this.stage = stage;
        this.seriesLink = seriesLink;
        setupDriver();
    }

    private void setupDriver() {
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
        driver.manage().timeouts().implicitlyWait(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        driver.manage().timeouts().pageLoadTimeout(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        driver.manage().timeouts().setScriptTimeout(model.settings()
                .getInteger(Defaults.PROXYTIMEOUT), TimeUnit.SECONDS);
        model.getRunningDrivers().add(driver);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void loadSeries() {
        try {
            driver.get(seriesLink);
            Document doc = Jsoup.parse(driver.getPageSource());
            Elements image = doc.getElementsByClass("img5");
            if (!image.isEmpty()) {
                try {
                    String outer = image.get(0).outerHtml();
                    String key1 = "src=\"//";
                    String key2 = "\" alt=\"\">";
                    String url = "https://" + outer.substring(outer.indexOf(key1) + key1.length(), outer.indexOf(key2));
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setRequestProperty("user-agent", model.getRandomUserAgent());
                    con.setReadTimeout(10_000);
                    con.setConnectTimeout(10_000);
                    con.connect();
                    this.image.setImage(new Image(con.getInputStream()));
                } catch (Exception e1) {
                    System.err.println("Failed to load series image for: " + seriesLink
                            + "\nError: " + e1.getLocalizedMessage());
                }
            }
            Elements description = doc.getElementsByTag("p");
            if (!description.isEmpty()) {
                desc.setText(description.get(0).text());
            }
            Elements title = doc.getElementsByClass("h1-tag");
            if (!title.isEmpty()) {
                String text = title.get(0).text();
                this.title.setText(text);
                stage.setTitle(text);
            }
        } catch (Exception e) {
            System.err.println("Failed to check series details for: " + seriesLink + " Error: " + e.getLocalizedMessage());
        } finally {
            if (driver != null) {
                model.getRunningDrivers().remove(driver);
                driver.close();
                driver.quit();
            }
        }
    }

    @FXML
    private void visitUrl() {
        model.showLinkPrompt(seriesLink, true);
    }

    @FXML
    private void copyUrl() {
        model.showCopyPrompt(seriesLink, false);
    }
}
