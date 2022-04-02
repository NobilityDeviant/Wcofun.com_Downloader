package com.nobility.downloader;

import com.nobility.downloader.scraper.settings.Defaults;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ResourceBundle;

public class ConfirmController implements Initializable {

    @FXML private TextArea updateLog;
    @FXML private Button btnUpdate, btnCancel;
    @FXML private ProgressBar progress;
    @FXML private Label fileProgress;
    private boolean required;
    private Stage stage;
    private final boolean upToDate;
    private final Model model;

    public ConfirmController(Model model) {
        this.model = model;
        upToDate = model.getUpdateManager().isLatestVersion()[0];
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        fileProgress.managedProperty().bind(fileProgress.visibleProperty());
        progress.managedProperty().bind(progress.visibleProperty());
    }

    @FXML public void update() {
        if (model.isClientUpdating()) {
            return;
        }
        fileProgress.setVisible(true);
        progress.setVisible(true);
        model.setUpdateFileProgress(fileProgress);
        model.setUpdateProgressBar(progress);
        btnUpdate.setText("Updating");
        btnUpdate.setDisable(true);
        model.getUpdateManager().openUpdateLink(required);
    }

    @FXML public void cancel() {
        if (model.isClientUpdating()) {
            model.showConfirm("The new update is currently downloading. " +
                    "Would you like to close this window and cancel the process?", () -> {
                if (required) {
                    model.showError("You must update your client to continue. Shutting down...");
                    System.exit(0);
                } else {
                    stage.close();
                    if (!upToDate) {
                        System.out.println("Update has been denied. You will no longer receive a notification about it until the next update.");
                        model.settings().setBoolean(Defaults.DENIEDUPDATE, true);
                        model.saveSettings();
                    }
                }
            });
            return;
        }
        if (required) {
            model.showError("You must update your client to continue. Shutting down...");
            System.exit(0);
        } else {
            stage.close();
            if (!upToDate) {
                System.out.println("Update has been denied. You will no longer receive a notification about it until the next update.");
                model.settings().setBoolean(Defaults.DENIEDUPDATE, true);
                model.saveSettings();
            }
        }
    }

    public void close() {
        if (model.isClientUpdating()) {
            model.showConfirm("The new update is currently downloading. " +
                    "Would you like to close this window and cancel the process?", () -> {
                if (required) {
                    model.showError("You must update your client to continue. Shutting down...");
                    System.exit(0);
                } else {
                    stage.close();
                }
            });
            return;
        }
        if (required) {
            model.showError("You must update your client to continue. Shutting down...");
            System.exit(0);
        } else {
            stage.close();
        }
    }

    protected void setStage(Stage stage, boolean required) {
        this.stage = stage;
        this.required = required;
        if (!required) {
            btnCancel.setText("Cancel");
        }
        StringBuilder sb = new StringBuilder();
        try {
            final URL url = new URL("https://www.dropbox.com/s/q7gzxpyf7xslksn/updates.txt?dl=1");
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setReadTimeout(UpdateManager.UPDATE_TIMEOUT);
            con.setConnectTimeout(UpdateManager.UPDATE_TIMEOUT);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String s;
            while ((s = in.readLine()) != null) {
                sb.append(s).append("\n");
            }
            in.close();
            con.disconnect();
            updateLog.setText(sb.toString());
        } catch (Exception e) {
            updateLog.setText("Failed to receive update log...");
        }
        if (upToDate) {
            btnUpdate.setText("Updated");
            btnUpdate.setDisable(true);
        }
    }

}