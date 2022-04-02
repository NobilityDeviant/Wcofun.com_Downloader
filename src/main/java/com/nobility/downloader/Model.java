package com.nobility.downloader;

import com.nobility.downloader.downloads.Download;
import com.nobility.downloader.downloads.DownloadSave;
import com.nobility.downloader.history.HistorySave;
import com.nobility.downloader.scraper.BuddyHandler;
import com.nobility.downloader.scraper.Episode;
import com.nobility.downloader.scraper.settings.Defaults;
import com.nobility.downloader.scraper.settings.JsonManager;
import com.nobility.downloader.scraper.settings.Settings;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.TextOutput;
import com.nobility.downloader.utils.Toast;
import io.github.bonigarcia.wdm.WebDriverManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.*;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.openqa.selenium.WebDriver;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class Model {

    public static final String WEBSITE = "https://www.wcofun.com/";
    public static final String EXAMPLE_SERIES = WEBSITE + "anime/ive-been-killing-slimes-for-300-years-and-maxed-out-my-level";
    public static final String EXAMPLE_SHOW = WEBSITE + "ive-been-killing-slimes-for-300-years-and-maxed-out-my-level-episode-1-english-dubbed";
    public static final boolean DEBUG_MODE = false;
    public static final String FX_PATH = "/fx/";
    public static final String IMAGE_PATH = "/images/";
    public static final String CSS_PATH = "/css/";
    public static final String DIALOG_PATH = CSS_PATH + "dialog.css";
    public static final String SETTINGS_ICON = IMAGE_PATH + "icon.png";
    public static final String MAIN_ICON = IMAGE_PATH + "icon.png";

    private final BuddyHandler reader;
    private Stage mainStage;
    private Settings settings;
    private HistorySave historySave;
    private DownloadSave downloadSave;
    private List<String> userAgents;
    private boolean running = false;
    private final List<Episode> links = Collections.synchronizedList(new ArrayList<>());
    private boolean clientUpdating = false;
    private volatile int linksFound;
    private volatile int downloadsFinishedForSession;
    private final Desktop desktop = Desktop.getDesktop();
    private TableView<Download> tableView;
    private final List<WebDriver> runningDrivers = Collections.synchronizedList(new ArrayList<>());

    @FXML private TextField tf_sub_url;
    @FXML private Button stopButton, startButton;
    @FXML private ProgressBar updateProgressBar;
    @FXML private Label updateFileProgress;

    public Model() {
        reader = new BuddyHandler(this);
        downloadSave = JsonManager.loadDownloads();
        if (downloadSave == null) {
            downloadSave = new DownloadSave();
            saveDownloads();
        }
        for (int i = 0; i < downloadSave.getDownloads().size(); i++) {
            downloadSave.getDownloads().get(i).updateSerializedProgress();
        }
        saveDownloads();
        historySave = JsonManager.loadHistory();
        if (historySave == null) {
            historySave = new HistorySave();
            saveSeriesHistory();
        }
        settings = JsonManager.loadSettings();
        if (settings == null) {
            settings = new Settings();
            settings.loadDefaultSettings();
            saveSettings();
        }
        settings.checkForNewSettings();
        saveSettings();
        try {
            userAgents = Files.readAllLines(new File("./resources/ua.txt").toPath());
        } catch (Exception e) {
            System.out.println("Unable to load /resources/ua.txt (UserAgents) defaulting to one user agent.");
        }
        WebDriverManager.chromedriver().setup();
        if (settings.getBoolean(Defaults.SILENTDRIVER) && !DEBUG_MODE) {
            System.setProperty("webdriver.chrome.silentOutput", "true");
            Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF);
        }
    }

    private Stage detailsStage;

    public void openVideoDetails(String link) {
        Platform.runLater(() -> {
            if (StringChecker.isNullOrEmpty(link)) {
                Toast.makeToast(mainStage, "This episode doesn't have a series link.");
                return;
            }
            if (detailsStage == null) {
                detailsStage = new Stage();
                detailsStage.initModality(Modality.APPLICATION_MODAL);
                detailsStage.initOwner(mainStage);
                detailsStage.setTitle("Series Details");
                InputStream icon = Main.class.getResourceAsStream(Model.SETTINGS_ICON);
                if (icon != null) {
                    detailsStage.getIcons().add(new Image(icon));
                }
                detailsStage.setResizable(true);
                detailsStage.initStyle(StageStyle.DECORATED);
            }
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(FX_PATH + "videodetails.fxml"));
            loader.setControllerFactory((Class<?> controllerType) -> {
                try {
                    for (Constructor<?> con : controllerType.getConstructors()) {
                        if (con.getParameterCount() == 3 && con.getParameterTypes()[0] == Model.class
                                && con.getParameterTypes()[1] == Stage.class && con.getParameterTypes()[2] == String.class) {
                            return con.newInstance(this, detailsStage, link);
                        }
                    }
                    return controllerType.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    System.err.println("Failed to load VideoDetailsController. Error: " + e.getMessage());
                    e.printStackTrace(System.err);
                    return null;
                }
            });
            Parent layout;
            try {
                layout = loader.load();
                Scene scene = new Scene(layout);
                detailsStage.toFront();
                detailsStage.setScene(scene);
                detailsStage.sizeToScene();
                SeriesDetailsController controller = loader.getController();
                try {
                    controller.loadSeries();
                    detailsStage.show();
                } catch (Exception e) {
                    showError("Error loading content from: " + link + " \n\nError: " + e.getLocalizedMessage());
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Validation Error: " + e.getMessage());
            }
        });
    }

    public void setTableView(TableView<Download> tableView) {
        this.tableView = tableView;
        tableView.getItems().addAll(FXCollections.observableArrayList(downloadSave.getDownloads()));
        tableView.sort();
    }

    public synchronized void addDownload(Download download) {
        if (tableView == null) {
            return;
        }
        if (!tableView.getItems().contains(download)) {
            tableView.getItems().add(download);
            downloadSave.getDownloads().add(download);
            tableView.sort();
            saveDownloads();
            tableView.getItems().indexOf(download);
        }
    }

    public void removeDownload(Download download) {
        if (tableView == null) {
            return;
        }
        tableView.getItems().remove(download);
        downloadSave.getDownloads().remove(download);
        tableView.sort();
        saveDownloads();
    }

    public void updateDownloadProgress(Download download, String progress) {
        if (tableView == null) {
            return;
        }
        tableView.getItems().get(indexForDownload(download, true)).setProgressProperty(progress);
        downloadSave.getDownloads().get(indexForDownload(download, false)).setProgressProperty(progress);
        saveDownloads();
    }

    public void updateDownload(Download download) {
        tableView.getItems().get(indexForDownload(download, true)).update(download);
        downloadSave.getDownloads().get(indexForDownload(download, false)).update(download);
        saveDownloads();
    }

    private int indexForDownload(Download download, boolean table) {
        if (table) {
            return tableView.getItems().indexOf(download);
        } else {
            return downloadSave.getDownloads().indexOf(download);
        }
    }

    public Download getDownloadForPath(String path) {
        for (Download download : downloadSave.getDownloads()) {
            if (download.getDownloadPath().equals(path)) {
                return download;
            }
        }
        return null;
    }

    public Download getDownloadForUrl(String url) {
        for (Download download : downloadSave.getDownloads()) {
            if (download.getUrl().equals(url)) {
                return download;
            }
        }
        return null;
    }

    public void start() {
        if (running) {
            return;
        }
        Platform.runLater(() -> {
            startButton.setDisable(true);
            stopButton.setDisable(false);
        });
        running = true;
    }

    public void stop() {
        if (!running) {
            return;
        }
        Platform.runLater(() -> {
            startButton.setDisable(false);
            stopButton.setDisable(true);
        });
        running = false;
    }

    public String getRandomUserAgent() {
        if (!userAgents.isEmpty()) {
            return userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size()));
        } else {
            return "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";
        }
    }

    public void saveSettings() {
        JsonManager.saveSettings(settings);
    }

    public void saveSeriesHistory() {
        JsonManager.saveHistory(historySave);
    }

    public void saveDownloads() {
        JsonManager.saveDownloads(downloadSave);
    }

    public List<String> getLinkUrls() {
        List<String> urls = new ArrayList<>();
        for (Episode episode : links) {
            urls.add(episode.getLink());
        }
        return urls;
    }

    public synchronized Episode getNextLink() {
        if (links.isEmpty()) {
            return null;
        }
        Episode link = links.get(0);
        links.remove(0);
        return link;
    }

    public void openFolder(File file, boolean parent) {
        if (file == null) {
            showError("This file is corrupted.");
            return;
        }
        openFolder(file.getAbsolutePath(), parent);
    }

    public void openFolder(String path, boolean parent) {
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeToast(mainStage, "This file doesn't exist.");
            return;
        }
        if (parent) {
            if (file.getParentFile().exists()) {
                file = file.getParentFile();
            } else {
                Toast.makeToast(mainStage, "This file's parent doesn't exist.");
                return;
            }
        }
        if (!Desktop.isDesktopSupported()) {
            showError("Desktop is not supported on this device.");
            return;
        }
        try {
            desktop.open(file);
        } catch (IOException e) {
            showError("Unable to open folder.", e);
        }
    }

    private boolean shutdownExecuted = false;

    public void shutdown(boolean force) {
        if (force && !shutdownExecuted) {
            stop();
            if (!runningDrivers.isEmpty()) {
                for (WebDriver driver : runningDrivers) {
                    if (driver != null) {
                        driver.close();
                        driver.quit();
                    }
                }
            }
            saveSettings();
            saveSeriesHistory();
            saveDownloads();
            System.err.println("forced shutdown");
            System.exit(-1);
            return;
        }
        if (isRunning()) {
            showConfirm("You are currently downloading videos right now. Shutting down will stop and possibly corrupt " +
                    "any incomplete video. Do you wish to continue?", () -> {
                stop();
                if (!runningDrivers.isEmpty()) {
                    for (WebDriver driver : runningDrivers) {
                        if (driver != null) {
                            driver.close();
                            driver.quit();
                        }
                    }
                }
                saveSettings();
                saveSeriesHistory();
                saveDownloads();
                shutdownExecuted = true;
                System.exit(0);
            });
        } else {
            if (!runningDrivers.isEmpty()) {
                for (WebDriver driver : runningDrivers) {
                    if (driver != null) {
                        driver.close();
                        driver.quit();
                    }
                }
            }
            saveSettings();
            saveSeriesHistory();
            saveDownloads();
            shutdownExecuted = true;
            System.exit(0);
        }
    }

    public void showMessage(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(MAIN_ICON);
        if (icon != null) {
            stage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText("");
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(MAIN_ICON);
        if (icon != null) {
            stage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText("");
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void showError(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(MAIN_ICON);
        if (icon != null) {
            stage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText("");
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void showError(String content, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(MAIN_ICON);
        if (icon != null) {
            stage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText("");
        alert.setContentText(content + "\nError: " + e.getLocalizedMessage());
        alert.showAndWait();
    }

    public void openLink(String link) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(link));
            } catch (Exception e) {
                showError("Unable to open " + link, e);
            }
        } else {
            showError("Desktop is not supported on this device.");
        }
    }

    public void showConfirm(String message, Runnable runnable) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.CANCEL, ButtonType.YES);
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType.equals(ButtonType.YES)) {
                if (runnable != null) {
                    runnable.run();
                }
            }
        });
    }

    public void showCopyPrompt(String text, boolean prompt) {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        if (prompt) {
            showConfirm("Do you want to copy this to your clipboard?", () -> {
                content.putString(text);
                clipboard.setContent(content);
                Toast.makeToast(mainStage, "Copied");
            });
        } else {
            content.putString(text);
            clipboard.setContent(content);
            Toast.makeToast(mainStage, "Copied");
        }
    }

    public void showLinkPrompt(String link, boolean prompt) {
        if (prompt) {
            showConfirm("Do you want to open\n" +
                    "[" + link + "]\nin your default browser?", () -> openLink(link));
        } else {
            openLink(link);
        }
    }

    public TextField getUrlTextField() {
        return tf_sub_url;
    }

    public void setUrlTextField(TextField tf_sub_url) {
        this.tf_sub_url = tf_sub_url;
    }

    public void setClientUpdating(boolean clientUpdating) {
        this.clientUpdating = clientUpdating;
    }

    public boolean isClientUpdating() {
        return clientUpdating;
    }

    public void setUpdateFileProgress(Label updateFileProgress) {
        this.updateFileProgress = updateFileProgress;
    }

    public Label getUpdateFileProgress() {
        return updateFileProgress;
    }

    public void setUpdateProgressBar(ProgressBar updateProgressBar) {
        this.updateProgressBar = updateProgressBar;
    }

    public ProgressBar getUpdateProgressBar() {
        return updateProgressBar;
    }

    public int getDownloadsFinishedForSession() {
        return downloadsFinishedForSession;
    }

    public void setDownloadsFinishedForSession(int downloadsFinishedForSession) {
        this.downloadsFinishedForSession = downloadsFinishedForSession;
    }

    public synchronized void incrementDownloadsFinished() {
        downloadsFinishedForSession++;
    }

    private final UpdateManager updateManager = new UpdateManager(this);

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public List<WebDriver> getRunningDrivers() {
        return runningDrivers;
    }

    public Desktop getDesktop() {
        return desktop;
    }

    public TableView<Download> getTableView() {
        return tableView;
    }

    public Settings settings() {
        return settings;
    }

    public HistorySave getHistorySave() {
        return historySave;
    }

    public DownloadSave getDownloadSave() {
        return downloadSave;
    }

    public BuddyHandler getReader() {
        return reader;
    }

    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    public void setTextOutput(TextArea textArea) {
        TextOutput output = new TextOutput(textArea);
        System.setOut(new PrintStream(output));
    }

    public Stage getMainStage() {
        return mainStage;
    }

    public boolean isRunning() {
        return running;
    }

    public List<Episode> getLinks() {
        return links;
    }


    public synchronized void incrementLinksFound() {
        linksFound++;
    }

    public void setLinksFound(int linksFound) {
        this.linksFound = linksFound;
    }

    public int getLinksFound() {
        return linksFound;
    }

    public void setStartButton(Button startButton) {
        this.startButton = startButton;
    }

    public void setStopButton(Button stopButton) {
        this.stopButton = stopButton;
    }
}