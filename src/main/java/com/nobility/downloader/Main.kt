package com.nobility.downloader

import com.nobility.downloader.updates.UpdateManager
import javafx.application.Application
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.util.Callback
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Application.launch(Main::class.java, *args)
}

class Main : Application() {

    @Throws(Exception::class)
    override fun start(primaryStage: Stage) {
        val model = Model()
        val loader = FXMLLoader(javaClass.getResource(Model.FX_PATH + "ui.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (
                        con.parameterCount == 2
                        && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                    ) {
                        return@Callback con.newInstance(model, primaryStage)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                System.err.println("Failed to load MainController. Error: " + e.message)
                e.printStackTrace(System.err)
                exitProcess(-2)
            }
        }
        val root = loader.load<Parent>()
        val scene = Scene(root)
        //controller.setMainStage(primaryStage)
        primaryStage.scene = scene
        scene.stylesheets.add(javaClass.getResource(Model.CSS_PATH + "contextmenu.css")?.toString() ?: "")
        primaryStage.title = "WcoFun Downloader By Nobility ver. " + UpdateManager.CURRENT_VERSION
        primaryStage.isResizable = true
        val icon = javaClass.getResourceAsStream(Model.MAIN_ICON)
        if (icon != null) {
            primaryStage.icons.add(Image(icon))
        }
        primaryStage.centerOnScreen()
        primaryStage.onCloseRequest = EventHandler { model.shutdown(false) }
        primaryStage.show()
        val controller = loader.getController<MainController>()
        //must set them up here because the scene is null before calling Stage#show
        controller.setupHotKeys()
    }
}