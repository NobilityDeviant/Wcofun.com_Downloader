package com.nobility.downloader

import com.nobility.downloader.downloads.Download
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.StringChecker
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.bytesToString
import com.nobility.downloader.utils.Tools.fixOldLink
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.Clipboard
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.Callback
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainController(private val model: Model) : Initializable {

    @FXML
    private lateinit var urlSubmission: TextField

    @FXML
    private lateinit var console: TextArea

    @FXML
    private lateinit var openDownloadFolder: MenuItem

    @FXML
    private lateinit var about: MenuItem

    @FXML
    private lateinit var openSettings: MenuItem

    @FXML
    private lateinit var updates: MenuItem

    @FXML
    private lateinit var openDownloadHistory: MenuItem

    @FXML
    private lateinit var openWebsite: MenuItem

    @FXML
    private lateinit var openGithub: MenuItem

    @FXML
    private lateinit var stopButton: Button

    @FXML
    private lateinit var startButton: Button

    @FXML
    private lateinit var downloadTable: TableView<Download>

    @FXML
    private lateinit var nameColumn: TableColumn<Download, String>

    @FXML
    private lateinit var sizeColumn: TableColumn<Download, String>

    @FXML
    private lateinit var progressColumn: TableColumn<Download, String>

    @FXML
    private lateinit var dateColumn: TableColumn<Download, String>

    fun setMainStage(mainStage: Stage) {
        model.mainStage = mainStage
        mainStage.widthProperty()
            .addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                downloadTable.refresh()
            }
    }

    override fun initialize(location: URL, resources: ResourceBundle?) {
        model.setTextOutput(console)
        model.urlTextField = urlSubmission
        urlSubmission.promptTextProperty().value = Model.EXAMPLE_SERIES
        urlSubmission.onKeyPressed = EventHandler { e: KeyEvent ->
            if (e.code == KeyCode.ENTER) {
                start()
            }
        }
        stopButton.isDisable = true
        model.setStartButton(startButton)
        model.setStopButton(stopButton)
        val lastUrl = model.settings().getString(Defaults.LASTDOWNLOAD)
        if (!StringChecker.isNullOrEmpty(lastUrl)) {
            urlSubmission.text = lastUrl
        }
        downloadTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        nameColumn.maxWidth = (1f * Int.MAX_VALUE * 60).toDouble()
        dateColumn.maxWidth = (1f * Int.MAX_VALUE * 20).toDouble()
        sizeColumn.maxWidth = (1f * Int.MAX_VALUE * 10).toDouble()
        progressColumn.maxWidth = (1f * Int.MAX_VALUE * 7).toDouble()
        nameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.episode.name)
        }
        sizeColumn.setCellValueFactory {
            SimpleStringProperty(bytesToString(it.value.fileSize))
        }
        /*sizeColumn.setCellValueFactory { row: TableColumn.CellDataFeatures<Download, String> ->
            SimpleStringProperty(
                bytesToString(
                    row.value.fileSize
                )
            )
        }*/
        progressColumn.setCellValueFactory {
            it.value.progress
        }
        dateColumn.setCellValueFactory {
            SimpleStringProperty(it.value.dateAdded)
        }
        dateColumn.sortType = TableColumn.SortType.DESCENDING
        downloadTable.sortOrder.add(dateColumn)
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
        downloadTable.setRowFactory {
            val row = TableRow<Download>()
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (!isEmpty!!) {
                        val download = row.item
                        if (download != null) {
                            val menu = ContextMenu()
                            val openFolder = MenuItem("Open Folder")
                            openFolder.onAction =
                                EventHandler { model.openFolder(download.downloadPath, true) }
                            val deleteFile = MenuItem("Delete File")
                            deleteFile.onAction = EventHandler {
                                model.showConfirm("Do you wish to delete this file?") {
                                    if (download.isDownloading) {
                                        model.toast("You can't delete videos that are being downloaded.")
                                        return@showConfirm
                                    }
                                    val file = File(download.downloadPath)
                                    if (file.exists()) {
                                        if (file.delete()) {
                                            model.showMessage("Success", "Successfully deleted this episode.")
                                        } else {
                                            model.showError("Unable to delete this file. No error thrown. Most likely folder permission issues.")
                                        }
                                    } else {
                                        model.toast("The original file no longer exists.")
                                    }
                                }
                            }
                            val removeFromList = MenuItem("Remove From List")
                            removeFromList.onAction = EventHandler {
                                if (download.isDownloading) {
                                    model.toast("You can't remove downloads that are being downloaded.")
                                    return@EventHandler
                                }
                                model.removeDownload(download)
                            }
                            val resumeDownload = MenuItem("Resume Download")
                            resumeDownload.onAction = EventHandler {
                                if (download.isComplete) {
                                    model.toast("This download is completed. You can't resume it.")
                                    return@EventHandler
                                }
                                if (model.isRunning) {
                                    if (model.linkUrls.contains(fixOldLink(download.episode.link))) {
                                        model.toast("This download is already in queue.")
                                        return@EventHandler
                                    }
                                    model.getLinks().add(download.episode)
                                    model.toast("Successfully added ${download.episode.name} to current queue.")
                                } else {
                                    urlSubmission.text = fixOldLink(download.episode.link)
                                    start()
                                    model.toast("Launched video downloader for: ${download.episode.name}")
                                }
                            }
                            val openDownloadUrl = MenuItem("Open Download URL")
                            openDownloadUrl.onAction = EventHandler {
                                model.showLinkPrompt(
                                    fixOldLink(download.episode.link), true
                                )
                            }
                            val copyDownloadUrl = MenuItem("Copy Download URL")
                            copyDownloadUrl.onAction = EventHandler {
                                model.showCopyPrompt(
                                    fixOldLink(download.episode.link), false
                                )
                            }
                            val seriesDetails = MenuItem("Series Details")
                            seriesDetails.onAction = EventHandler {
                                model.openSeriesDetails(
                                    fixOldLink(download.episode.seriesLink)
                                )
                            }
                            val copySeriesLink = MenuItem("Copy Series Lihk")
                            copySeriesLink.onAction = EventHandler {
                                model.showCopyPrompt(
                                    fixOldLink(download.episode.seriesLink),
                                    false
                                )
                            }
                            val downloadSeries = MenuItem("Download Series")
                            downloadSeries.onAction = EventHandler {
                                if (model.isRunning) {
                                    model.showError("You can't download a series while the downloader is running.")
                                    return@EventHandler
                                }
                                model.urlTextField.text = download.episode.seriesLink
                                model.start()
                                model.toast("Successfully launched video downloader for: ${download.episode.seriesLink}")
                            }
                            val pauseDownload = MenuItem("Pause Download")
                            pauseDownload.onAction = EventHandler {
                                model.toast("Not implemented.")
                            }
                            val play = MenuItem("Play Video")
                            play.onAction = EventHandler {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        model.taskScope.launch {
                                            Desktop.getDesktop().open(download.downloadFile)
                                        }
                                    } else {
                                        model.toast("Desktop is not supported.")
                                    }
                                } catch (e: Exception) {
                                    model.toast("Failed to play video. Check the console.")
                                    if (e.localizedMessage.contains("No application is associated with the specified file for this operation")) {
                                        println("There is no default application for opening this type of file. (mp4)")
                                    } else {
                                        println(e.localizedMessage)
                                    }
                                }
                            }
                            menu.items.addAll(
                                openFolder,
                                openDownloadUrl,
                                copyDownloadUrl
                            )
                            if (!download.episode.seriesLink.isNullOrEmpty()) {
                                menu.items.add(seriesDetails)
                                menu.items.add(copySeriesLink)
                                menu.items.add(downloadSeries)
                            }
                            if (download.isDownloading && !download.isComplete) {
                                menu.items.add(0, pauseDownload)
                            }
                            if (!download.isDownloading && !download.isComplete) {
                                menu.items.add(0, resumeDownload)
                            }
                            if (!download.isDownloading && download.isComplete) {
                                menu.items.add(0, play)
                            }
                            if (!download.isDownloading) {
                                menu.items.add(removeFromList)
                            }
                            if (download.isComplete && !download.isDownloading) {
                                menu.items.add(deleteFile)
                            }
                            row.contextMenu = menu
                            row.onMouseClicked = EventHandler { event: MouseEvent ->
                                if (model.settings().getBoolean(Defaults.SHOWCONTEXTONCLICK)) {
                                    menu.show(model.mainStage, event.screenX, event.screenY)
                                }
                            }
                        }
                    }
                }
            row
        }
        model.setTableView(downloadTable)
        model.taskScope.launch {
            model.updateManager.checkForUpdates(prompt = false, refresh = true)
        }
    }

    private var historyStage: Stage? = null

    private fun openHistory() {
        if (historyStage == null) {
            historyStage = Stage()
            historyStage!!.initModality(Modality.APPLICATION_MODAL)
            historyStage!!.initOwner(model.mainStage)
            historyStage!!.title = "Series Download History"
            val icon = Main::class.java.getResourceAsStream(Model.SETTINGS_ICON)
            if (icon != null) {
                historyStage!!.icons.add(Image(icon))
            }
            historyStage!!.isResizable = true
            historyStage!!.initStyle(StageStyle.DECORATED)
        }
        val loader = FXMLLoader(Main::class.java.getResource(Model.FX_PATH + "history.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (con.parameterCount == 2 && con.parameterTypes[0] == Model::class.java) {
                        return@Callback con.newInstance(model, historyStage)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                System.err.println("Failed to load HistoryController. Error: ${e.localizedMessage}")
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
            historyStage!!.toFront()
            historyStage!!.scene = scene
            historyStage!!.sizeToScene()
            historyStage!!.showAndWait()
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to load history window. Error: ${e.localizedMessage}")
        }
    }

    @FXML
    fun clearURL() {
        urlSubmission.clear()
    }

    @FXML
    fun pasteURL() {
        val clipboard = Clipboard.getSystemClipboard()
        urlSubmission.text = clipboard.string
    }

    @FXML
    fun pasteURLAndStart() {
        val clipboard = Clipboard.getSystemClipboard()
        urlSubmission.text = clipboard.string
        if (!model.isRunning) {
            model.start()
        } else {
            model.showError("Downloader is already running. Unable to start.")
        }
    }

    @FXML
    fun clearConsole() {
        console.clear()
    }

    @FXML
    fun start() {
        model.start()
    }

    @FXML
    fun stop() {
        model.stop()
    }

    @FXML
    @Throws(IOException::class)
    fun handleClicks(event: ActionEvent) {
        val src = event.source
        if (src == openSettings) {
            model.openSettings()
        } else if (src == openDownloadHistory) {
            openHistory()
        } else if (src == openDownloadFolder) {
            if (Desktop.isDesktopSupported()) {
                if (!StringChecker.isNullOrEmpty(model.settings().getString(Defaults.SAVEFOLDER))) {
                    val f = File(model.settings().getString(Defaults.SAVEFOLDER))
                    Desktop.getDesktop().open(f)
                } else {
                    model.showError(
                        "Your download folder doesn't exist.",
                        "Be sure to set it inside the settings before downloading videos."
                    )
                    model.openSettings(0)
                }
            } else {
                model.showError("Desktop is not supported on your OS.")
            }
        } else if (src == about) {
            model.showMessage(
                "About", """This is a FREE open source program to download videos from ${Model.WEBSITE}. That's all. :) 
Author Discord: I don't have discord anymore. Contact me on Github.
Github: https://github.com/NobilityDeviant/
To use this program you must first install Google Chrome, Chromium, Opera, Edge, Firefox or Safari. You can choose any browser in the settings. Everything else should be handled automatically.
You find an episode or series link at ${Model.WEBSITE} and paste it into the field. The default settings should be enough for most people.
If you have any issues, please create an issue on Github."""
            )
        } else if (src == updates) {
            if (model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                model.settings().setBoolean(Defaults.DENIEDUPDATE, false)
                model.saveSettings()
            }
            model.taskScope.launch {
                model.updateManager.checkForUpdates(prompt = true, refresh = true)
            }
        } else if (src == openWebsite) {
            model.showLinkPrompt(Model.WEBSITE, true)
        } else if (src == openGithub) {
            model.showLinkPrompt(Model.GITHUB, true)
        }
    }
}