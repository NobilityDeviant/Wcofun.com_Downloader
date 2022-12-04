package com.nobility.downloader.utils

import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.Stage
import java.net.URL
import java.util.*

class ChoiceController(
    val stage: Stage,
    val title: String,
    private val content: String,
    private val options: List<Option>
): Initializable {

    @FXML
    lateinit var vboxMain: VBox
    @FXML
    lateinit var lbTitle: Label
    @FXML
    lateinit var lbContent: Label
    @FXML
    lateinit var button1: Button
    @FXML
    lateinit var button2: Button
    @FXML
    lateinit var button3: Button
    @FXML
    lateinit var button4: Button

    override fun initialize(p0: URL, p1: ResourceBundle?) {
        if (title.isNotEmpty()) {
            lbTitle.text = title
        } else {
            vboxMain.children.removeAll(lbTitle)
        }
        if (content.isNotEmpty()) {
            lbContent.text = content
        } else {
            vboxMain.children.remove(lbContent)
        }
        vboxMain.children.removeAll(button1, button2, button3, button4)
        for ((index, o) in options.withIndex()) {
            when (index) {
                0 -> {
                    vboxMain.children.add(button1)
                    button1.text = o.title
                    button1.onAction = EventHandler {
                        o.runnable?.run()
                        stage.close()
                    }
                }
                1 -> {
                    vboxMain.children.add(button2)
                    button2.text = o.title
                    button2.onAction = EventHandler {
                        o.runnable?.run()
                        stage.close()
                    }
                }
                2 -> {
                    vboxMain.children.add(button3)
                    button3.text = o.title
                    button3.onAction = EventHandler {
                        o.runnable?.run()
                        stage.close()
                    }
                }
                3 -> {
                    vboxMain.children.add(button4)
                    button4.text = o.title
                    button4.onAction = EventHandler {
                        o.runnable?.run()
                        stage.close()
                    }
                }
            }
        }
    }
}