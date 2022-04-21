package com.nobility.downloader;

import com.nobility.downloader.downloads.Download;
import com.nobility.downloader.scraper.settings.Defaults;
import com.nobility.downloader.utils.AlertBox;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private final Model model;
    @FXML public TextField tf_sub_url;
    @FXML public TextArea print;
    @FXML public MenuItem open_download_folder, about, open_settings, updates, open_download_history, open_website;
    @FXML private Button stop_button, start_button;
    @FXML private TableView<Download> download_table;
    @FXML private TableColumn<Download, String> name_column, size_column, progress_column, date_column;
    @FXML private TableColumn<Download, MenuButton> actions_column;

    public MainController(Model model) {
        this.model = model;
    }

    public void setMainStage(Stage mainStage) {
        model.setMainStage(mainStage);
        mainStage.widthProperty().addListener(((observable, oldValue, newValue) -> download_table.refresh()));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        model.setTextOutput(print);
        model.setUrlTextField(tf_sub_url);
        tf_sub_url.promptTextProperty().setValue(Model.EXAMPLE_SERIES);
        tf_sub_url.setOnKeyPressed(e -> {
            if (e.getCode().equals(KeyCode.ENTER)) {
                start();
            }
        });
        stop_button.setDisable(true);
        model.setStartButton(start_button);
        model.setStopButton(stop_button);
        String lastUrl = model.settings().getString(Defaults.LASTDOWNLOAD);
        if (!StringChecker.isNullOrEmpty(lastUrl)) {
            tf_sub_url.setText(lastUrl);
        }
        download_table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        name_column.setMaxWidth(1f * Integer.MAX_VALUE * 60);
        date_column.setMaxWidth(1f * Integer.MAX_VALUE * 20);
        size_column.setMaxWidth(1f * Integer.MAX_VALUE * 10);
        progress_column.setMaxWidth(1f * Integer.MAX_VALUE * 7);
        actions_column.setMaxWidth(1f * Integer.MAX_VALUE * 10);

        name_column.setCellValueFactory(p -> p.getValue().getNameProperty());
        size_column.setCellValueFactory(row -> new SimpleStringProperty(Tools.bytesToString(row
                .getValue().getFileSize())));
        progress_column.setCellValueFactory(p -> p.getValue().getProgressProperty());
        date_column.setCellValueFactory(p -> p.getValue().getDateAddedProperty());
        date_column.setSortType(TableColumn.SortType.DESCENDING);
        download_table.getSortOrder().add(date_column);
        Comparator<String> dateComparator = (o1, o2) -> {
            SimpleDateFormat sdf = new SimpleDateFormat(Tools.dateFormat);
            try {
                Date date1 = sdf.parse(o1);
                Date date2 = sdf.parse(o2);
                return date1.compareTo(date2);
            } catch (Exception ignored) {}
            return 0;
        };
        date_column.setComparator(dateComparator);
        actions_column.setCellValueFactory(row -> {
            Download download = row.getValue();
            MenuButton menu = new MenuButton("");
            menu.getStylesheets().add(String.valueOf(Main.class.getResource(Model.CSS_PATH + "menubutton.css")));
            int height = 22;
            menu.setMaxHeight(height);
            menu.setMinHeight(height);
            menu.setPrefHeight(height);
            int width = (int) actions_column.getWidth() - 10;
            menu.setMaxWidth(width);
            menu.setMinWidth(width);
            menu.setPrefWidth(width);
            menu.setAlignment(Pos.CENTER);
            MenuItem openFolder = new MenuItem("Open Folder");
            openFolder.setOnAction(event -> model.openFolder(download.getDownloadPath(), true));
            MenuItem deleteFile = new MenuItem("Delete File");
            deleteFile.setOnAction(event ->
                    model.showConfirm("Do you wish to delete this file?", () -> {
                if (download.isDownloading()) {
                    model.showError("You can't delete videos that are being downloaded.");
                    return;
                }
                File file = new File(download.getDownloadPath());
                if (file.exists()) {
                    if (file.delete()) {
                        model.showMessage("Success", "Successfully deleted this episode.");
                    } else {
                        model.showError("Unable to delete this file. No error thrown. Most likely folder permission issues.");
                    }
                } else {
                    model.showError("The original file no longer exists.");
                }
            }));
            MenuItem removeFromList = new MenuItem("Remove From List");
            removeFromList.setOnAction(event -> {
                if (download.isDownloading()) {
                    model.showError("You can't remove downloads that are being downloaded.");
                    return;
                }
                model.removeDownload(download);
            });
            MenuItem resumeDownload = new MenuItem("Resume Download");
            resumeDownload.setOnAction(event -> {
                if (download.isComplete()) {
                    model.showError("This download is completed. You can't resume it.");
                    return;
                }
                if (model.isRunning()) {
                    if (model.getLinkUrls().contains(download.getUrl())) {
                        model.showError("This download is already in queue.");
                        return;
                    }
                    model.getLinks().add(download.toEpisode());
                    model.showMessage("Added to downloads", "Successfully added " + download.getName() + " to current queue.");
                } else {
                    tf_sub_url.setText(download.getUrl());
                    start();
                    model.showMessage("Started download", "Launched video downloader for: " + download.getName());
                }
            });
            MenuItem openDownloadUrl = new MenuItem("Open Download URL");
            openDownloadUrl.setOnAction(event -> model.showLinkPrompt(download.getUrl(), true));
            MenuItem copyDownloadUrl = new MenuItem("Copy Download URL");
            copyDownloadUrl.setOnAction(event -> model.showCopyPrompt(download.getUrl(), false));
            MenuItem seriesDetails = new MenuItem("Series Details");
            seriesDetails.setOnAction(event -> model.openVideoDetails(download.getSeriesLink()));
            menu.getItems().addAll(openFolder, openDownloadUrl, copyDownloadUrl,
                    resumeDownload, deleteFile, removeFromList, seriesDetails);
            return new SimpleObjectProperty<>(menu);
        });
        model.setTableView(download_table);

        boolean[] latest = model.getUpdateManager().isLatestVersion();
        String version = model.getUpdateManager().getLatestVersion();
        if (!model.settings().getString(Defaults.UPDATEVERSION).equalsIgnoreCase(version)) {
            model.settings().setBoolean(Defaults.DENIEDUPDATE, false);
            model.saveSettings();
        }
        if (!latest[0]) {
            if (latest[1]) {
                model.settings().setString(Defaults.UPDATEVERSION, version);
                model.saveSettings();
                showUpdateConfirm("Update Available - v" + version + " - Required", true);
            } else {
                if (!model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                    model.settings().setString(Defaults.UPDATEVERSION, version);
                    model.saveSettings();
                    showUpdateConfirm("Update Available - v" + version,false);
                }
            }
        }
        if (!latest[0] && latest[1]) {
            model.showError("You must update your client to continue. Shutting down...");
            System.exit(0);
        }
    }

    private final Stage confirmStage = new Stage();

    @FXML
    private void showUpdateConfirm(String title, boolean required) {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(Model.FX_PATH + "confirm.fxml"));
        loader.setControllerFactory((Class<?> controllerType) -> {
            try {
                for (Constructor<?> con : controllerType.getConstructors()) {
                    if (con.getParameterCount() == 1 && con.getParameterTypes()[0] == Model.class) {
                        return con.newInstance(model);
                    }
                }
                return controllerType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.exit(-1);
                return null;
            }
        });
        try {
            Parent root = loader.load();
            ConfirmController confirmController = loader.getController();
            Scene scene = new Scene(root);
            confirmController.setStage(confirmStage, required);
            InputStream icon = Main.class.getResourceAsStream(Model.MAIN_ICON);
            if (icon != null) {
                confirmStage.getIcons().add(new Image(icon));
            }
            confirmStage.sizeToScene();
            confirmStage.setTitle(title);
            confirmStage.setResizable(false);
            confirmStage.setScene(scene);
            confirmStage.setOnCloseRequest(event -> confirmController.close());
            confirmStage.showAndWait();
        } catch (IOException var5) {
            System.out.println("Message Error: " + var5.getMessage());
        }
    }

    private Stage settingsStage;

    public void openSettings() {
        if (settingsStage == null) {
            settingsStage = new Stage();
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.initOwner(model.getMainStage());
            settingsStage.setTitle("Settings");
            InputStream icon = Main.class.getResourceAsStream(Model.SETTINGS_ICON);
            if (icon != null) {
                settingsStage.getIcons().add(new Image(icon));
            }
            settingsStage.setResizable(true);
            settingsStage.initStyle(StageStyle.DECORATED);
        }
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(Model.FX_PATH + "settings.fxml"));
        loader.setControllerFactory((Class<?> controllerType) -> {
            try {
                for (Constructor<?> con : controllerType.getConstructors()) {
                    if (con.getParameterCount() == 2 && con.getParameterTypes()[0] == Model.class
                            && con.getParameterTypes()[1] == Stage.class) {
                        return con.newInstance(model, settingsStage);
                    }
                }
                return controllerType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Failed to load SettingsController. Error: " + e.getMessage());
                e.printStackTrace(System.err);
                return null;
            }
        });
        Parent layout;
        try {
            layout = loader.load();
            Scene scene = new Scene(layout);
            settingsStage.toFront();
            settingsStage.setScene(scene);
            settingsStage.sizeToScene();
            settingsStage.showAndWait();
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("Validation Error: " + e.getMessage());
        }
    }

    private Stage historyStage;

    private void openHistory() {
        if (historyStage == null) {
            historyStage = new Stage();
            historyStage.initModality(Modality.APPLICATION_MODAL);
            historyStage.initOwner(model.getMainStage());
            historyStage.setTitle("Series Download History");
            InputStream icon = Main.class.getResourceAsStream(Model.SETTINGS_ICON);
            if (icon != null) {
                historyStage.getIcons().add(new Image(icon));
            }
            historyStage.setResizable(true);
            historyStage.initStyle(StageStyle.DECORATED);
        }
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(Model.FX_PATH + "history.fxml"));
        loader.setControllerFactory((Class<?> controllerType) -> {
            try {
                for (Constructor<?> con : controllerType.getConstructors()) {
                    if (con.getParameterCount() == 2 && con.getParameterTypes()[0] == Model.class) {
                        return con.newInstance(model, historyStage);
                    }
                }
                return controllerType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Failed to load HistoryController. Error: " + e.getMessage());
                e.printStackTrace(System.err);
                return null;
            }
        });
        Parent layout;
        try {
            layout = loader.load();
            Scene scene = new Scene(layout);
            historyStage.toFront();
            historyStage.setScene(scene);
            historyStage.sizeToScene();
            historyStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Validation Error: " + e.getMessage());
        }
    }

    @FXML
    private void clearConsole() {
        print.clear();
    }

    @FXML
    private void start() {
        if (model.isRunning()) {
            return;
        }
        String url = tf_sub_url.getText();
        if (StringChecker.isNullOrEmpty(url)) {
            model.showError("You must input a series or show link first. \n\nExamples: \n\n"
                    + "Series: " + Model.EXAMPLE_SERIES + "\n\n Episode: " + Model.EXAMPLE_SHOW);
            return;
        }
        model.settings().setString(Defaults.LASTDOWNLOAD, url);
        model.saveSettings();
        /*if (!url.toLowerCase(Locale.US).contains(model.getWebsite())) {*model.showError("Your url must start with " + model.getWebsite() + " HTTPS Only");
            return;
        }*/
        if (model.settings().getInteger(Defaults.THREADS) < 1) {
            model.showError("Your download threads must be higher than 0.");
            return;
        }
        if (model.settings().getInteger(Defaults.THREADS) > 10) {
            model.showError("Your download threads must be lower than 10.");
            return;
        }
        if (model.settings().getInteger(Defaults.MAXLINKS) > 9999) {
            model.showError("Your episodes can't be higher than 9999.");
            return;
        }
        if (model.settings().getInteger(Defaults.MAXLINKS) < 0) {
            model.showError("Your episodes can't be lower than 0.");
            return;
        }
        model.start();
        model.getLinks().clear();
        model.setLinksFound(0);
        model.setDownloadsFinishedForSession(0);
        model.saveSettings();
        new Thread(() -> {
            try {
                model.getReader().update(url, false);
            } catch (Exception e) {
                try {
                    System.err.println(e.getMessage());
                    model.getReader().update(url, true);
                } catch (Exception e1) {
                    Platform.runLater(() -> model.showError("Error reading episodes from this url. " + e1.getLocalizedMessage()));
                    model.stop();
                    return;
                }
            }
            if (model.getLinks().isEmpty()) {
                Platform.runLater(() -> model.showError("Unable to find any episodes from this url."));
                model.stop();
                return;
            }
            model.settings().setString(Defaults.LASTDOWNLOAD, "");
            model.saveSettings();
            model.getReader().launch();
        }).start();
    }

    @FXML
    private void stop() {
        model.stop();
    }

    @FXML
    public void handleClicks(ActionEvent event) throws IOException {
        Object src = event.getSource();
        if (src.equals(open_settings)) {
            openSettings();
        } else if (src.equals(open_download_history)) {
            openHistory();
        } else if (src.equals(open_download_folder)) {
            if (Desktop.isDesktopSupported()) {
                if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.SAVEFOLDER))) {
                    File f = new File(model.settings().getString(Defaults.SAVEFOLDER));
                    Desktop.getDesktop().open(f);
                } else {
                    AlertBox.show(Alert.AlertType.ERROR, "Your download folder doesn't exist.", "Be sure to set it inside " +
                            "the settings before downloading videos.");
                }
            } else {
                model.showError("Desktop is not supported on your OS.");
            }
        } else if (src.equals(about)) {
            model.showMessage("About", "This is a FREE open source program to download videos from " + Model.WEBSITE
                    + ". That's all. :) "
                    + "\nAuthor Discord: Nobility#9814"
                    + "\n Github: https://github.com/NobilityDeviant/"
                    + "\nTo use this program you must first install Google Chrome. Everything else should be " +
                    "handled automatically." +
                    "\nYou find an episode or series link at " + Model.WEBSITE + " and paste it into " +
                    "the field. The default settings should be enough for most people." +
                    "\nIf you have any issues, please let me know on Discord.");
        } else if (src.equals(updates)) {
            showUpdateConfirm("Check For Updates", false);
        } else if (src.equals(open_website)) {
            model.showLinkPrompt(Model.WEBSITE, true);
        }
    }
}
