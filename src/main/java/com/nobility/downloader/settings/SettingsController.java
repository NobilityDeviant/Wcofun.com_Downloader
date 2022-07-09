package com.nobility.downloader.settings;

import com.nobility.downloader.Model;
import com.nobility.downloader.utils.AlertBox;
import com.nobility.downloader.utils.StringChecker;
import com.nobility.downloader.utils.Toast;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
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
    @FXML private Button button_save;
    @FXML private TextField field_threads, field_links, field_proxy, field_proxytimeout, field_download;
    @FXML private CheckBox cb_savelinks;
    @FXML private CheckBox cb_showcontext;

    public SettingsController(Model model, Stage stage) {
        this.model = model;
        this.stage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (model.settings() != null) {
            field_threads.setText(String.valueOf(model.settings().getInteger(Defaults.THREADS)));
            field_links.setText(String.valueOf(model.settings().getInteger(Defaults.MAXLINKS)));
            field_proxy.setText(model.settings().getString(Defaults.PROXY));
            field_proxytimeout.setText(String.valueOf(model.settings().getInteger(Defaults.PROXYTIMEOUT)));
            field_download.setText(model.settings().getString(Defaults.SAVEFOLDER));
            field_threads.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(field_threads, oldValue, newValue, true));
            field_links.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(field_links, oldValue, newValue, true));
            field_proxy.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(field_proxy, oldValue, newValue, false));
            field_proxytimeout.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(field_proxytimeout, oldValue, newValue, true));
            field_download.textProperty().addListener((observable, oldValue, newValue) -> handleTextField(field_download, oldValue, newValue, false));

            button_save.setDisable(true); // disable save button by default
            cb_savelinks.setSelected(model.settings().getBoolean(Defaults.SAVELINKS));
            cb_savelinks.selectedProperty().addListener((observable, oldValue, newValue) ->
                    button_save.setDisable(!settingsChanged()));
            cb_showcontext.setSelected(model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK));
            cb_showcontext.selectedProperty().addListener((observable, oldValue, newValue) ->
                    button_save.setDisable(!settingsChanged()));
        } else {
            AlertBox.show(Alert.AlertType.INFORMATION, "Settings are corrupted!",
                    "Restart the application before using it.");
        }
        stage.setOnCloseRequest(event -> {
            if (settingsChanged()) {
                model.showConfirm("You have unsaved changes. Would you like to save your settings?", () -> {
                    button_save_action();
                    stage.close();
                }, stage::close);
                return;
            }
            stage.close();
        });
    }

    @FXML
    private void resetHistory() {
        if (model.getHistorySave().getSavedSeries().isEmpty()) {
            return;
        }
        model.showConfirm("Would you like to clear all your download history?", () -> {
            int size = model.getHistorySave().getSavedSeries().size();
            model.getHistorySave().getSavedSeries().clear();
            model.saveSeriesHistory();
            Toast.makeToast(model.getMainStage(), "Cleared " + size + " download history from your settings.");
        });
    }

    @FXML
    private void clearDownloads() {
        if (model.getDownloadSave().getDownloads().isEmpty()) {
            return;
        }
        model.showConfirm("Would you like to clear all your downloads? " +
                "\nNote: This will not delete any files.", () -> {
                int size = model.getDownloadSave().getDownloads().size();
                model.getDownloadSave().getDownloads().clear();
                model.getTableView().getItems().clear();
                model.saveDownloads();
                Toast.makeToast(model.getMainStage(), "Cleared " + size + " downloads from your settings.");
        });
    }

    @FXML
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
                Toast.makeToast(model.getMainStage(), "Successfully killed all chrome.exe and chromedriver.exe processes.");
            } catch (Exception e) {
                model.showError("Failed to close one or more chrome processes. Error: " + e.getLocalizedMessage());
            }
        });
    }

    @FXML
    private void setDownloadFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File saveFolder = new File(model.settings().getString(Defaults.SAVEFOLDER));
        if (saveFolder.exists()) {
            directoryChooser.setInitialDirectory(saveFolder);
        } else {
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            field_download.setText(selectedDirectory.getAbsolutePath());
            button_save.setDisable(!settingsChanged());
        }
    }
    /**
     * Main button save handle
     */
    @FXML
    private void button_save_action() {
        if (field_threads.getText().isEmpty()) {
            Toast.makeToast(model.getMainStage(), "Threads can't be empty.");
            return;
        }
        if (field_links.getText().isEmpty()) {
            Toast.makeToast(model.getMainStage(), "Episodes can't be empty.");
            return;
        }
        if (field_proxytimeout.getText().isEmpty()) {
            Toast.makeToast(model.getMainStage(), "Timeout can't be empty.");
            return;
        }
        if (field_download.getText().isEmpty()) {
            Toast.makeToast(model.getMainStage(), "Download folder can't be empty.");
            return;
        }
        if (!new File(field_download.getText()).exists()) {
            Toast.makeToast(model.getMainStage(), "This download folder does not exist.");
            return;
        }
        if (!StringChecker.isNullOrEmpty(field_proxy.getText())) {
            byte res = isValidProxy(field_proxy.getText());
            switch (res) {
                case 2:
                    Toast.makeToast(model.getMainStage(), "Ports can only be between 1-65535");
                    return;
                case -1:
                    AlertBox.show(Alert.AlertType.ERROR, "Not a valid proxy",
                            "You must input a proxy and port with : as a delimiter. Auth Proxies are not allowed." +
                                    "\nExample: 192.168.0.1:80 \uD83E\uDD7A");
                    return;
                case 1:
                    AlertBox.show(Alert.AlertType.ERROR, "Unable to connect",
                            "Proxy is not able to reach the target " + Model.WEBSITE
                                    + "\nRead the console to see the error.");
                    return;
            }
        }
        int threads = Integer.parseInt(field_threads.getText());
        int links = Integer.parseInt(field_links.getText());
        int proxyTimeout = 0;
        if (threads < 1 || threads > 10) {
            AlertBox.show(Alert.AlertType.ERROR, threads < 1 ? "Threads are too low" : "Threads are too high",
                    "You are only allowed to use 1 - 10 threads.");
        } else if (links < 1 || links >= 500) {
            AlertBox.show(Alert.AlertType.ERROR, links < 1 ? "Episodes are too low" : "Episodes are too high",
                    "You are only allowed to use 1-500 episodes.");
        } else {
            if (!StringChecker.isNullOrEmpty(field_proxytimeout.getText())) {
                proxyTimeout = Integer.parseInt(field_proxytimeout.getText());
                if (proxyTimeout < 1 || proxyTimeout > 120) {
                    AlertBox.show(Alert.AlertType.ERROR, proxyTimeout < 1 ? "Proxy Timeout is too low"
                            : "Proxy Timeout is too high", "You are only allowed to use 1-120 seconds for proxy timeout.");
                    return;
                }
            }
            if (model.settings() != null) {
                model.settings().setInteger(Defaults.THREADS, threads);
                model.settings().setInteger(Defaults.MAXLINKS, links);
                model.settings().setBoolean(Defaults.SAVELINKS, cb_savelinks.isSelected());
                model.settings().setString(Defaults.PROXY, field_proxy.getText());
                model.settings().setInteger(Defaults.PROXYTIMEOUT, proxyTimeout);
                model.settings().setString(Defaults.SAVEFOLDER, field_download.getText());
                model.settings().setBoolean(Defaults.SHOWCONTEXTONCLICK, cb_showcontext.isSelected());
                model.saveSettings();
                Toast.makeToast(model.getMainStage(), "Settings successfully saved.");
                button_save.setDisable(true);
            } else {
                Toast.makeToast(model.getMainStage(), "Settings are corrupted. Restart the application.");
            }
        }
    }

    /**
     * Determines whether or not if any setting value has been changed
     * @return Boolean - Settings have been changed
     */
    private boolean settingsChanged() {
        return ((!(String.valueOf(model.settings().getInteger(Defaults.THREADS)).equals(field_threads.getText()))) ||
                (!(String.valueOf(model.settings().getInteger(Defaults.MAXLINKS)).equals(field_links.getText()))) ||
                (model.settings().getBoolean(Defaults.SAVELINKS) != cb_savelinks.isSelected()) ||
                (model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK) != cb_showcontext.isSelected()) ||
                (!model.settings().getString(Defaults.PROXY).equals(field_proxy.getText())) ||
                (!model.settings().getString(Defaults.SAVEFOLDER).equals(field_download.getText())) ||
                (!(String.valueOf(model.settings().getInteger(Defaults.PROXYTIMEOUT)).equals(field_proxytimeout.getText())))
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
                field.setText(newValue.replaceAll("[^\\d]", "0"));
            }
            try {
                if (field.getText().length() > 0) {
                    int value = Integer.parseInt(newValue);
                    if (field == field_threads) {
                        if (field.getText().length() == 1) {
                            if (value < 1) {
                                field.setText("1");
                            }
                        } else {
                            if (value > 10) {
                                field.setText("10");
                            }
                        }
                    } else if (field == field_links) {
                        if (field.getText().length() == 1) {
                            if (value < 1) {
                                field.setText("1");
                            }
                        } else {
                            if (value > 9999) {
                                field.setText("9999");
                            }
                        }
                    } else if (field == field_proxytimeout) {
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
        button_save.setDisable(!settingsChanged());
    }
    private byte isValidProxy(String s) {
        String[] split;
        int port;
        try {
            split = s.split(":");
            port = Integer.parseInt(split[1]);
            if (port < 0 || port > 65535) {
                return 2;
            }
        } catch (Exception ignored) {
            return -1;
        }
        try {
            Connection.Response response = Jsoup.connect(Model.WEBSITE)
                    .timeout(model.settings().getInteger(Defaults.PROXYTIMEOUT) * 1000)
                    .proxy(split[0], port)
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
