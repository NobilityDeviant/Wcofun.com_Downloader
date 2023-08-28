package com.nobility.downloader.wco

import com.nobility.downloader.Main
import com.nobility.downloader.Model
import com.nobility.downloader.entities.Series
import com.nobility.downloader.entities.SeriesIdentity
import com.nobility.downloader.entities.Series_
import com.nobility.downloader.scraper.BuddyHandler
import com.nobility.downloader.scraper.RecentResult
import com.nobility.downloader.scraper.RecentScraper
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Option
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.loadImageFromURL
import io.objectbox.query.Query
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import javafx.scene.text.TextFlow
import javafx.stage.Stage
import javafx.util.Callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*


class WcoController : Initializable {

    @FXML
    private lateinit var seriesTable: TableView<Series>

    @FXML
    private lateinit var coverColumn: TableColumn<Series, ImageView>

    @FXML
    private lateinit var nameColumn: TableColumn<Series, TextFlow>

    @FXML
    private lateinit var descriptionColumn: TableColumn<Series, TextArea>

    @FXML
    private lateinit var genresColumn: TableColumn<Series, TextFlow>

    @FXML
    private lateinit var identityColumn: TableColumn<Series, TextFlow>

    @FXML
    private lateinit var episodesColumn: TableColumn<Series, TextFlow>

    @FXML
    private lateinit var searchTextField: TextField

    @FXML
    private lateinit var resultsLabel: Label

    @FXML
    private lateinit var dubbedButton: Button

    @FXML
    private lateinit var subbedButton: Button

    @FXML
    private lateinit var cartoonButton: Button

    @FXML
    private lateinit var movieButton: Button

    @FXML
    private lateinit var recentButton: Button

    @FXML
    private lateinit var uncategorizedButton: Button
    private lateinit var model: Model
    private lateinit var stage: Stage
    private var filteredData: FilteredList<Series>? = null
    private val taskScope = CoroutineScope(Dispatchers.IO)
    private val rowHeight = 170.0
    private var checkingSize = 0
    private var loading = false
    private var currentPage = -1

    fun setup(model: Model, stage: Stage) {
        this.model = model
        this.stage = stage
        seriesTable.setRowFactory {
            val row = TableRow<Series>()
            val menu = ContextMenu()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        val seriesDetails =
                            MenuItem("Series Details")
                        seriesDetails.onAction =
                            EventHandler {
                                model.openSeriesDetails(
                                    series = row.item
                                )
                            }
                        val openLink =
                            MenuItem("Open Series Link")
                        openLink.onAction =
                            EventHandler {
                                model.showLinkPrompt(
                                    model.linkForSlug(row.item.slug),
                                    true
                                )
                            }
                        val copyLink =
                            MenuItem("Copy Series Link")
                        copyLink.onAction =
                            EventHandler {
                                model.showCopyPrompt(
                                    model.linkForSlug(row.item.slug),
                                    false,
                                    stage
                                )
                            }
                        val downloadSeries =
                            MenuItem("Download Series")
                        downloadSeries.onAction =
                            EventHandler {
                                if (model.isRunning) {
                                    model.openDownloadConfirm(row.item, null)
                                    return@EventHandler
                                }
                                model.urlTextField.text = model.linkForSlug(row.item.slug)
                                model.start()
                            }
                        val checkForNewEpisodes =
                            MenuItem("Check For New Episodes")
                        checkForNewEpisodes.onAction =
                            EventHandler {
                                if (checkingSize >= 3) {
                                    model.showError(
                                        "You can't check for new updates for more than 3 series at a time." +
                                                "\nPlease wait for the others to finish."
                                    )
                                    return@EventHandler
                                }
                                model.toast("Checking for new episodes... Please wait.", stage)
                                checkingSize++
                                taskScope.launch {
                                    val buddyHandler = BuddyHandler(model)
                                    val result = buddyHandler.checkForNewEpisodes(row.item)
                                    if (result.data != null) {
                                        val updatedEpisodes = result.data.updatedEpisodes
                                        val seriesHistory = model.settings().seriesForSlug(row.item.slug)
                                        val seriesWco = model.settings().wcoHandler.seriesForSlug(row.item.slug)
                                        seriesHistory?.updateEpisodes(updatedEpisodes, true)
                                        seriesWco?.updateEpisodes(updatedEpisodes, true)
                                        row.item.updateEpisodes(updatedEpisodes)
                                        withContext(Dispatchers.JavaFx) {
                                            row.item.updateEpisodeCountValue()
                                            model.showMessage(
                                                "New episodes found!",
                                                "Found ${result.data.newEpisodes.size} new episode(s) for \n${row.item.name}."
                                            )
                                        }
                                    } else {
                                        withContext(Dispatchers.JavaFx) {
                                            model.toast("No new episodes found for \n${row.item.name}.", stage)
                                        }
                                    }
                                    buddyHandler.cancel()
                                    checkingSize--
                                }
                            }
                        val updateSeriesDetails = MenuItem("Update Details")
                        updateSeriesDetails.onAction = EventHandler {
                            if (checkingSize >= 3) {
                                model.showError(
                                    "You can't check for new updates for more than 3 series at a time." +
                                            "\nPlease wait for the others to finish."
                                )
                                return@EventHandler
                            }
                            model.toast("Updating Series Details... Please wait.", stage)
                            checkingSize++
                            taskScope.launch {
                                val buddyHandler = BuddyHandler(model)
                                val result = buddyHandler.updateSeriesDetails(row.item)
                                if (result.data != null) {
                                    withContext(Dispatchers.JavaFx) {
                                        row.item.update(result.data)
                                        row.item.updateEpisodeCountValue()
                                        model.toast("Successfully updated series: ${row.item.name}.", stage)
                                    }
                                } else {
                                    withContext(Dispatchers.JavaFx) {
                                        if (result.message != "Series is already updated.") {
                                            model.showError("Failed to update details for ${row.item.name}. Error: ${result.message}")
                                        } else {
                                            model.toast("Series: ${row.item.name} is already up to date.", stage)
                                        }
                                    }
                                }
                                buddyHandler.cancel()
                                checkingSize--
                            }
                        }
                        menu.items.clear()
                        menu.items.addAll(
                            seriesDetails,
                            openLink,
                            copyLink,
                            downloadSeries,
                            checkForNewEpisodes,
                            updateSeriesDetails
                        )

                        val moveToCategory = MenuItem("Move To Category")
                        moveToCategory.onAction = EventHandler {
                            model.showChoice(
                                "Move To Category",
                                "You have the option to label this series.",
                                Option("Dubbbed") {
                                    val series = row.item
                                    val identity = SeriesIdentity.DUBBED
                                    series.identity = identity.type
                                    model.settings().wcoHandler.addOrUpdateSeries(series)
                                    model.settings().addOrUpdateCategoryLinkWithSlug(series.slug, identity)
                                    model.toast("Successfully changed identity to Dubbed.", stage)
                                },
                                Option("Subbed") {
                                    val series = row.item
                                    val identity = SeriesIdentity.SUBBED
                                    series.identity = identity.type
                                    model.settings().wcoHandler.addOrUpdateSeries(series)
                                    model.settings().addOrUpdateCategoryLinkWithSlug(series.slug, identity)
                                    model.toast("Successfully changed identity to Subbed.", stage)
                                },
                                Option("Cartoon") {
                                    val series = row.item
                                    val identity = SeriesIdentity.CARTOON
                                    series.identity = identity.type
                                    model.settings().wcoHandler.addOrUpdateSeries(series)
                                    model.settings().addOrUpdateCategoryLinkWithSlug(series.slug, identity)
                                    model.toast("Successfully changed identity to Cartoon.", stage)
                                },
                                Option("Movie") {
                                    val series = row.item
                                    val identity = SeriesIdentity.MOVIE
                                    series.identity = identity.type
                                    model.settings().wcoHandler.addOrUpdateSeries(series)
                                    model.settings().addOrUpdateCategoryLinkWithSlug(series.slug, identity)
                                    model.toast("Successfully changed identity to Movie.", stage)
                                }
                            )
                        }
                        menu.items.add(moveToCategory)
                        if (model.developerMode) {
                            val remove = MenuItem("Remove From DB")
                            remove.onAction = EventHandler {
                                model.settings().wcoHandler.seriesBox.remove(row.item)
                                model.toast("Successfully removed series from the database.", stage)
                            }
                            menu.items.add(remove)
                            val clearEpisodes = MenuItem("Remove All Episodes")
                            clearEpisodes.onAction = EventHandler {
                                row.item.episodes.clear()
                                row.item.episodes.applyChangesToDb()
                                model.toast("Successfully deleted all episodes from series.", stage)
                            }
                            menu.items.add(clearEpisodes)
                            val clearGenres = MenuItem("Remove All Genres")
                            clearGenres.onAction = EventHandler {
                                row.item.genres.clear()
                                row.item.genres.applyChangesToDb()
                                model.toast("Successfully deleted all genres from series.", stage)
                            }
                            menu.items.add(clearGenres)
                        }
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

        nameColumn.comparator = Tools.abcTextFlowComparator
        seriesTable.sortOrder.add(nameColumn)
        seriesTable.placeholder = Label("Click one of the buttons below to load saved series.")
        seriesTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        seriesTable.fixedCellSize = rowHeight
        coverColumn.maxWidth = (1f * Int.MAX_VALUE * 20).toDouble()
        nameColumn.maxWidth = (1f * Int.MAX_VALUE * 30).toDouble()
        descriptionColumn.maxWidth = (1f * Int.MAX_VALUE * 20).toDouble()
        genresColumn.maxWidth = (1f * Int.MAX_VALUE * 15).toDouble()
        identityColumn.maxWidth = (1f * Int.MAX_VALUE * 10).toDouble()
        episodesColumn.maxWidth = (1f * Int.MAX_VALUE * 10).toDouble()
        coverColumn.setCellValueFactory {
            val image = ImageView()
            image.fitHeight = rowHeight
            image.fitWidth = coverColumn.width
            coverColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                image.fitWidth = coverColumn.width
            }
            try {
                val file = File(
                    model.settings().wcoHandler.seriesImagesPath +
                            Tools.titleForImages(it.value.name)
                )
                if (file.exists()) {
                    image.image = Image(
                        file.inputStream()
                    )
                } else {
                    if (!it.value.imageLink.isNullOrEmpty()) {
                        taskScope.launch {
                            model.settings().wcoHandler.downloadSeriesImage(it.value)
                            loadImageFromURL(model, it.value.imageLink, image)
                        }
                    }
                }
            } catch (_: Exception) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    image.image = Image(icon)
                }
            }
            return@setCellValueFactory SimpleObjectProperty(image)
        }
        nameColumn.setCellValueFactory {
            val text = Text(it.value.name)
            text.fill = Color.WHITE
            val textFlow = TextFlow(text)
            textFlow.textAlignment = TextAlignment.CENTER
            nameColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                textFlow.prefWidth = nameColumn.width
            }
            return@setCellValueFactory SimpleObjectProperty(textFlow)
        }
        identityColumn.setCellValueFactory {
            val link = Hyperlink()
            link.textFill = Color.WHITE
            link.isWrapText = true
            val identity = SeriesIdentity.idForType(it.value.identity)
            link.text = identity.toString()
            link.tooltip = Tooltip(identity.toString())
            link.setOnAction {
                model.showLinkPrompt(
                    model.linkForSlug(identity.slug),
                    true
                )
            }
            val textFlow = TextFlow(link)
            textFlow.textAlignment = TextAlignment.CENTER
            identityColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                textFlow.prefWidth = identityColumn.width
            }
            return@setCellValueFactory SimpleObjectProperty(textFlow)
        }
        genresColumn.setCellValueFactory {
            val textFlow = TextFlow()
            textFlow.textAlignment = TextAlignment.CENTER
            genresColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                textFlow.prefWidth = genresColumn.width
            }
            for (g in it.value.genres) {
                val link = Hyperlink(g.name)
                link.textFill = Color.WHITE
                link.setOnAction {
                    model.showChoice(
                        "Genre Options",
                        "",
                        Option("Search For ${g.name}") {
                            searchTextField.text = g.name
                            search()
                        },
                        Option("Open Link") {
                            model.showLinkPrompt(
                                model.linkForSlug(g.slug),
                                true
                            )
                        }
                    )
                }
                textFlow.children.add(link)
            }
            return@setCellValueFactory SimpleObjectProperty(textFlow)
        }
        descriptionColumn.setCellValueFactory {
            val textArea = TextArea()
            textArea.isWrapText = true
            textArea.prefWidth = descriptionColumn.width
            textArea.prefHeight = rowHeight
            textArea.stylesheets.add(
                Main::class.java.getResource(
                    Model.CSS_PATH + "textarea.css"
                )?.toString() ?: ""
            )
            textArea.text = it.value.description
            return@setCellValueFactory SimpleObjectProperty(textArea)
        }
        episodesColumn.setCellValueFactory {
            val text = Text(it.value.episodes.size.toString())
            text.textProperty().bindBidirectional(it.value.episodesCountProperty())
            text.fill = Color.WHITE
            val textFlow = TextFlow(text)
            textFlow.textAlignment = TextAlignment.CENTER
            episodesColumn.widthProperty().addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                textFlow.prefWidth = episodesColumn.width
            }
            return@setCellValueFactory SimpleObjectProperty(textFlow)
        }

        searchTextField.setOnKeyReleased { event ->
            if (event.code === KeyCode.ENTER) {
                search()
            }
        }
    }

    @FXML
    fun switchPage(e: ActionEvent) {
        if (loading) {
            model.toast("Please wait for the previous task to finish.", stage)
            return
        }
        when (e.source) {
            recentButton -> setRecent()
            dubbedButton -> setFilter(SeriesIdentity.DUBBED)
            subbedButton -> setFilter(SeriesIdentity.SUBBED)
            cartoonButton -> setFilter(SeriesIdentity.CARTOON)
            movieButton -> setFilter(SeriesIdentity.MOVIE)
            uncategorizedButton -> setFilter(SeriesIdentity.NONE, SeriesIdentity.NEW)
        }
    }

    private fun setRecent() {
        loading = true
        resultsLabel.text = "Checking for recent data..."
        taskScope.launch {
            val recentScraper = RecentScraper(model)
            val result = recentScraper.run()
            withContext(Dispatchers.JavaFx) {
                if (result.data != null) {
                    loading = false
                    openRecent(result.data.data)
                } else {
                    loading = false
                    resultsLabel.text = "Failed to retrieve recent data..."
                    model.showError(
                        "Failed to retrieve recent data. Error: ${result.message}",
                    )
                }
            }
        }
    }

    private val recentStage: Stage = Stage()

    private fun openRecent(data: List<RecentResult.Data>) {
        resultsLabel.text = "Opening recent window..."
        recentStage.title = "Recent"
        val icon = Main::class.java.getResourceAsStream(Model.SETTINGS_ICON)
        if (icon != null) {
            recentStage.icons.add(Image(icon))
        }
        recentStage.isResizable = true
        val loader = FXMLLoader(Main::class.java.getResource(Model.FX_PATH + "recent.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (
                        con.parameterCount == 3
                        && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                        && con.parameterTypes[2] == List::class.java
                    ) {
                        return@Callback con.newInstance(model, recentStage, data)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                resultsLabel.text = "Failed to open recent window..."
                println("Failed to load RecentController. Error: ${e.localizedMessage}")
                e.printStackTrace()
                return@Callback null
            }
        }
        val layout: Parent
        try {
            layout = loader.load()
            val scene = Scene(layout)
            scene.stylesheets.add(
                Main::class.java.getResource(
                    Model.CSS_PATH + "contextmenu.css"
                )?.toString() ?: ""
            )
            recentStage.toFront()
            recentStage.scene = scene
            recentStage.sizeToScene()
            recentStage.show()
            resultsLabel.text = ""
        } catch (e: IOException) {
            e.printStackTrace()
            resultsLabel.text = "Failed to open recent window..."
            println("Failed to open recent window. Error: ${e.localizedMessage}")
        }
    }

    private fun setFilter(vararg identities: SeriesIdentity) {
        if (currentPage == identities[0].type) {
            return
        }
        currentPage = identities[0].type
        seriesTable.items = FXCollections.observableArrayList(emptyList())
        loading = true
        resultsLabel.text = "Adding series from type..."
        taskScope.launch {
            var builder = model.settings().wcoHandler.seriesBox.query()
                .equal(Series_.identity, identities[0].type.toLong())
            if (identities.size > 1) {
                for (i in 1 until identities.size) {
                    builder = builder.or().equal(Series_.identity, identities[i].type.toLong())
                }
            }
            val query: Query<Series>
            try {
                query = builder.build()
                query.use {
                    val series = it.find()
                    if (series.isNotEmpty()) {
                        withContext(Dispatchers.JavaFx) {
                            setupData(series)
                            loading = false
                        }
                    } else {
                        throw Exception("No results found.")
                    }
                }
            } catch (e: Exception) {
                loading = false
                withContext(Dispatchers.JavaFx) {
                    resultsLabel.text = "Failed to query series."
                    model.showError("Failed to query series.", e)
                }
            }
        }
    }

    private fun setupData(seriesList: List<Series>) {
        val observableList = FXCollections.observableArrayList(seriesList)
        filteredData = FilteredList(observableList)
        val sortedData = SortedList(filteredData)
        sortedData.comparatorProperty().bind(seriesTable.comparatorProperty())
        filteredData!!.predicateProperty().addListener { _, _, _ ->
            resultsLabel.text = "${filteredData!!.size} results found"
        }
        resultsLabel.text = "${seriesList.size} results found"
        seriesTable.items = sortedData
        for (s in seriesTable.items) {
            s.updateEpisodeCountValue()
        }
        seriesTable.sortOrder.clear()
        seriesTable.sortOrder.add(nameColumn)
        seriesTable.sort()
    }

    fun search() {
        resultsLabel.text = "Searching..."
        if (filteredData == null) {
            resultsLabel.text = "Press one of the buttons to load some series first."
            return
        }
        filteredData!!.setPredicate {
            val search = searchTextField.text
            if (search.isNullOrEmpty()) {
                resultsLabel.text = "0 results found"
                return@setPredicate true
            }
            val filter: String = search.lowercase(Locale.getDefault())
            val genresFilter = it.genres.hasA { genre ->
                genre.name.lowercase(Locale.getDefault()).contains(filter)
            }
            val identity = SeriesIdentity.idForType(it.identity)
            (identity.toString().lowercase(Locale.getDefault()).contains(filter)
                    || genresFilter
                    || it.name.lowercase(Locale.getDefault()).contains(filter))
        }
    }

    override fun initialize(url: URL, resourceBundle: ResourceBundle?) {}
}