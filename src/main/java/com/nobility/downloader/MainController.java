package com.nobility.downloader;

import com.nobility.downloader.downloads.Download;
import com.nobility.downloader.settings.Defaults;
import com.nobility.downloader.utils.AlertBox;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Toast;
import com.nobility.downloader.utils.Tools;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
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
    @FXML
    public TextField tf_sub_url;
    @FXML
    public TextArea print;
    @FXML
    public MenuItem open_download_folder, about, open_settings, updates, open_download_history, open_website, open_github;
    @FXML
    private Button stop_button, start_button;
    @FXML
    private TableView<Download> download_table;
    @FXML
    private TableColumn<Download, String> name_column, size_column, progress_column, date_column;

    public MainController(Model model) {
        this.model = model;
        //SeriesScaper seriesScaper = new SeriesScaper(model);
        //seriesScaper.scrapeSeriesIntoFile();
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

        name_column.setCellValueFactory(new PropertyValueFactory<>("name"));
        size_column.setCellValueFactory(row -> new SimpleStringProperty(Tools.bytesToString(row
                .getValue().getFileSize())));
        progress_column.setCellValueFactory(row -> row.getValue().getProgress());
        date_column.setCellValueFactory(new PropertyValueFactory<>("dateAdded"));
        date_column.setSortType(TableColumn.SortType.DESCENDING);
        download_table.getSortOrder().add(date_column);
        Comparator<String> dateComparator = (o1, o2) -> {
            SimpleDateFormat sdf = new SimpleDateFormat(Tools.dateFormat);
            try {
                Date date1 = sdf.parse(o1);
                Date date2 = sdf.parse(o2);
                return date1.compareTo(date2);
            } catch (Exception ignored) {
            }
            return 0;
        };
        date_column.setComparator(dateComparator);

        download_table.setRowFactory(param -> {
            TableRow<Download> row = new TableRow<>();
            row.emptyProperty().addListener((observable, oldValue, isEmpty) -> {
                if (!isEmpty) {
                    Download download = row.getItem();
                    if (download != null) {
                        ContextMenu menu = new ContextMenu();
                        MenuItem openFolder = new MenuItem("Open Folder");
                        openFolder.setOnAction(event -> model.openFolder(download.getDownloadPath(), true));
                        MenuItem deleteFile = new MenuItem("Delete File");
                        deleteFile.setOnAction(event ->
                                model.showConfirm("Do you wish to delete this file?", () -> {
                                    if (download.isDownloading()) {
                                        Toast.makeToast(model.getMainStage(), "You can't delete videos that are being downloaded.");
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
                                        Toast.makeToast(model.getMainStage(), "The original file no longer exists.");
                                    }
                                }));
                        MenuItem removeFromList = new MenuItem("Remove From List");
                        removeFromList.setOnAction(event -> {
                            if (download.isDownloading()) {
                                Toast.makeToast(model.getMainStage(),"You can't remove downloads that are being downloaded.");
                                return;
                            }
                            model.removeDownload(download);
                        });

                        MenuItem resumeDownload = new MenuItem("Resume Download");
                        resumeDownload.setOnAction(event -> {
                            if (download.isComplete()) {
                                Toast.makeToast(model.getMainStage(),"This download is completed. You can't resume it.");
                                return;
                            }
                            if (model.isRunning()) {
                                if (model.getLinkUrls().contains(download.getUrl())) {
                                    Toast.makeToast(model.getMainStage(), "This download is already in queue.");
                                    return;
                                }
                                model.getLinks().add(download.toEpisode());
                                Toast.makeToast(model.getMainStage(), "Successfully added " + download.getName() + " to current queue.");
                            } else {
                                tf_sub_url.setText(download.getUrl());
                                start();
                                Toast.makeToast(model.getMainStage(), "Launched video downloader for: " + download.getName());
                            }
                        });
                        MenuItem openDownloadUrl = new MenuItem("Open Download URL");
                        openDownloadUrl.setOnAction(event -> model.showLinkPrompt(download.getUrl(), true));
                        MenuItem copyDownloadUrl = new MenuItem("Copy Download URL");
                        copyDownloadUrl.setOnAction(event -> model.showCopyPrompt(download.getUrl(), false));
                        MenuItem seriesDetails = new MenuItem("Series Details");
                        seriesDetails.setOnAction(event -> model.openVideoDetails(download.getSeriesLink()));
                        MenuItem copySeriesLink = new MenuItem("Copy Series Lihk");
                        copySeriesLink.setOnAction(event -> model.showCopyPrompt(download.getSeriesLink(), false));
                        MenuItem pauseDownload = new MenuItem("Pause Download");
                        pauseDownload.setOnAction(event -> {
                            //
                            Toast.makeToast(model.getMainStage(), "Not implemented.");
                        });
                        MenuItem play = new MenuItem("Play Video");
                        play.setOnAction(event -> {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(download.getDownloadFile());
                                } else {
                                    Toast.makeToast(model.getMainStage(), "Desktop is not supported.");
                                }
                            } catch (Exception e) {
                                Toast.makeToast(model.getMainStage(), "Failed to play video. Check the console.");
                                if (e.getLocalizedMessage().contains("No application is associated with the specified file for this operation")) {
                                    System.out.println("There is no default application for opening this type of file. (mp4)");
                                } else {
                                    System.out.println(e.getLocalizedMessage());
                                }
                            }
                        });
                        menu.getItems().addAll(
                                openFolder,
                                openDownloadUrl,
                                copyDownloadUrl
                        );
                        if (!StringChecker.isNullOrEmpty(download.getSeriesLink())) {
                            menu.getItems().add(seriesDetails);
                            menu.getItems().add(copySeriesLink);
                        }
                        if (download.isDownloading() && !download.isComplete()) {
                            menu.getItems().add(0, pauseDownload);
                        }
                        if (!download.isDownloading() && !download.isComplete()) {
                            menu.getItems().add(0, resumeDownload);
                        }
                        if (!download.isDownloading() && download.isComplete()) {
                            menu.getItems().add(0, play);
                        }
                        if (!download.isDownloading()) {
                            menu.getItems().add(removeFromList);
                        }
                        if (download.isComplete() && !download.isDownloading()) {
                            menu.getItems().add(deleteFile);
                        }
                        row.setContextMenu(menu);

                        row.setOnMouseClicked(event -> {
                            if (model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK)) {
                                menu.show(model.getMainStage(), event.getScreenX(), event.getScreenY());
                            }
                        });

                    }
                }
            });
            return row;
        });
        model.setTableView(download_table);
        model.getUpdateManager().checkForUpdates(false, true);
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
        } catch (IOException e) {
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
            scene.getStylesheets().add(String.valueOf(Main.class.getResource(Model.CSS_PATH + "contextmenu.css")));
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
        model.start();
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
                    + "\nGithub: https://github.com/NobilityDeviant/"
                    + "\nTo use this program you must first install Google Chrome. Everything else should be " +
                    "handled automatically." +
                    "\nYou find an episode or series link at " + Model.WEBSITE + " and paste it into " +
                    "the field. The default settings should be enough for most people." +
                    "\nIf you have any issues, please let me know on Discord.");
        } else if (src.equals(updates)) {
            if (model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                model.settings().setBoolean(Defaults.DENIEDUPDATE, false);
                model.saveSettings();
            }
            model.getUpdateManager().checkForUpdates(true, true);
        } else if (src.equals(open_website)) {
            model.showLinkPrompt(Model.WEBSITE, true);
        } else if (src.equals(open_github)) {
            model.showLinkPrompt(Model.GITHUB, true);
        }
    }
}
