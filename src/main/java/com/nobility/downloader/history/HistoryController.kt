package com.nobility.downloader.history

import com.nobility.downloader.Model
import com.nobility.downloader.entities.Series
import com.nobility.downloader.scraper.BuddyHandler
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.MouseEvent
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class HistoryController(private val model: Model, private val stage: Stage) : Initializable {
    
    @FXML
    lateinit var table: TableView<Series>

    @FXML
    lateinit var nameColumn: TableColumn<Series, String>

    @FXML
    lateinit var linkColumn: TableColumn<Series, String>

    @FXML
    lateinit var episodesColumn: TableColumn<Series, String>

    @FXML
    lateinit var dateColumn: TableColumn<Series, String>
    private val taskScope = CoroutineScope(Dispatchers.IO)
    private var checkingSize = 0
    
    override fun initialize(location: URL, resources: ResourceBundle?) {
        stage.onCloseRequest = EventHandler {
            taskScope.cancel()
        }
        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        stage.widthProperty()
            .addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? -> table.refresh() }

        nameColumn.maxWidth = (1f * Int.MAX_VALUE * 25).toDouble()
        linkColumn.maxWidth = (1f * Int.MAX_VALUE * 40).toDouble()
        episodesColumn.maxWidth = (1f * Int.MAX_VALUE * 7).toDouble()
        dateColumn.maxWidth = (1f * Int.MAX_VALUE * 20).toDouble()
        dateColumn.setCellValueFactory {
            SimpleStringProperty(it.value.dateAdded)
        }
        dateColumn.sortType = TableColumn.SortType.DESCENDING
        table.sortOrder.add(dateColumn)
        val dateComparator = Comparator { o1: String?, o2: String? ->
            val sdf = SimpleDateFormat(Tools.dateFormat)
            try {
                val date1 = sdf.parse(o1)
                val date2 = sdf.parse(o2)
                return@Comparator date1.compareTo(date2)
            } catch (ignored: Exception) {
            }
            0
        }
        dateColumn.comparator = dateComparator
        table.setRowFactory {
            val row = TableRow<Series>()
            val menu = ContextMenu()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (isEmpty == false) {
                        val seriesDetails = MenuItem("Series Details")
                        seriesDetails.onAction =
                            EventHandler { model.openSeriesDetails(series = row.item) }
                        val openLink = MenuItem("Open Series Link")
                        openLink.onAction =
                            EventHandler { model.showLinkPrompt(
                                model.linkForSlug(row.item.slug),
                                true
                            ) }
                        val copyLink = MenuItem("Copy Series Link")
                        copyLink.onAction =
                            EventHandler { model.showCopyPrompt(model.linkForSlug(row.item.slug), false, stage) }
                        val downloadSeries = MenuItem("Download Series")
                        downloadSeries.onAction = EventHandler {
                            if (model.isRunning) {
                                model.openDownloadConfirm(row.item, null)
                                return@EventHandler
                            }
                            model.urlTextField.text = model.linkForSlug(row.item.slug)
                            model.start()
                        }
                        val checkForNewEpisodes = MenuItem("Check For New Episodes")
                        checkForNewEpisodes.onAction = EventHandler {
                            if (checkingSize >= 3) {
                                model.showError("You can't check for new updates for more than 3 series at a time." +
                                        "\nPlease wait for the others to finish.")
                                return@EventHandler
                            }
                            model.toast("Checking for new episodes... Please wait.", stage)
                            checkingSize++
                            val seriesHistory = model.settings().seriesForSlug(row.item.slug)
                            val seriesWco = model.settings().wcoHandler.seriesForSlug(row.item.slug)
                            if (seriesHistory == null) {
                                model.toast("Series not found in history. Check the console.")
                                println("Series not found in history. Please download it again. " +
                                        "Link: ${model.linkForSlug(row.item.slug)}")
                                return@EventHandler
                            }
                            taskScope.launch {
                                val buddyHandler = BuddyHandler(model)
                                val result = buddyHandler.checkForNewEpisodes(seriesHistory)
                                if (result.data != null) {
                                    val updatedEpisodes = result.data.updatedEpisodes
                                    seriesHistory.updateEpisodes(updatedEpisodes, true)
                                    //optional wco db update
                                    seriesWco?.updateEpisodes(updatedEpisodes, true)
                                    row.item.updateEpisodes(updatedEpisodes)
                                    buddyHandler.cancel()
                                    checkingSize--
                                    withContext(Dispatchers.JavaFx) {
                                        row.item.updateEpisodeCountValue()
                                        model.showMessage(
                                            "New episodes found!",
                                            "Found ${result.data.newEpisodes.size} new episode(s) for \n${row.item.name}."
                                        )
                                    }
                                } else {
                                    checkingSize--
                                    withContext(Dispatchers.JavaFx) {
                                        model.toast("No new episodes found for \n${row.item.name}.", stage)
                                    }
                                }
                            }
                        }
                        val removeFromList = MenuItem("Remove From List")
                        removeFromList.onAction = EventHandler {
                            model.settings().removeSeries(row.item)
                            table.items.remove(row.item)
                            table.sort()
                        }
                        menu.items.addAll(
                            seriesDetails,
                            openLink, copyLink,
                            downloadSeries,
                            checkForNewEpisodes,
                            removeFromList
                        )
                        row.contextMenu = menu
                        row.onMouseClicked = EventHandler { event: MouseEvent ->
                            if (model.settings().booleanSetting(Defaults.SHOWCONTEXTONCLICK)) {
                                menu.show(stage, event.screenX, event.screenY)
                            }
                        }
                    }
                }
            row
        }
        nameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.name)
        }
        episodesColumn.cellValueFactory = PropertyValueFactory("episodesCount")
        linkColumn.setCellValueFactory {
            SimpleStringProperty(model.linkForSlug(it.value.slug))
        }
        for (s in model.settings().seriesBox.all) {
            s.updateEpisodeCountValue()
            table.items.add(s)
        }

        table.sort()
        table.requestFocus()
    }
}