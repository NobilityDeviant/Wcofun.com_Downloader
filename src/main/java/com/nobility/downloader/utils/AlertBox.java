package com.nobility.downloader.utils;

import com.nobility.downloader.Main;
import com.nobility.downloader.Model;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * Creates an AlertBox
 */
public class AlertBox {

    /**
     * Displays an AlertBox
     * @param alertType - javafx.scene.control.Alert.AlertType
     * @param header - Heading of the Alert
     * @param content - Content of the Alert
     */
    public static void show(Alert.AlertType alertType, String header, String content) {
        Alert alert = new Alert(alertType, content, ButtonType.OK);
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(Model.DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(Model.MAIN_ICON);
        if (icon != null) {
            dialogStage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText(header);
        alert.setTitle("Settings");
        alert.show();
    }

    public static boolean showChoice(Alert.AlertType alertType, String header, String content) {
        Alert alert = new Alert(alertType, content, ButtonType.YES, ButtonType.NO);
        alert.getDialogPane().getStylesheets().add(String.valueOf(Main.class.getResource(Model.DIALOG_PATH)));
        alert.getDialogPane().getStyleClass().add("dialog");
        Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
        InputStream icon = Main.class.getResourceAsStream(Model.MAIN_ICON);
        if (icon != null) {
            dialogStage.getIcons().add(new Image(icon));
        }
        alert.setHeaderText(header);
        alert.setTitle("Settings");
        alert.showAndWait();
        return alert.getResult() == ButtonType.YES;
    }
}
