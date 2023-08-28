package com.nobility.downloader.scraper

import com.nobility.downloader.Main
import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.io.File
import java.net.URL
import java.util.*

class RecentController(
    val model: Model,
    val stage: Stage,
    val data: List<RecentResult.Data>
    ): Initializable {

    @FXML
    private lateinit var episodesTable: TableView<RecentResult.Data>
    @FXML
    private lateinit var episodeImageColumn: TableColumn<RecentResult.Data, ImageView>
    @FXML
    private lateinit var episodeNameColumn: TableColumn<RecentResult.Data, String>
    @FXML
    private lateinit var seriesTable: TableView<RecentResult.Data>
    @FXML
    private lateinit var seriesImageColumn: TableColumn<RecentResult.Data, ImageView>
    @FXML
    private lateinit var seriesNameColumn: TableColumn<RecentResult.Data, String>
    private val taskScope = CoroutineScope(Dispatchers.IO)
    private val rowHeight = 170.0


    override fun initialize(p0: URL, p1: ResourceBundle?) {
        stage.onCloseRequest = EventHandler {
            taskScope.cancel()
        }
        episodeNameColumn.comparator = Tools.mainEpisodesComparator
        episodesTable.sortOrder.add(episodeNameColumn)
        episodesTable.placeholder = Label("No recent episodes found.")
        episodesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        episodesTable.fixedCellSize = rowHeight
        episodeImageColumn.maxWidth = (1f * Int.MAX_VALUE * 40).toDouble()
        episodeNameColumn.maxWidth = (1f * Int.MAX_VALUE * 60).toDouble()

        seriesTable.setRowFactory {
            val row = TableRow<RecentResult.Data>()
            val menu = ContextMenu()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        menu.items.clear()
                        val seriesDetails =
                            MenuItem("Series Details")
                        seriesDetails.onAction =
                            EventHandler {
                                model.openSeriesDetails(
                                    Tools.extractSlugFromLink(row.item.link)
                                )
                            }
                        val openLink =
                            MenuItem("Open Series Link")
                        openLink.onAction =
                            EventHandler {
                                model.showLinkPrompt(
                                    row.item.link,
                                    true
                                )
                            }
                        val copyLink =
                            MenuItem("Copy Series Link")
                        copyLink.onAction =
                            EventHandler {
                                model.showCopyPrompt(
                                    row.item.link,
                                    false,
                                    stage
                                )
                            }
                        val downloadSeries =
                            MenuItem("Download Series")
                        downloadSeries.onAction =
                            EventHandler {
                                if (model.isRunning) {
                                    model.toast("You can't download recent series while the downloader is running.", stage)
                                    return@EventHandler
                                }
                                model.urlTextField.text = row.item.link
                                model.start()
                                model.toast("Launched video downloader for: ${row.item.link}", stage)
                            }
                        menu.items.addAll(
                            seriesDetails,
                            openLink,
                            copyLink,
                            downloadSeries
                        )
                        row.contextMenu = menu
                        row.onMouseClicked =
                            EventHandler { event: MouseEvent ->
                                if (model.settings()
                                        .booleanSetting(Defaults.SHOWCONTEXTONCLICK)
                                ) {
                                    menu.show(stage, event.screenX, event.screenY)
                                }
                            }
                    }
                }
            row
        }

        episodesTable.setRowFactory {
            val row = TableRow<RecentResult.Data>()
            val menu = ContextMenu()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        menu.items.clear()
                        val openLink =
                            MenuItem("Open Episode Link")
                        openLink.onAction =
                            EventHandler {
                                model.showLinkPrompt(
                                    row.item.link,
                                    true
                                )
                            }
                        val copyLink =
                            MenuItem("Copy Episode Link")
                        copyLink.onAction =
                            EventHandler {
                                model.showCopyPrompt(
                                    row.item.link,
                                    false,
                                    stage
                                )
                            }
                        val downloadEpisode =
                            MenuItem("Download Episode")
                        downloadEpisode.onAction =
                            EventHandler {
                                if (model.isRunning) {
                                    model.showError(
                                        "You can't download recent episodes while the downloader is running.",
                                    )
                                    return@EventHandler
                                }
                                model.urlTextField.text = row.item.link
                                model.start()
                                model.toast("Launched video downloader for: ${row.item.link}", stage)
                            }
                        menu.items.addAll(
                            openLink,
                            copyLink,
                            downloadEpisode
                        )
                        row.contextMenu = menu
                        row.onMouseClicked =
                            EventHandler { event: MouseEvent ->
                                if (model.settings()
                                        .booleanSetting(Defaults.SHOWCONTEXTONCLICK)
                                ) {
                                    menu.show(stage, event.screenX, event.screenY)
                                }
                            }
                    }
                }
            row
        }

        episodeImageColumn.setCellValueFactory {
            val image = ImageView()
            image.fitHeight = rowHeight
            image.fitWidth = episodeImageColumn.width

            episodeImageColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                image.fitWidth = episodeImageColumn.width
            }
            try {
                val file = File(it.value.imagePath)
                if (file.exists()) {
                    image.image = Image(
                        file.inputStream()
                    )
                } else {
                    throw Exception()
                }
            } catch (_: Exception) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    image.image = Image(icon)
                }
            }
            return@setCellValueFactory SimpleObjectProperty(image)
        }
        episodeNameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.name)
        }

        seriesNameColumn.comparator = Tools.seriesComparator
        seriesTable.sortOrder.add(seriesNameColumn)
        seriesTable.placeholder = Label("No recent series found.")
        seriesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        seriesTable.fixedCellSize = rowHeight
        seriesImageColumn.maxWidth = (1f * Int.MAX_VALUE * 40).toDouble()
        seriesNameColumn.maxWidth = (1f * Int.MAX_VALUE * 60).toDouble()

        seriesImageColumn.setCellValueFactory {
            val image = ImageView()
            image.fitHeight = rowHeight
            image.fitWidth = seriesImageColumn.width

            seriesImageColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                image.fitWidth = seriesImageColumn.width
            }
            try {
                val file = File(it.value.imagePath)
                if (file.exists()) {
                    image.image = Image(
                        file.inputStream()
                    )
                } else {
                    throw Exception()
                }
            } catch (_: Exception) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    image.image = Image(icon)
                }
            }
            return@setCellValueFactory SimpleObjectProperty(image)
        }
        seriesNameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.name)
        }
        val series = data.filter {
            it.isSeries
        }
        val episodes = data.filter {
            !it.isSeries
        }
        episodesTable.items.addAll(episodes)
        episodesTable.sort()
        seriesTable.items.addAll(series)
        seriesTable.sort()
    }
}