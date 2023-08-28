package com.nobility.downloader.utils

import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Toast {

    private val scope = CoroutineScope(Dispatchers.Default)

    fun makeToast(ownerStage: Stage, text: String, transparency: Double) {
        makeText(ownerStage, text, 3500, 500, 500, transparency)
    }

    fun makeToast(ownerStage: Stage, text: String, e: Exception, transparency: Double) {
        makeText(
            ownerStage,
            """
                $text
                Error: ${e.localizedMessage}
                """.trimIndent(),
            3500,
            500,
            500,
            transparency
        )
    }

    private fun makeText(
        ownerStage: Stage,
        toastMsg: String,
        toastDelay: Int,
        fadeInDelay: Int,
        fadeOutDelay: Int,
        transparency: Double
    ) {
        val toastStage = Stage()
        toastStage.initOwner(ownerStage)
        toastStage.isResizable = false
        toastStage.initStyle(StageStyle.TRANSPARENT)
        //toastStage.isAlwaysOnTop = true
        val text = Text(toastMsg)
        text.font = Font.font("Verdana", 20.0)
        text.fill = Color.GHOSTWHITE
        val root = StackPane(text)
        root.style = "-fx-background-radius: 20; -fx-background-color: rgba(0, 0, 0, ${transparency / 100}); -fx-padding: 50px;"
        root.opacity = 0.0
        val scene = Scene(root)
        scene.fill = Color.TRANSPARENT
        toastStage.scene = scene
        toastStage.show()
        val fadeInTimeline = Timeline()
        val fadeInKey1 = KeyFrame(
            Duration.millis(fadeInDelay.toDouble()),
            KeyValue(toastStage.scene.root.opacityProperty(), 1)
        )
        fadeInTimeline.keyFrames.add(fadeInKey1)
        fadeInTimeline.onFinished = EventHandler {
            scope.launch {
                delay(toastDelay.toLong())
                val fadeOutTimeline = Timeline()
                val fadeOutKey1 = KeyFrame(
                    Duration.millis(fadeOutDelay.toDouble()),
                    KeyValue(toastStage.scene.root.opacityProperty(), 0)
                )
                fadeOutTimeline.keyFrames.add(fadeOutKey1)
                fadeOutTimeline.onFinished = EventHandler {
                    //toastStage.isAlwaysOnTop = false
                    toastStage.close()
                }
                fadeOutTimeline.play()
            }
        }
        fadeInTimeline.play()
    }
}