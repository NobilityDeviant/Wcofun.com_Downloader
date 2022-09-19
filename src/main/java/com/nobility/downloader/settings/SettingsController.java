package com.nobility.downloader.settings;

import com.nobility.downloader.Model;
import com.nobility.downloader.scraper.CategoryUpdater;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Tools;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    private final Model model;
    private final Stage stage;
    @FXML private Button buttonSaveSettings,
            buttonChooseDownloadFolder,
            buttonClearDownloads,
            buttonStopChrome,
            buttonClearHistory,
            buttonCheckGenres;
    @FXML private TextField fieldDownloadThreads,
            fieldMaxEpisodes,
            fieldProxy,
            fieldTimeout,
            fieldDownloadFolder;
    @FXML private CheckBox cbShowContext,
            cbSaveEpisodes,
            cbNoEpisodeLimit,
            cbEnableProxy;
    @FXML private ChoiceBox<String> choiceBrowser;

    public SettingsController(Model model, Stage stage) {
        this.model = model;
        this.stage = stage;
    }

    public void executeStartCommand(int command) {
        if (command > -1) {
            switch (command) {
                case 0:
                    setDownloadFolder();
                    break;
                case 1:
                    break;
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        model.settings();
        fieldDownloadThreads.setText(String.valueOf(model.settings().getInteger(Defaults.DOWNLOADTHREADS)));
        fieldMaxEpisodes.setText(String.valueOf(model.settings().getInteger(Defaults.MAXEPISODES)));
        fieldProxy.setText(model.settings().getString(Defaults.PROXY));
        fieldTimeout.setText(String.valueOf(model.settings().getInteger(Defaults.TIMEOUT)));
        fieldDownloadFolder.setText(model.settings().getString(Defaults.SAVEFOLDER));
        fieldDownloadThreads.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(fieldDownloadThreads, oldValue, newValue, true));
        fieldMaxEpisodes.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(fieldMaxEpisodes, oldValue, newValue, true));
        fieldProxy.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(fieldProxy, oldValue, newValue, false));
        fieldTimeout.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(fieldTimeout, oldValue, newValue, true));
        fieldDownloadFolder.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(fieldDownloadFolder, oldValue, newValue, false));

        buttonSaveSettings.setDisable(true); // disable save button by default
        cbNoEpisodeLimit.setSelected(model.settings().getBoolean(Defaults.NOEPISODELIMIT));
        fieldMaxEpisodes.setDisable(model.settings().getBoolean(Defaults.NOEPISODELIMIT));
        cbNoEpisodeLimit.selectedProperty().addListener(((observableValue, oldValue, newValue) -> {
            buttonSaveSettings.setDisable(!settingsChanged());
            fieldMaxEpisodes.setDisable(newValue);
        }));
        cbEnableProxy.setSelected(model.settings().getBoolean(Defaults.ENABLEPROXY));
        fieldProxy.setDisable(!model.settings().getBoolean(Defaults.ENABLEPROXY));
        cbEnableProxy.selectedProperty().addListener((observable, oldValue, newValue) -> {
            buttonSaveSettings.setDisable(!settingsChanged());
            fieldProxy.setDisable(!newValue);
        });
        cbSaveEpisodes.setSelected(model.settings().getBoolean(Defaults.SAVELINKS));
        cbSaveEpisodes.selectedProperty().addListener((observable, oldValue, newValue) ->
                buttonSaveSettings.setDisable(!settingsChanged()));
        cbShowContext.setSelected(model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK));
        cbShowContext.selectedProperty().addListener((observable, oldValue, newValue) ->
                buttonSaveSettings.setDisable(!settingsChanged()));
        choiceBrowser.getItems().addAll(DriverDefaults.allDrivers());
        choiceBrowser.setValue(model.settings().getString(Defaults.DRIVER));
        choiceBrowser.getSelectionModel().selectedItemProperty().addListener(((observable, oldValue, newValue) ->
                buttonSaveSettings.setDisable(!settingsChanged())));
        stage.setOnCloseRequest(event -> {
            if (settingsChanged()) {
                model.showConfirm("You have unsaved changes. Would you like to save your settings?", () -> {
                    if (saveSettings()) {
                        stage.close();
                    }
                }, stage::close);
            } else {
                stage.close();
            }
        });
    }

    @FXML
    private void handleClicks(ActionEvent event) {
        if (event.getSource().equals(buttonClearHistory)) {
            if (model.history().getSavedSeries().isEmpty()) {
                return;
            }
            model.showConfirm("Would you like to clear all your download history?", () -> {
                int size = model.history().getSavedSeries().size();
                model.history().getSavedSeries().clear();
                model.saveSeriesHistory();
                model.toast("Cleared " + size + " download history from your settings.", stage);
            });
        } else if (event.getSource().equals(buttonClearDownloads)) {
            if (model.downloads().getDownloads().isEmpty()) {
                return;
            }
            model.showConfirm("Would you like to clear all your downloads? " +
                    "\nNote: This will not delete any files.", () -> {
                int size = model.downloads().getDownloads().size();
                model.downloads().getDownloads().clear();
                model.getTableView().getItems().clear();
                model.saveDownloads();
                model.toast("Cleared " + size + " downloads from your settings.", stage);
            });
        } else if (event.getSource().equals(buttonChooseDownloadFolder)) {
            setDownloadFolder();
        } else if (event.getSource().equals(buttonSaveSettings)) {
            saveSettings();
        } else if (event.getSource().equals(buttonStopChrome)) {
            stopChrome();
        } else if (event.getSource().equals(buttonCheckGenres)) {
            if (model.getCategoryUpdater() == null) {
                model.setCategoryUpdater(new CategoryUpdater(model));
                //model.getCategoryUpdater().checkForUpdates();
            }
        }
    }

    private void stopChrome() {
        if (model.isRunning()) {
            model.showError("You can't use this while the downloader is running.");
            return;
        }
        String systemType = System.getProperty("os.name").toLowerCase();
        model.showConfirm("Would you like to kill all chrome.exe and chromedriver.exe processes?"
                + "\nSometimes Selenium doesn't properly close all resources. This makes it easier to kill them all. " +
                "\nNote: This will close your actual chrome browser as well.", () -> {
            try {
                if (systemType.contains("win")) {
                    Runtime.getRuntime().exec("taskkill /f /im chromedriver.exe");
                    Runtime.getRuntime().exec("taskkill /f /im chrome.exe");
                } else {
                    Runtime.getRuntime().exec("pkill -f \"(chrome)?(--headless)\"");
                }
                model.toast("Successfully killed all chrome.exe and chromedriver.exe processes.", stage);
            } catch (Exception e) {
                model.showError("Failed to close one or more chrome processes. Error: " + e.getLocalizedMessage());
            }
        });
    }

    private void setDownloadFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Download Folder");
        File saveFolder = new File(model.settings().getString(Defaults.SAVEFOLDER));
        if (saveFolder.exists()) {
            directoryChooser.setInitialDirectory(saveFolder);
        } else {
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            fieldDownloadFolder.setText(selectedDirectory.getAbsolutePath());
            buttonSaveSettings.setDisable(!settingsChanged());
        }
    }
    /**
     * Main button save handle
     */
    private boolean saveSettings() {
        if (fieldDownloadThreads.getText().isEmpty()) {
            fieldDownloadThreads.setText(String.valueOf(Defaults.DOWNLOADTHREADS.getValue()));
        }
        if (fieldMaxEpisodes.getText().isEmpty()) {
            fieldMaxEpisodes.setText(String.valueOf(Defaults.MAXEPISODES.getValue()));
        }
        if (fieldTimeout.getText().isEmpty()) {
            fieldTimeout.setText(String.valueOf(Defaults.TIMEOUT.getValue()));
        }
        if (fieldDownloadFolder.getText().isEmpty()) {
            model.toast("The download folder can't be empty.", stage);
            setDownloadFolder();
            return false;
        }
        File downloadFolder = new File(fieldDownloadFolder.getText());
        if (!downloadFolder.exists()) {
            model.toast("The downloader folder doesn't exist.", stage);
            setDownloadFolder();
            return false;
        }
        if (!downloadFolder.canWrite()) {
            model.showError("The download folder doesn't allow write permissions." +
                    "\nIf this is a USB or SD Card then disable write protection." +
                    "\nTry selecting a folder in the user or home folder. Those are usually not restricted.");
            return false;
        }
        if (Tools.bytesToMB(downloadFolder.getUsableSpace()) < 150) {
            model.showError("The download folder requires at least 150MB of free space." +
                    "\nMost videos average around 100MB.");
            return false;
        }
        if (!StringChecker.isNullOrEmpty(fieldProxy.getText())
                && cbEnableProxy.isSelected()) {
            byte res = isValidProxy(fieldProxy.getText());
            switch (res) {
                case 2:
                    model.toast("Ports can only be between 1-65535.", stage);
                    return false;
                case -1:
                    model.showError("Not a valid proxy", "You must input a proxy and port with : as a delimiter. Auth Proxies are not allowed." +
                            "\nExample: 192.168.0.1:80 \uD83E\uDD7A");
                    return false;
                case 1:
                    model.showError("Unable to connect to proxy",
                            "Proxy is not able to reach the target " + Model.WEBSITE
                            + "\nRead the console to see the error.");
                    return false;
            }
        }
        int threads = Integer.parseInt(fieldDownloadThreads.getText());
        int links = Integer.parseInt(fieldMaxEpisodes.getText());
        int proxyTimeout = 0;
        if (threads < 1 || threads > 10) {
            model.showError(threads < 1 ? "Threads are too low" : "Threads are too high",
                    "You are only allowed to use 1 - 10 threads.");
            return false;
        }
        if (!cbNoEpisodeLimit.isSelected()) {
            if (links < 1 || links >= 200) {
                model.showError(links < 1 ? "Episodes are too low" : "Episodes are too high",
                        "You are only allowed to use 1-200 episodes.");
                return false;
            }
        }
        if (!StringChecker.isNullOrEmpty(fieldTimeout.getText())) {
            proxyTimeout = Integer.parseInt(fieldTimeout.getText());
            if (proxyTimeout < 5 || proxyTimeout > 240) {
                model.showError(proxyTimeout < 5 ? "Proxy Timeout is too low"
                                : "Proxy Timeout is too high",
                        "You are only allowed to use 5-240 seconds for proxy timeout.");
                return false;
            }
        }
        model.settings().setInteger(Defaults.DOWNLOADTHREADS, threads);
        model.settings().setInteger(Defaults.MAXEPISODES, links);
        model.settings().setBoolean(Defaults.SAVELINKS, cbSaveEpisodes.isSelected());
        model.settings().setString(Defaults.PROXY, fieldProxy.getText());
        model.settings().setInteger(Defaults.TIMEOUT, proxyTimeout);
        model.settings().setString(Defaults.SAVEFOLDER, fieldDownloadFolder.getText());
        model.settings().setBoolean(Defaults.SHOWCONTEXTONCLICK, cbShowContext.isSelected());
        model.settings().setString(Defaults.DRIVER, choiceBrowser.getValue());
        model.settings().setBoolean(Defaults.ENABLEPROXY, cbEnableProxy.isSelected());
        model.settings().setBoolean(Defaults.NOEPISODELIMIT, cbNoEpisodeLimit.isSelected());
        model.saveSettings();
        model.toast("Settings successfully saved.", stage);
        buttonSaveSettings.setDisable(true);
        return true;
    }

    /**
     * Determines whether or not if any setting value has been changed
     * @return Boolean - Settings have been changed
     */
    private boolean settingsChanged() {
        return ((!(String.valueOf(model.settings().getInteger(Defaults.DOWNLOADTHREADS)).equals(fieldDownloadThreads.getText()))) ||
                (!(String.valueOf(model.settings().getInteger(Defaults.MAXEPISODES)).equals(fieldMaxEpisodes.getText()))) ||
                (model.settings().getBoolean(Defaults.SAVELINKS) != cbSaveEpisodes.isSelected()) ||
                (model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK) != cbShowContext.isSelected()) ||
                (!model.settings().getString(Defaults.PROXY).equals(fieldProxy.getText())) ||
                (!model.settings().getString(Defaults.SAVEFOLDER).equals(fieldDownloadFolder.getText())) ||
                (model.settings().getBoolean(Defaults.NOEPISODELIMIT) != cbNoEpisodeLimit.isSelected()) ||
                (model.settings().getBoolean(Defaults.ENABLEPROXY) != cbEnableProxy.isSelected()) ||
                (!(String.valueOf(model.settings().getInteger(Defaults.TIMEOUT)).equals(fieldTimeout.getText()))) ||
                (!model.settings().getString(Defaults.DRIVER).equals(choiceBrowser.getValue()))
        );
    }

    /**
     * Makes sure the user is only able enter in a number on the textfield
     * @param field - TextField
     * @param oldValue - String the value before a new character is entered
     * @param newValue - String the value after the new character is entered
     */
    private void handleTextField(TextField field, String oldValue, String newValue, boolean numbers) {
        if (numbers) {
            if (!newValue.matches("\\d*")) {
                field.setText(newValue.replaceAll("\\D", "0"));
            }
            try {
                if (field.getText().length() > 0) {
                    int value = Integer.parseInt(newValue);
                    if (field == fieldDownloadThreads) {
                        if (field.getText().length() == 1) {
                            if (value < 1) {
                                field.setText("1");
                            }
                        } else {
                            if (value > 10) {
                                field.setText("10");
                            }
                        }
                    } else if (field == fieldMaxEpisodes) {
                        if (field.getText().length() == 1) {
                            if (value < 1) {
                                field.setText("1");
                            }
                        } else {
                            if (value > 9999) {
                                field.setText("9999");
                            }
                        }
                    } else if (field == fieldTimeout) {
                        if (field.getText().length() == 1) {
                            if (value < 1) {
                                field.setText("1");
                            }
                        } else {
                            if (value > 120) {
                                field.setText("120");
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                field.setText(oldValue);
            }
        }
        buttonSaveSettings.setDisable(!settingsChanged());
    }
    private byte isValidProxy(String s) {
        model.toast("Checking proxy...");
        String[] split;
        String ip;
        int port;
        try {
            split = s.split(":");
            ip = split[0];
            port = Integer.parseInt(split[1]);
            if (port < 0 || port > 65535) {
                return 2;
            }
        } catch (Exception ignored) {
            return -1;
        }
        try {
            Connection.Response response = Jsoup.connect(Model.WEBSITE)
                    .timeout(model.settings().getInteger(Defaults.TIMEOUT) * 1000)
                    .proxy(ip, port)
                    .execute();
            if (response.statusCode() == 200) {
                return 0;
            } else {
                return 1;
            }
        } catch (Exception e) {
            return 1;
        }
    }
}
