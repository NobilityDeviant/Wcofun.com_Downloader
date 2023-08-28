package com.nobility.downloader.series

import com.nobility.downloader.Main
import com.nobility.downloader.Model
import com.nobility.downloader.driver.DriverBase
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Series
import com.nobility.downloader.scraper.BuddyHandler
import com.nobility.downloader.scraper.SlugHandler
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Option
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.imageio.ImageIO

class SeriesDetailsController : DriverBase(), Initializable {

    private val taskScope = CoroutineScope(Dispatchers.IO)

    private lateinit var stage: Stage
    private lateinit var seriesSlug: String
    private lateinit var seriesLink: String
    private lateinit var series: Series
    private var updating = false

    @FXML
    private lateinit var image: ImageView

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var desc: TextArea

    @FXML
    private lateinit var genresHbox: HBox

    @FXML
    private lateinit var genresTitle: Label

    @FXML
    private lateinit var episodesTable: TableView<Episode>

    @FXML
    private lateinit var nameColumn: TableColumn<Episode, String>

    @FXML
    private lateinit var episodesLabel: Label

    fun setup(model: Model, stage: Stage, seriesSlug: String) {
        this.model = model
        this.stage = stage
        this.seriesSlug = seriesSlug
        this.seriesLink = model.linkForSlug(seriesSlug)
        episodesTable.setRowFactory {
            val row = TableRow<Episode>()
            val menu = ContextMenu()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        val episode = row.item
                        val openLink =
                            MenuItem("Open Episode Link")
                        openLink.onAction =
                            EventHandler {
                                model.showLinkPrompt(
                                    model.linkForSlug(episode.slug),
                                    true
                                )
                            }
                        val copyLink =
                            MenuItem("Copy Episode Link")
                        copyLink.onAction =
                            EventHandler {
                                model.showCopyPrompt(
                                    model.linkForSlug(episode.slug),
                                    false,
                                    stage
                                )
                            }
                        menu.items.addAll(
                            openLink,
                            copyLink,
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
        episodesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        stage.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
            episodesTable.refresh()
        }
        nameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.name)
        }
        episodesTable.sortOrder.add(nameColumn)
        episodesTable.placeholder = Label("No episodes found for this series.")
        nameColumn.comparator = Tools.mainEpisodesComparator
        stage.onCloseRequest = EventHandler {
            killDriver()
            taskScope.cancel()
            stage.close()
        }
        var series = model.settings().seriesForSlug(seriesSlug)
        if (series == null || series.episodes.isEmpty()) {
            series = model.settings().wcoHandler.seriesForSlug(seriesSlug)
        }
        if (series != null && series.hasImageAndDescription()) {
            taskScope.launch {
                updateUI(series)
            }
            return
        }
        taskScope.launch {
            withContext(Dispatchers.JavaFx) {
                model.toast("Setting up driver...", stage)
            }
            setupDriver()
            withContext(Dispatchers.JavaFx) {
                model.toast("Loading series...", stage)
            }
            val resource = loadSeries()
            killDriver()
            if (!resource.message.isNullOrEmpty()) {
                withContext(Dispatchers.JavaFx) {
                    model.toast(resource.message, stage)
                }
            }
        }
    }

    private suspend fun updateUI(series: Series) {
        this.series = series
        withContext(Dispatchers.IO) {
            loadSeriesImage(series)
        }
        withContext(Dispatchers.JavaFx) {
            title.text = series.name
            desc.text = series.description
            if (series.genres != null) {
                genresHbox.children.clear()
                for (genre in series.genres.distinctBy { it.name }) {
                    val button = Button()
                    button.text = genre.name
                    button.textAlignment = TextAlignment.CENTER
                    button.prefHeight = 50.0
                    button.prefWidth = 120.0
                    button.tooltip = Tooltip(genre.name)
                    button.setOnAction {
                        model.showLinkPrompt(model.linkForSlug(genre.slug), true)
                    }
                    genresHbox.children.add(button)
                    HBox.setMargin(
                        button,
                        Insets(
                            0.0,
                            5.0,
                            0.0,
                            5.0
                        )
                    )
                }
            } else {
                genresTitle.isVisible = false
                genresHbox.isVisible = false
            }
            episodesTable.items.clear()
            episodesLabel.text = "Episodes (${series.episodes.size})"
            episodesTable.items.addAll(
                FXCollections.observableArrayList(series.episodes)
            )
            episodesTable.sort()
            episodesTable.requestFocus()
        }
    }

    override fun initialize(location: URL, resources: ResourceBundle?) {}

    private suspend fun loadSeries(): Resource<Boolean> = withContext(Dispatchers.IO) {
        val slugHandler = SlugHandler(model)
        try {
            val result = slugHandler.handleSlug(seriesSlug, true)
            if (result.data is Series) {
                model.settings().addOrUpdateSeries(result.data)
                model.settings().wcoHandler.addOrUpdateSeries(result.data)
                updateUI(result.data)
                return@withContext Resource.Success(true)
            } else {
                return@withContext Resource.Error("Failed to load series. Error: ${result.message}")
            }
        } catch (e: Exception) {
            return@withContext Resource.Error("Failed to load series. Error: ${e.localizedMessage}")
        } finally {
            slugHandler.killDriver()
        }
    }

    @FXML
    fun urlOptions() {
        if (updating) {
            model.toast("Please wait for the update to finish.", stage)
            return
        }
        model.showChoice(
            "URL Options",
            "",
            Option("Copy URL") {
                model.showCopyPrompt(seriesLink, false, stage)
            },
            Option("Visit URL") {
                model.showLinkPrompt(seriesLink, true)
            }
        )
    }

    @FXML
    fun updateDetails() {
        if (updating) {
            model.toast("Please wait for the update to finish.", stage)
            return
        }
        updating = true
        model.toast("Updating Series Details... Please wait.", stage)
        taskScope.launch {
            val buddyHandler = BuddyHandler(model)
            val result = buddyHandler.updateSeriesDetails(series)
            buddyHandler.cancel()
            if (result.data != null) {
                withContext(Dispatchers.JavaFx) {
                    series.update(result.data)
                    updateUI(series)
                    model.toast("Successfully updated series: ${series.name}.", stage)
                }
            } else {
                withContext(Dispatchers.JavaFx) {
                    if (result.message != "Series is already updated.") {
                        model.showError("Failed to update details for ${series.name}. Error: ${result.message}")
                    } else {
                        model.toast("Series is already up to date.", stage)
                    }
                }
            }
            updating = false
        }
    }

    @FXML
    fun downloadSeries() {
        if (updating) {
            model.toast("Please wait for the update to finish.", stage)
            return
        }
        if (model.isRunning) {
            model.openDownloadConfirm(series, null)
            stage.close()
            return
        }
        model.urlTextField.text = seriesLink
        model.start()
        stage.close()
    }

    private fun loadSeriesImage(series: Series) {
        try {
            val file = File(
                model.settings().wcoHandler.seriesImagesPath +
                    Tools.titleForImages(series.name))
            if (file.exists()) {
                image.image = Image(
                    file.inputStream()
                )
                val bufferedImage = ImageIO.read(file)
                image.fitWidth = bufferedImage.width.toDouble()
            } else {
                throw Exception("")
            }
        } catch (_: Exception) {
            taskScope.launch {
                model.settings().wcoHandler.downloadSeriesImage(series)
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
                    val bufferedImage = ImageIO.read(con.inputStream)
                    image.fitWidth = bufferedImage.width.toDouble()
                    image.image = Image(con.inputStream)
                    image.setOnMouseClicked {
                        model.showLinkPrompt(
                            imageLink,
                            "Would you like to open this image in your default browser?",
                            true
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    System.err.println(
                        "Failed to load series image for: " +
                                "${series.name} Error: ${e.localizedMessage}"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println(
                "Failed to load series image for: " +
                        "${series.name} Error: ${e.localizedMessage}"
            )
            withContext(Dispatchers.JavaFx) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    image.image = Image(icon)
                    stage.widthProperty().addListener { _, _, _ ->
                        run {
                            image.fitWidth = stage.width
                        }
                    }
                }
            }
        }
    }
}