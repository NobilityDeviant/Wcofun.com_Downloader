package com.nobility.downloader.series

import com.nobility.downloader.DriverBase
import com.nobility.downloader.Model
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.cache.Genre
import com.nobility.downloader.cache.Series
import com.nobility.downloader.utils.Resource
import com.nobility.downloader.utils.Tools
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class SeriesDetailsController : DriverBase(), Initializable {

    private val taskScope = CoroutineScope(Dispatchers.Default)

    private lateinit var stage: Stage
    private lateinit var seriesLink: String

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

    fun setup(model: Model, stage: Stage, seriesLink: String) {
        this.model = model
        this.stage = stage
        this.seriesLink = seriesLink
        image.fitHeight = stage.height / 2.5
        stage.heightProperty().addListener { _, _, _ ->
            run {
                image.fitHeight = stage.height / 2.5
            }
        }
        stage.widthProperty().addListener { _, _, _ ->
            run {
                image.fitWidth = stage.width
            }
        }
        stage.onCloseRequest = EventHandler {
            killDriver()
            taskScope.cancel()
            stage.close()
        }
        val series = model.history().seriesHistoryForLink(seriesLink)
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
            if (!resource.message.isNullOrEmpty()) {
                withContext(Dispatchers.JavaFx) {
                    model.toast(resource.message, stage)
                }
            }
        }
    }

    private suspend fun updateUI(series: Series) {
        withContext(Dispatchers.IO) {
            loadImageFromURL(series.imageLink)
        }
        withContext(Dispatchers.JavaFx) {
            title.text = series.name
            desc.text = series.description
            if (series.genres != null) {
                for (genre in series.genres) {
                    val button = Button()
                    button.text = genre.name
                    button.textAlignment = TextAlignment.CENTER
                    button.prefHeight = 50.0
                    button.prefWidth = 120.0
                    button.tooltip = Tooltip(genre.name)
                    button.setOnAction {
                        model.showLinkPrompt(genre.link, true)
                    }
                    genresHbox.children.add(button)
                    HBox.setMargin(button, Insets(0.0, 5.0, 0.0, 5.0))
                }
            } else {
                genresTitle.isVisible = false
                genresHbox.isVisible = false
            }
        }
    }

    override fun initialize(location: URL, resources: ResourceBundle?) {}

    private suspend fun loadSeries(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            driver.get(seriesLink)
            val doc = Jsoup.parse(driver.pageSource)
            val loaded = doc.getElementsByClass("recent-release")
            if (loaded.text().lowercase().contains("page not found")) {
                throw Exception("Series doesn't exist.")
            }
            val videoTitle = doc.getElementsByClass("video-title")
            val categoryEpisodes = doc.getElementsByClass("cat-eps")
            if (categoryEpisodes.isNotEmpty()) {
                val episodes = ArrayList<Episode>()
                categoryEpisodes.reverse()
                for (element in categoryEpisodes) {
                    val title = element.select("a").text()
                    val link = element.select("a").attr("href")
                    val episode = Episode(
                        Tools.fixTitle(title),
                        link,
                        seriesLink
                    )
                    episodes.add(episode)
                }
                var title = ""
                if (videoTitle.isNotEmpty()) {
                    title = Tools.fixTitle(videoTitle[0].text())
                }
                val image = doc.getElementsByClass("img5")
                var imageLink = ""
                if (image.isNotEmpty()) {
                    imageLink = "https:${image.attr("src")}"
                }
                var descriptionText = ""
                val description = doc.getElementsByTag("p")
                if (description.isNotEmpty()) {
                    descriptionText = description[0].text()
                }
                val genres = doc.getElementsByClass("genre-buton")
                val genresList = mutableListOf<Genre>()
                if (genres.isNotEmpty()) {
                    for (genre in genres) {
                        val link = genre.attr("href")
                        if (link.contains("search-by-genre")) {
                            genresList.add(
                                Genre(
                                    genre.text(),
                                    link
                                )
                            )
                        }
                    }
                }
                val series = Series(
                    seriesLink,
                    title,
                    imageLink,
                    descriptionText,
                    episodes,
                    genresList,
                    Tools.dateFormatted
                )
                updateUI(series)
                val added = model.history().addSeries(series, true)
                if (added) {
                    model.saveSeriesHistory()
                }
                return@withContext Resource.Success(true)
            } else {
                return@withContext Resource.Error("Failed to find any details for this series.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Resource.Error("Failed to load series. Error: " + e.localizedMessage)
        }
    }

    @FXML
    fun visitUrl() {
        model.showLinkPrompt(seriesLink, true)
    }

    @FXML
    fun copyUrl() {
        model.showCopyPrompt(seriesLink, false)
    }

    @FXML
    fun downloadSeries() {
        if (model.isRunning) {
            model.showError("You can't download a series while the downloader is running.")
            return
        }
        model.urlTextField.text = seriesLink
        model.start()
        model.showMessage("Started download", "Launched video downloader for: $seriesLink")
    }

    private suspend fun loadImageFromURL(url: String) {
        try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.setRequestProperty("user-agent", model.randomUserAgent)
            con.readTimeout = 10000
            con.connectTimeout = 10000
            con.connect()
            withContext(Dispatchers.JavaFx) {
                try {
                    this@SeriesDetailsController.image.image = Image(con.inputStream)
                    this@SeriesDetailsController.image.setOnMouseClicked {
                        model.showLinkPrompt(
                            url,
                            "Would you like to open this image in your default browser?",
                            true
                        )
                    }
                } catch (e: IOException) {
                    System.err.println(
                        "Failed to load series image for: " +
                                "$seriesLink Error: ${e.localizedMessage}"
                    )
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "Failed to load series image for: " +
                        "$seriesLink Error: ${e.localizedMessage}"
            )
        }
    }
}