package com.nobility.downloader.scraper

import com.nobility.downloader.Main
import com.nobility.downloader.Model
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Series
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class DownloadConfirmController(
    private val model: Model,
    private val stage: Stage,
    private val series: Series,
    private val episode: Episode?
) : Initializable {

    @FXML
    private lateinit var image: ImageView

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var desc: TextArea

    @FXML
    private lateinit var episodesTable: TableView<DownloadEpisode>

    @FXML
    private lateinit var nameColumn: TableColumn<DownloadEpisode, String>

    @FXML
    private lateinit var selectColumn: TableColumn<DownloadEpisode, CheckBox>

    @FXML
    private lateinit var downloadButton: Button

    @FXML
    private lateinit var checkButton: Button

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val selectedEpisodes = ArrayList<Episode>()

    override fun initialize(url: URL, resourceBundle: ResourceBundle?) {
        stage.onCloseRequest = EventHandler {
            ioScope.cancel()
        }
        episodesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        nameColumn.maxWidth = (1f * Int.MAX_VALUE * 90).toDouble()
        selectColumn.maxWidth = (1f * Int.MAX_VALUE * 10).toDouble()
        stage.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
            episodesTable.refresh()
        }
        nameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.episode.name)
        }

        selectColumn.setCellValueFactory {
            val checkBox = CheckBox()
            checkBox.alignment = Pos.CENTER
            checkBox.stylesheets.add(Model.CSS_PATH + "checkbox.css")
            checkBox.selectedProperty().addListener { _, _, newValue ->
                if (newValue == true) {
                    selectEpisode(it.value.episode)
                } else {
                    unselectEpisode(it.value.episode)
                }
                it.value.selected = newValue
            }
            checkBox.isSelected = it.value.selected
            return@setCellValueFactory SimpleObjectProperty(checkBox)
        }
        episodesTable.sortOrder.add(nameColumn)
        episodesTable.placeholder = Label("No episodes found for this series")
        nameColumn.comparator = Tools.mainEpisodesComparator
        image.fitHeight = stage.height / 3
        stage.heightProperty().addListener { _, _, _ ->
            run {
                image.fitHeight = stage.height / 3
            }
        }
        stage.widthProperty().addListener { _, _, _ ->
            run {
                image.fitWidth = stage.width
            }
        }
        loadSeriesImage()
        title.text = series.name
        desc.text = series.description
        for (e in series.episodes) {
            if (episode != null) {
                val selected = e.matches(episode)
                val download = DownloadEpisode(e, selected)
                episodesTable.items.add(download)
                if (selected) {
                    selectedEpisodes.add(e)
                    episodesTable.scrollTo(download)
                }
            } else {
                episodesTable.items.add(DownloadEpisode(e, true))
                selectedEpisodes.add(e)
            }
        }
        episodesTable.sort()
    }

    @FXML
    fun downloadSeries() {
        if (model.isRunning) {
            model.toast("You can't use this while the downloader is running.")
            return
        }
        if (selectedEpisodes.isEmpty()) {
            model.toast("You must select at least one episode to download.", stage)
            return
        }
        model.softStart()
        //must use an outside scope because closing this window
        //will cancel the local coroutine
        model.taskScope.launch(Dispatchers.IO) {
            model.episodes.addAll(selectedEpisodes)
            try {
                var threads = model.settings().integerSetting(Defaults.DOWNLOADTHREADS)
                if (model.episodes.size < threads) {
                    threads = model.episodes.size
                }
                val tasks = mutableListOf<Job>()
                for (i in 1..threads) {
                    tasks.add(
                        launch {
                            val downloader = VideoDownloader(model)
                            try {
                                downloader.run()
                            } catch (e: Exception) {
                                downloader.killDriver()
                                downloader.taskScope.cancel()
                                if (e.localizedMessage.contains("unknown error: cannot find")) {
                                    println("VideoDownloader error. Unable to find your browser. Be sure to set it in the settings before downloading anything.")
                                } else {
                                    println("VideoDownloader error: " + e.localizedMessage)
                                }
                            }
                        }
                    )
                }
                tasks.joinAll()
                if (model.downloadsFinishedForSession > 0) {
                    println("Gracefully finished downloading all files.")
                } else {
                    println("Gracefully shutdown. No downloads have been made.")
                }
                withContext(Dispatchers.JavaFx) {
                    model.stop()
                }
            } catch (e: Exception) {
                model.stop()
                println("Download service error: " + e.localizedMessage)
            }
        }
        model.toast("Successfully launched video downloader for ${selectedEpisodes.size} episode(s).")
        stage.close()
    }

    @FXML
    fun checkForNewEpisodes() {
        downloadButton.isDisable = true
        checkButton.isDisable = true
        model.toast("Checking for new episodes... Please wait.", stage)
        ioScope.launch {
            val buddyHandler = BuddyHandler(model)
            val result = buddyHandler.checkForNewEpisodes(series)
            if (result.data != null) {
                for (e in result.data.newEpisodes) {
                    episodesTable.items.add(DownloadEpisode(e, true))
                    if (!selectedEpisodes.contains(e)) {
                        selectedEpisodes.add(e)
                    }
                }
                episodesTable.refresh()
                val seriesHistory = model.settings().seriesForLink(series.link)
                val seriesWco = model.settings().wcoHandler.seriesForLink(series.link)
                if (seriesHistory != null) {
                    seriesHistory.episodes.clear()
                    seriesHistory.episodes.addAll(result.data.episodes)
                    seriesHistory.episodes.applyChangesToDb()
                }
                if (seriesWco != null) {
                    seriesWco.episodes.clear()
                    seriesWco.episodes.addAll(result.data.episodes)
                    seriesWco.episodes.applyChangesToDb()
                }
                buddyHandler.kill()
                withContext(Dispatchers.JavaFx) {
                    downloadButton.isDisable = false
                    checkButton.isDisable = false
                    model.showMessage(
                        "New episodes found!",
                        "Found ${result.data.newEpisodes.size} new episode(s). They have been selected by default."
                    )
                }
            } else {
                withContext(Dispatchers.JavaFx) {
                    downloadButton.isDisable = false
                    checkButton.isDisable = false
                    model.showMessage(
                        "Up to date!",
                        "This series is up to date. No new episodes have been found.",
                    )
                }
            }
        }
    }

    @FXML
    fun selectAll() {
        for (e in episodesTable.items) {
            e.selected = true
        }
        selectedEpisodes.clear()
        for (e in episodesTable.items) {
            selectedEpisodes.add(e.episode)
        }
        episodesTable.refresh()
    }

    @FXML
    fun deselectAll() {
        for (e in episodesTable.items) {
            e.selected = false
        }
        selectedEpisodes.clear()
        episodesTable.refresh()
    }

    private fun selectEpisode(episode: Episode) {
        if (indexForSelectedEpisode(episode) == -1) {
            selectedEpisodes.add(episode)
        }
    }

    private fun unselectEpisode(episode: Episode) {
        val index = indexForSelectedEpisode(episode)
        if (index != -1) {
            selectedEpisodes.removeAt(index)
        }
    }

    private fun indexForSelectedEpisode(episode: Episode): Int {
        for ((index, e) in selectedEpisodes.withIndex()) {
            if (e.matches(episode)) {
                return index
            }
        }
        return -1
    }

    private fun loadSeriesImage() {
        try {
            val file = File(  model.settings().wcoHandler.seriesImagesPath +
                    Tools.titleForImages(series.name))
            if (file.exists()) {
                image.image = Image(
                    file.inputStream()
                )
            } else {
                throw Exception("")
            }
        } catch (_: Exception) {
            ioScope.launch {
                loadImageFromURL(series.imageLink)
            }
        }
    }

    private suspend fun loadImageFromURL(imageLink: String) {
        try {
            val con = URL(imageLink).openConnection() as HttpURLConnection
            con.setRequestProperty("user-agent", model.randomUserAgent)
            con.readTimeout = 10000
            con.connectTimeout = 10000
            con.connect()
            withContext(Dispatchers.JavaFx) {
                try {
                    this@DownloadConfirmController.image.image = Image(con.inputStream)
                    this@DownloadConfirmController.image.setOnMouseClicked {
                        model.showLinkPrompt(
                            imageLink,
                            "Would you like to open this image in your default browser?",
                            true
                        )
                    }
                } catch (e: IOException) {
                    System.err.println(
                        "Failed to load series image for: " +
                                "${series.link} Error: ${e.localizedMessage}"
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "Failed to load series image for: " +
                        "${series.link} Error: ${e.localizedMessage}"
            )
            withContext(Dispatchers.JavaFx) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    image.image = Image(icon)
                }
            }
        }
    }
}