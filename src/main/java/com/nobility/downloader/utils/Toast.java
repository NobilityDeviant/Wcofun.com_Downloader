package com.nobility.downloader.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public final class Toast {

    public static void makeToast(Stage ownerStage, String text) {
        makeText(ownerStage, text, 3_500, 500, 500);
    }

    public static void makeToast(Stage ownerStage, String text, Exception e) {
        makeText(
                ownerStage,
                text
                + "\n" + "Error: " + e.getLocalizedMessage(),
                3_500,
                500,
                500
        );
    }

    public static void makeText(Stage ownerStage, String toastMsg, int toastDelay, int fadeInDelay, int fadeOutDelay) {

        final Stage toastStage = new Stage();
        toastStage.initOwner(ownerStage);
        toastStage.setResizable(false);
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);

        final Text text = new Text(toastMsg);
        text.setFont(Font.font("Verdana", 20));
        text.setFill(Color.GHOSTWHITE);

        final StackPane root = new StackPane(text);
        root.setStyle("-fx-background-radius: 20; -fx-background-color: rgba(0, 0, 0, 0.4); -fx-padding: 50px;");
        root.setOpacity(0);

        final Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        toastStage.setScene(scene);
        toastStage.show();

        final Timeline fadeInTimeline = new Timeline();
        KeyFrame fadeInKey1 = new KeyFrame(Duration.millis(fadeInDelay), new KeyValue(toastStage.getScene().getRoot().opacityProperty(), 1));
        fadeInTimeline.getKeyFrames().add(fadeInKey1);
        fadeInTimeline.setOnFinished((ae) -> new Thread(() -> {
            try {
                Thread.sleep(toastDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            final Timeline fadeOutTimeline = new Timeline();
            final KeyFrame fadeOutKey1 = new KeyFrame(Duration.millis(fadeOutDelay),
                    new KeyValue (toastStage.getScene().getRoot().opacityProperty(), 0));
            fadeOutTimeline.getKeyFrames().add(fadeOutKey1);
            fadeOutTimeline.setOnFinished((aeb) -> toastStage.close());
            fadeOutTimeline.play();
        }).start());
        fadeInTimeline.play();
    }
}
