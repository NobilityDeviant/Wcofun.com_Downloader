package com.nobility.downloader.series;

import com.nobility.downloader.DriverBase;
import com.nobility.downloader.Model;
import com.nobility.downloader.utils.Toast;
import javafx.application.Platform;
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

public class SeriesDetailsController extends DriverBase implements Initializable {

    private final String seriesLink;
    private final Stage stage;
    @FXML
    private ImageView image;
    @FXML
    private Label title;
    @FXML
    private TextArea desc;

    public SeriesDetailsController(Model model, Stage stage, String seriesLink) {
        super(model);
        this.stage = stage;
        this.seriesLink = seriesLink;
        stage.setOnCloseRequest(event -> {
            killDriver();
            stage.close();
        });
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Toast.makeToast(stage, "Loading series...");
        loadSeries();
    }

    public void loadSeries() {
        new Thread(() -> {
            try {
                driver.get(seriesLink);
                Document doc = Jsoup.parse(driver.getPageSource());
                Elements loaded = doc.getElementsByClass("recent-release");
                if (loaded.text().toLowerCase(Locale.ROOT).contains("page not found")) {
                    throw new Exception("Series doesn't exist.");
                }
                Elements image = doc.getElementsByClass("img5");
                if (!image.isEmpty()) {
                    try {
                        String outer = image.get(0).outerHtml();
                        String key1 = "src=\"//";
                        String key2 = "\" alt=\"\">";
                        String url = "https://" + outer.substring(outer.indexOf(key1) + key1.length(), outer.indexOf(key2));
                        System.err.println(url);
                        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                        con.setRequestProperty("user-agent", model.getRandomUserAgent());
                        con.setReadTimeout(10_000);
                        con.setConnectTimeout(10_000);
                        con.connect();
                        Platform.runLater(() -> {
                            try {
                                this.image.setImage(new Image(con.getInputStream()));
                            } catch (IOException e) {
                                System.err.println("Failed to load series image for: " + seriesLink
                                        + "\nError: " + e.getLocalizedMessage());
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("Failed to load series image for: " + seriesLink
                                + "\nError: " + e.getLocalizedMessage());
                    }
                }
                Elements description = doc.getElementsByTag("p");
                if (!description.isEmpty()) {
                    Platform.runLater(() -> desc.setText(description.get(0).text()));
                }
                Elements title = doc.getElementsByClass("h1-tag");
                if (!title.isEmpty()) {
                    Platform.runLater(() -> {
                        String text = title.get(0).text();
                        this.title.setText(text);
                        stage.setTitle(text);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to check series details for: " + seriesLink + " Error: " + e.getLocalizedMessage());
                Platform.runLater(() ->
                        Toast.makeToast(stage, "Failed to load series. Error: " + e.getLocalizedMessage()));
            } finally {
                killDriver();
            }
        }).start();
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
