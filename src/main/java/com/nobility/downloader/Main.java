package com.nobility.downloader;

import com.nobility.downloader.updates.UpdateManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.lang.reflect.Constructor;

public class Main extends Application {

    private double x, y;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Model model = new Model();
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(Model.FX_PATH + "newui.fxml"));
        loader.setControllerFactory((Class<?> controllerType) -> {
            try {
                for (Constructor<?> con : controllerType.getConstructors()) {
                    if (con.getParameterCount() == 1 && con.getParameterTypes()[0] == Model.class) {
                        return con.newInstance(model);
                    }
                }
                return controllerType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Failed to load MainController. Error: " + e.getMessage());
                e.printStackTrace(System.err);
                return null;
            }
        });
        Parent root = loader.load();
        Scene scene = new Scene(root);
        MainController controller = loader.getController();
        controller.setMainStage(primaryStage);
        primaryStage.setScene(scene);
        scene.getStylesheets().add(String.valueOf(getClass().getResource(Model.CSS_PATH + "contextmenu.css")));
        primaryStage.setTitle("WcoFun Downloader By Nobility ver. " + UpdateManager.CURRENT_VERSION);
        primaryStage.setResizable(true);
        InputStream icon = getClass().getResourceAsStream(Model.MAIN_ICON);
        if (icon != null) {
            primaryStage.getIcons().add(new Image(icon));
        }
        primaryStage.centerOnScreen();
        //primaryStage.sizeToScene();

        root.setOnMousePressed(event -> {
            x = event.getSceneX();
            y = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - x);
            primaryStage.setY(event.getScreenY() - y);
        });
        primaryStage.setOnCloseRequest(event -> model.shutdown(false));
        primaryStage.show();
    }

    public static void main(String[] args)  {
        launch(args);
    }
}
