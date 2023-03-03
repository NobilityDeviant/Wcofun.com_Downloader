package com.nobility.downloader

import com.nobility.downloader.entities.Download
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
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
import javafx.scene.input.*
import javafx.stage.Stage
import javafx.util.Callback
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


class MainController(private val model: Model, private val mainStage: Stage) : Initializable {

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
    private lateinit var openWcoSeries: MenuItem

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
        val lastUrl = model.settings().stringSetting(Defaults.LASTDOWNLOAD)
        if (lastUrl.isNotEmpty()) {
            urlSubmission.text = lastUrl
        }
        downloadTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        nameColumn.maxWidth = (1f * Int.MAX_VALUE * 60).toDouble()
        dateColumn.maxWidth = (1f * Int.MAX_VALUE * 20).toDouble()
        sizeColumn.maxWidth = (1f * Int.MAX_VALUE * 10).toDouble()
        progressColumn.maxWidth = (1f * Int.MAX_VALUE * 7).toDouble()
        nameColumn.setCellValueFactory {
            SimpleStringProperty(it.value.name)
        }
        sizeColumn.setCellValueFactory {
            it.value.fileSizeProperty
        }
        progressColumn.setCellValueFactory {
            it.value.progress
        }
        dateColumn.setCellValueFactory {
            it.value.dateProperty
        }
        dateColumn.sortType = TableColumn.SortType.DESCENDING
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
            row.isCache = false
            row.emptyProperty()
                .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, isEmpty: Boolean? ->
                    if (isEmpty == false) {
                        if (row.item != null) {
                            val menu = ContextMenu()
                            val openFolder = MenuItem("Open Folder")
                            openFolder.onAction =
                                EventHandler { model.openFolder(row.item.downloadPath, true) }
                            val deleteFile = MenuItem("Delete File")
                            deleteFile.onAction = EventHandler {
                                model.showConfirm("Do you wish to delete this file?") {
                                    if (row.item.downloading || row.item.queued) {
                                        model.toast("You can't delete videos that are being downloaded.")
                                        return@showConfirm
                                    }
                                    val file = row.item.downloadFile()
                                    if (file != null && file.exists()) {
                                        if (file.delete()) {
                                            model.removeDownload(row.item)
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
                                if (row.item.downloading || row.item.queued) {
                                    model.toast("You can't remove downloads that are being downloaded.")
                                    return@EventHandler
                                }
                                model.removeDownload(row.item)
                            }
                            val resumeDownload = MenuItem("Resume Download")
                            resumeDownload.onAction = EventHandler {
                                if (row.item.isComplete) {
                                    model.toast("This download is completed. You can't resume it.")
                                    return@EventHandler
                                }
                                if (model.isRunning) {
                                    if (model.addEpisodeToQueue(row.item)) {
                                        model.toast("Successfully added episode to current queue.")
                                    } else {
                                        model.toast("This episode is already in queue.")
                                    }
                                } else {
                                    urlSubmission.text = fixOldLink(row.item.link)
                                    start()
                                    model.toast("Launched video downloader for: ${row.item.name}")
                                }
                            }
                            val openDownloadUrl = MenuItem("Open Download URL")
                            openDownloadUrl.onAction = EventHandler {
                                model.showLinkPrompt(
                                    fixOldLink(row.item.link), true
                                )
                            }
                            val copyDownloadUrl = MenuItem("Copy Download URL")
                            copyDownloadUrl.onAction = EventHandler {
                                model.showCopyPrompt(
                                    fixOldLink(row.item.link), false
                                )
                            }
                            val seriesDetails = MenuItem("Series Details")
                            seriesDetails.onAction = EventHandler {
                                model.openSeriesDetails(
                                    fixOldLink(row.item.seriesLink)
                                )
                            }
                            val copySeriesLink = MenuItem("Copy Series Lihk")
                            copySeriesLink.onAction = EventHandler {
                                model.showCopyPrompt(
                                    fixOldLink(row.item.seriesLink),
                                    false
                                )
                            }
                            val downloadSeries = MenuItem("Download Series")
                            downloadSeries.onAction = EventHandler {
                                if (model.isRunning) {
                                    model.showError("You can't download a series while the downloader is running.")
                                    return@EventHandler
                                }
                                model.urlTextField.text = row.item.seriesLink
                                model.start()
                                //model.toast(
                                  //  "Successfully launched video downloader for: ${row.item.seriesLink}"
                                //)
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
                                            Desktop.getDesktop().open(row.item.downloadFile())
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
                            if (!row.item.seriesLink.isNullOrEmpty()) {
                                menu.items.add(seriesDetails)
                                menu.items.add(copySeriesLink)
                                menu.items.add(downloadSeries)
                            }
                            if ((row.item.downloading || row.item.queued) && !row.item.isComplete) {
                                menu.items.add(0, pauseDownload)
                            }
                            if (!row.item.downloading &&!row.item.queued && !row.item.isComplete) {
                                menu.items.add(0, resumeDownload)
                            }
                            if (!row.item.downloading && !row.item.queued && row.item.isComplete) {
                                menu.items.add(0, play)
                            }
                            if (!row.item.downloading && !row.item.queued) {
                                menu.items.add(removeFromList)
                            }
                            if (row.item.isComplete && !row.item.downloading && !row.item.queued) {
                                menu.items.add(deleteFile)
                            }
                            row.contextMenu = menu
                            row.onMouseClicked = EventHandler { event: MouseEvent ->
                                if (model.settings().booleanSetting(Defaults.SHOWCONTEXTONCLICK)) {
                                    menu.show(model.mainStage, event.screenX, event.screenY)
                                }
                            }
                        }
                    }
                }
            row
        }
        model.setTableView(downloadTable, dateColumn)
        model.taskScope.launch {
            model.updateManager.checkForUpdates(prompt = false, refresh = true)
        }
        model.mainStage = mainStage
        mainStage.widthProperty()
            .addListener { _: ObservableValue<out Number?>?, _: Number?, _: Number? ->
                downloadTable.refresh()
            }
        val downloadFolder = File(model.settings().stringSetting(Defaults.SAVEFOLDER))
        if (!downloadFolder.exists()) {
            println("Your download folder no longer exists." +
                    "\n${downloadFolder.absolutePath}")
            println("Your download folder has been reset to the default.")
            model.settings().setSetting(Defaults.SAVEFOLDER, Defaults.SAVEFOLDER.value)
        }
        if (!downloadFolder.canWrite()) {
            println(
                """
    Your download folder doesn't allow write permissions.
    If this is a USB or SD Card then disable write protection.
    Try selecting a folder in the user or home folder. Those are usually not restricted.
    ${downloadFolder.absolutePath}
    """.trimIndent()
            )
            println("Your download folder has been reset to the default.")
            model.settings().setSetting(Defaults.SAVEFOLDER, Defaults.SAVEFOLDER.value)
        }
    }

    private var historyStage: Stage = Stage()

    private fun openHistory() {
        historyStage.title = "Series Download History"
        val icon = Main::class.java.getResourceAsStream(Model.SETTINGS_ICON)
        if (icon != null) {
            historyStage.icons.add(Image(icon))
        }
        historyStage.isResizable = true
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
            historyStage.toFront()
            historyStage.scene = scene
            historyStage.sizeToScene()
            historyStage.show()
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
            //todo make a way to do this in the background without effecting the current dl
            //if found in cache just launch
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

    fun setupHotKeys() {
        model.mainStage.scene.addEventFilter(KeyEvent.KEY_PRESSED, object : EventHandler<KeyEvent> {
            val settings: KeyCombination = KeyCodeCombination(
                KeyCode.S,
                KeyCombination.CONTROL_DOWN
            )
            val history: KeyCombination = KeyCodeCombination(
                KeyCode.H,
                KeyCombination.CONTROL_DOWN
            )
            val downloadFolder: KeyCombination = KeyCodeCombination(
                KeyCode.F,
                KeyCombination.CONTROL_DOWN
            )
            val wco: KeyCombination = KeyCodeCombination(
                KeyCode.W,
                KeyCombination.CONTROL_DOWN
            )


            override fun handle(ke: KeyEvent) {
                if (settings.match(ke)) {
                    model.openSettings()
                    ke.consume()
                } else if (wco.match(ke)) {
                    model.openWco()
                    ke.consume()
                } else if (history.match(ke)) {
                    openHistory()
                    ke.consume()
                } else if (downloadFolder.match(ke)) {
                    openDownloadFolder()
                    ke.consume()
                }
            }
        })
        //println("Hotkeys have been set up.")
    }

    private fun openDownloadFolder() {
        if (Desktop.isDesktopSupported()) {
            if (model.settings().stringSetting(Defaults.SAVEFOLDER).isNotEmpty()) {
                val f = File(model.settings().stringSetting(Defaults.SAVEFOLDER))
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
    }

    @FXML
    @Throws(IOException::class)
    fun handleClicks(event: ActionEvent) {
        when (event.source) {
            openSettings -> {
                model.openSettings()
            }
            openDownloadHistory -> {
                openHistory()
            }
            openWcoSeries -> {
                model.openWco()
            }
            openDownloadFolder -> {
                openDownloadFolder()
            }
            about -> {
                model.showMessage(
                    "About",
                    "This is a FREE open source program to download videos from ${Model.WEBSITE}. That's all. :)" +
                            "\nAuthor Discord: I don't have discord anymore. Contact me on Github." +
                            "\nGithub: https://github.com/NobilityDeviant/" +
                            "\nTo use this program you must first install a browser like: " +
                            "\nGoogle Chrome, Chromium, Opera, Edge, Firefox or Safari." +
                            "\nYou can choose any listed browser in the settings. Everything else should be handled automatically." +
                            "\nYou find an episode or series link at ${Model.WEBSITE} and paste it into the field. The default settings should be enough for most people." +
                            "\nIf you have any issues, please create an issue on Github."
                )
            }
            updates -> {
                if (model.settings().booleanSetting(Defaults.DENIEDUPDATE)) {
                    model.settings().setSetting(Defaults.DENIEDUPDATE, false)
                }
                model.taskScope.launch {
                    model.updateManager.checkForUpdates(prompt = true, refresh = true)
                }
            }
            openWebsite -> {
                model.showLinkPrompt(Model.WEBSITE, true)
            }
            openGithub -> {
                model.showLinkPrompt(Model.GITHUB, true)
            }
        }
    }
}