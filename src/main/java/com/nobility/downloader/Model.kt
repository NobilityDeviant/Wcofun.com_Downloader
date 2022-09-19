package com.nobility.downloader

import com.nobility.downloader.downloads.Download
import com.nobility.downloader.downloads.Downloads
import com.nobility.downloader.history.History
import com.nobility.downloader.cache.Series
import com.nobility.downloader.scraper.BuddyHandler
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.cache.WebsiteData
import com.nobility.downloader.scraper.CategoryUpdater
import com.nobility.downloader.series.SeriesDetails
import com.nobility.downloader.series.SeriesDetailsController
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.settings.JsonManager
import com.nobility.downloader.settings.Settings
import com.nobility.downloader.settings.SettingsController
import com.nobility.downloader.updates.ConfirmController
import com.nobility.downloader.updates.UpdateManager
import com.nobility.downloader.utils.TextOutput
import com.nobility.downloader.utils.Toast
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.utils.Tools.fixOldLink
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import javafx.util.Callback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import org.openqa.selenium.WebDriver
import java.awt.Desktop
import java.io.*
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

class Model {

    lateinit var mainStage: Stage
    private var _settings: Settings? = null
    private val settings get() = _settings!!
    private var downloadHistory: History? = null
    private val historySave get() = downloadHistory!!
    private var _downloads: Downloads? = null
    private val downloadSave get() = _downloads!!
    private var _websiteData: WebsiteData? = null
    private val data get() = _websiteData!!
    val taskScope = CoroutineScope(Dispatchers.Default)
    val backgroundScope = CoroutineScope(Dispatchers.Default)

    private lateinit var userAgents: List<String>
    var isRunning = false
        private set
    //it is needed here for other download checks across the app.
    val episodes: MutableList<Episode> = Collections.synchronizedList(ArrayList())
    var isClientUpdating = false

    @Volatile
    var downloadsFinishedForSession = 0
        private set
    lateinit var tableView: TableView<Download>
        private set
    val runningDrivers: MutableList<WebDriver?> = Collections.synchronizedList(ArrayList())

    @JvmField
    var details: MutableList<SeriesDetails> = Collections.synchronizedList(ArrayList())

    @FXML
    lateinit var urlTextField: TextField

    @FXML
    private lateinit var stopButton: Button

    @FXML
    private lateinit var startButton: Button

    private fun canStart(checkUrl: Boolean): Boolean {
        if (isRunning) {
            return false
        }
        val downloadFolder = File(settings.getString(Defaults.SAVEFOLDER))
        if (!downloadFolder.exists()) {
            showError("The downloader folder in your settings doesn't exist.")
            openSettings(0)
            return false
        }
        if (!downloadFolder.canWrite()) {
            showError(
                """
    The download folder in your settings doesn't allow write permissions.
    If this is a USB or SD Card then disable write protection.
    Try selecting a folder in the user or home folder. Those are usually not restricted.
    """.trimIndent()
            )
            openSettings(0)
            return false
        }
        if (Tools.bytesToMB(downloadFolder.usableSpace) < 150) {
            showError(
                """
    The download folder in your settings requires at least 150MB of free space.
    Most videos average around 100MB.
    """.trimIndent()
            )
            openSettings(0)
            return false
        }
        if (checkUrl) {
            val url = urlTextField.text
            if (url.isNullOrEmpty()) {
                showError(
                    """You must input a series or show link first. 

Examples: 

Series: $EXAMPLE_SERIES

Episode: $EXAMPLE_SHOW
 """
                )
                return false
            }
        }
        if (settings.getInteger(Defaults.DOWNLOADTHREADS) < 1) {
            showError("Your download threads must be higher than 0.")
            return false
        }
        if (settings.getInteger(Defaults.DOWNLOADTHREADS) > 10) {
            showError("Your download threads must be lower than 10.")
            return false
        }
        if (settings.getInteger(Defaults.MAXEPISODES) > 9999) {
            showError("Your episodes can't be higher than 9999.")
            return false
        }
        if (settings.getInteger(Defaults.MAXEPISODES) < 0) {
            showError("Your episodes can't be lower than 0.")
            return false
        }
        return true
    }

    fun downloadNewEpisodesForSeries(series: Series) {
        if (!canStart(false)) {
            return
        }
        Platform.runLater {
            startButton.isDisable = true
            stopButton.isDisable = false
        }
        isRunning = true
        episodes.clear()
        downloadsFinishedForSession = 0
        taskScope.launch {
            val buddyHandler = BuddyHandler(this@Model)
            try {
                val newEpisodes = buddyHandler.checkForNewEpisodes(series)
                if (newEpisodes.data != null) {
                    println("Found ${newEpisodes.data.size} new episode(s). Starting the downloader.")
                    series.episodes.addAll(newEpisodes.data)
                    val added = historySave.addSeries(series, true)
                    if (added) {
                        saveSeriesHistory()
                    }
                    episodes.addAll(newEpisodes.data)
                    buddyHandler.launch()
                } else {
                    throw Exception(newEpisodes.message)
                }
            } catch (e: Exception) {
                buddyHandler.kill()
                stop()
                //e.printStackTrace()
                println("Failed to download new episodes." +
                        "\nError: " + e.localizedMessage)
                return@launch
            }
        }
    }

    fun start() {
        if (!canStart(true)) {
            return
        }
        Platform.runLater {
            startButton.isDisable = true
            stopButton.isDisable = false
        }
        val url = urlTextField.text
        isRunning = true
        settings.setString(Defaults.LASTDOWNLOAD, url)
        saveSettings()
        episodes.clear()
        downloadsFinishedForSession = 0
        saveSettings()
        taskScope.launch {
            val buddyHandler = BuddyHandler(this@Model)
            try {
                buddyHandler.update(url)
            } catch (e1: Exception) {
                buddyHandler.kill()
                stop()
                e1.printStackTrace()
                Platform.runLater { showError(
                    "Failed to read episodes from $url" +
                            "\nError: " + e1.localizedMessage
                ) }
                return@launch
            }
            settings.setString(Defaults.LASTDOWNLOAD, "")
            saveSettings()
            buddyHandler.launch()
        }
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        Platform.runLater {
            startButton.isDisable = false
            stopButton.isDisable = true
        }
        isRunning = false
    }

    private fun downloadUserAgents() {
        val resources = File(".${File.separator}resources${File.separator}ua.txt")
        if (!resources.exists()) {
            if (!resources.mkdir()) {
                println("Failed to find or create resources folder. Unable to download user agents.")
                return
            }
        }
        taskScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            try {
                BufferedInputStream(
                    URL(
                        "https://www.dropbox.com/s/42q46p69n4b84o7/ua.txt?dl=1"
                    ).openStream()
                ).use { `in` ->
                    FileOutputStream(resources).use { fileOutputStream ->
                        BufferedOutputStream(fileOutputStream, buffer.size).use { bos ->
                            var bytesRead: Int
                            while (`in`.read(buffer, 0, 2048).also { bytesRead = it } != -1) {
                                bos.write(buffer, 0, bytesRead)
                            }
                            println("Successfully downloaded user agents.")
                            try {
                                userAgents = Files.readAllLines(resources.toPath())
                                println("Successfully loaded " + userAgents.size + " user agents.")
                            } catch (e: Exception) {
                                println("Failed to read download user agents file. Defaulting to using one.")
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                println("Failed to download user agents.")
                println("Download them manually and place them in the resources folder.")
                println("Download Link: https://www.dropbox.com/s/42q46p69n4b84o7/ua.txt?dl=1")
            }
        }
    }

    private val confirmStage = Stage()

    @FXML
    fun showUpdateConfirm(title: String?, required: Boolean, upToDate: Boolean) {
        if (updateManager.latestUpdate == null) {
            println("Failed to show update window. Latest update is null.")
            return
        }
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "confirm.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (con.parameterCount == 1 && con.parameterTypes[0] == Model::class.java) {
                        return@Callback con.newInstance(this)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                System.err.println("Failed to open update window. Error: ${e.localizedMessage}")
                e.printStackTrace()
                exitProcess(-1)
            }
        }
        try {
            val root = loader.load<Parent>()
            val confirmController = loader.getController<ConfirmController>()
            val scene = Scene(root)
            confirmController.setStage(confirmStage, required, upToDate)
            val icon = Main::class.java.getResourceAsStream(MAIN_ICON)
            if (icon != null) {
                confirmStage.icons.add(Image(icon))
            }
            confirmStage.title = title
            confirmStage.isResizable = false
            confirmStage.scene = scene
            confirmStage.sizeToScene()
            confirmStage.showAndWait()
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to show update confirm window. Error: ${e.localizedMessage}")
        }
    }

    private var detailsStage: Stage = Stage()

    fun openSeriesDetails(link: String) {
        if (link.isEmpty()) {
            toast("This episode doesn't have a series link.")
            return
        }
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "seriesdetails.fxml"))
        /*loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (con.parameterCount == 3 && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                        && con.parameterTypes[2] == String::class.java) {
                        return@Callback con.newInstance(this, detailsStage, link)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                println("Failed to load VideoDetailsController. Error: ${e.localizedMessage}")
                e.printStackTrace(System.err)
                return@Callback null
            }
        }*/
        try {
            val root = loader.load<Parent>()
            val scene = Scene(root)
            detailsStage.title = "Series Details"
            val icon = Main::class.java.getResourceAsStream(SETTINGS_ICON)
            if (icon != null) {
                detailsStage.icons.add(Image(icon))
            }
            detailsStage.isResizable = true
            //detailsStage.initStyle(StageStyle.DECORATED)
            detailsStage.toFront()
            detailsStage.scene = scene
            detailsStage.sizeToScene()
            detailsStage.show()
            loader.getController<SeriesDetailsController>()?.setup(this, detailsStage, link)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to show video details window. Error: ${e.localizedMessage}")
        }
    }

    private var settingsStage: Stage = Stage()

    @JvmOverloads
    fun openSettings(command: Int = -1) {
        settingsStage.title = "Settings"
        val icon = Main::class.java.getResourceAsStream(SETTINGS_ICON)
        if (icon != null) {
            settingsStage.icons.add(Image(icon))
        }
        settingsStage.isResizable = true
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "settings.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (con.parameterCount == 2 && con.parameterTypes[0] == Model::class.java && con.parameterTypes[1] == Stage::class.java) {
                        return@Callback con.newInstance(this, settingsStage)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                println("Failed to load SettingsController. Error: ${e.localizedMessage}")
                e.printStackTrace(System.err)
                return@Callback null
            }
        }
        val layout: Parent
        try {
            layout = loader.load()
            val controller = loader.getController<SettingsController>()
            val scene = Scene(layout)
            settingsStage.toFront()
            settingsStage.scene = scene
            settingsStage.sizeToScene()
            settingsStage.show()
            controller.executeStartCommand(command)
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to open settings window. Error: ${e.localizedMessage}")
        }
    }

    fun setTableView(tableView: TableView<Download>) {
        this.tableView = tableView
        tableView.items.addAll(FXCollections.observableArrayList(downloadSave.downloads))
        tableView.sort()
    }

    @Synchronized
    fun addDownload(download: Download) {
        if (!tableView.items.contains(download)) {
            tableView.items.add(download)
            downloadSave.downloads.add(download)
            tableView.sort()
            saveDownloads()
        }
    }

    fun removeDownload(download: Download) {
        tableView.items.remove(download)
        downloadSave.downloads.remove(download)
        tableView.sort()
        saveDownloads()
    }

    fun updateDownloadProgress(download: Download) {
        tableView.items[indexForDownload(download, true)].updateProgress()
        downloadSave.downloads[indexForDownload(download, false)].updateProgress()
        saveDownloads()
    }

    fun updateDownload(download: Download) {
        tableView.items[indexForDownload(download, true)].update(download)
        downloadSave.downloads[indexForDownload(download, false)].update(download)
        saveDownloads()
    }

    private fun indexForDownload(download: Download, table: Boolean): Int {
        return if (table) {
            tableView.items.indexOf(download)
        } else {
            downloadSave.downloads.indexOf(download)
        }
    }

    fun getDownloadForUrl(url: String): Download? {
        for (download in downloadSave.downloads) {
            if (fixOldLink(download.episode.link) == url) {
                return download
            }
        }
        return null
    }

    val randomUserAgent: String
        get() = if (userAgents.isNotEmpty()) {
            userAgents[ThreadLocalRandom.current().nextInt(userAgents.size)]
        } else {
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1"
        }

    fun saveSettings() {
        JsonManager.saveSettings(settings)
    }

    fun saveSeriesHistory() {
        JsonManager.saveHistory(historySave)
    }

    fun saveDownloads() {
        JsonManager.saveDownloads(downloadSave)
    }

    fun saveData() {
        JsonManager.saveWebsiteData(data)
    }

    val linkUrls: List<String>
        get() {
            val urls: MutableList<String> = ArrayList()
            for (episode in episodes) {
                urls.add(episode.link)
            }
            return urls
        }

    @get:Synchronized
    val nextLink: Episode?
        get() {
            if (episodes.isEmpty()) {
                return null
            }
            val link = episodes.first()
            episodes.removeAt(0)
            return link
        }

    fun openFolder(file: File?, parent: Boolean) {
        if (file == null) {
            showError("This file is corrupted.")
            return
        }
        openFolder(file.absolutePath, parent)
    }

    fun openFolder(path: String, parent: Boolean) {
        var file = File(path)
        if (!file.exists()) {
            toast("This file doesn't exist.")
            return
        }
        if (parent) {
            file = if (file.parentFile.exists()) {
                file.parentFile
            } else {
                toast("This file's parent doesn't exist.")
                return
            }
        }
        if (!Desktop.isDesktopSupported()) {
            showError("Desktop is not supported on this device.")
            return
        }
        try {
            Desktop.getDesktop().open(file)
        } catch (e: IOException) {
            showError("Unable to open folder.", e)
        }
    }

    private var shutdownExecuted = false

    fun shutdown(force: Boolean) {
        if (force && !shutdownExecuted) {
            stop()
            if (runningDrivers.isNotEmpty()) {
                for (driver in runningDrivers) {
                    if (driver != null) {
                        driver.close()
                        driver.quit()
                    }
                }
            }
            saveSettings()
            saveSeriesHistory()
            saveDownloads()
            exitProcess(-1)
        }
        if (isRunning) {
            showConfirm(
                "You are currently downloading videos right now. Shutting down will stop and possibly corrupt " +
                        "any incomplete video. Do you wish to continue?"
            ) {
                stop()
                if (runningDrivers.isNotEmpty()) {
                    for (driver in runningDrivers) {
                        if (driver != null) {
                            try {
                                driver.close()
                                driver.quit()
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
                saveSettings()
                saveSeriesHistory()
                saveDownloads()
                shutdownExecuted = true
                exitProcess(0)
            }
        } else {
            if (runningDrivers.isNotEmpty()) {
                for (driver in runningDrivers) {
                    if (driver != null) {
                        try {
                            driver.close()
                            driver.quit()
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
            saveSettings()
            saveSeriesHistory()
            saveDownloads()
            shutdownExecuted = true
            exitProcess(0)
        }
    }

    fun showMessage(title: String?, content: String?) {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.dialogPane.stylesheets.add(Main::class.java.getResource(DIALOG_PATH)?.toString() ?: "")
        alert.dialogPane.styleClass.add("dialog")
        if (SystemUtils.IS_OS_WINDOWS) { //icons dont work on manjaro
            val stage = alert.dialogPane.scene.window as Stage
            val icon = Main::class.java.getResourceAsStream(MAIN_ICON)
            if (icon != null) {
                stage.icons.add(Image(icon))
            }
        }
        alert.headerText = ""
        alert.contentText = content
        alert.showAndWait()
    }

    fun showError(title: String?, content: String?) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.dialogPane.stylesheets.add(Main::class.java.getResource(DIALOG_PATH)?.toString() ?: "")
        alert.dialogPane.styleClass.add("dialog")
        if (SystemUtils.IS_OS_WINDOWS) {
            val stage = alert.dialogPane.scene.window as Stage
            val icon = Main::class.java.getResourceAsStream(MAIN_ICON)
            if (icon != null) {
                stage.icons.add(Image(icon))
            }
        }
        alert.headerText = ""
        alert.contentText = content
        alert.showAndWait()
    }

    fun showError(content: String?) {
        showError("Error", content)
    }

    fun showError(content: String, e: Exception) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = "Error"
        alert.dialogPane.stylesheets.add(Main::class.java.getResource(DIALOG_PATH)?.toString() ?: "")
        alert.dialogPane.styleClass.add("dialog")
        if (SystemUtils.IS_OS_WINDOWS) {
            val stage = alert.dialogPane.scene.window as Stage
            val icon = Main::class.java.getResourceAsStream(MAIN_ICON)
            if (icon != null) {
                stage.icons.add(Image(icon))
            }
        }
        alert.headerText = ""
        alert.contentText = """
               $content
               Error: ${e.localizedMessage}
               """.trimIndent()
        alert.showAndWait()
    }

    private fun openLink(link: String) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(URI(link))
            } catch (e: Exception) {
                showError("Unable to open $link", e)
            }
        } else {
            showError("Desktop is not supported on this device.")
        }
    }

    fun showConfirm(message: String?, runnable: Runnable?) {
        val alert = Alert(
            Alert.AlertType.CONFIRMATION, message,
            ButtonType.CANCEL, ButtonType.YES
        )
        alert.dialogPane.stylesheets.add(Main::class.java.getResource(DIALOG_PATH)?.toString() ?: "")
        alert.dialogPane.styleClass.add("dialog")
        alert.showAndWait().ifPresent { buttonType: ButtonType ->
            if (buttonType == ButtonType.YES) {
                runnable?.run()
            }
        }
    }

    fun showConfirm(message: String?, runnable: Runnable?, denyRunnable: Runnable?) {
        val alert = Alert(
            Alert.AlertType.CONFIRMATION, message,
            ButtonType.CANCEL, ButtonType.YES
        )
        alert.dialogPane.stylesheets.add(Main::class.java.getResource(DIALOG_PATH)?.toString() ?: "")
        alert.dialogPane.styleClass.add("dialog")
        alert.showAndWait().ifPresent { buttonType: ButtonType ->
            if (buttonType == ButtonType.YES) {
                runnable?.run()
            } else {
                denyRunnable?.run()
            }
        }
    }

    fun showCopyPrompt(text: String, prompt: Boolean) {
        showCopyPrompt(text, prompt, mainStage)
    }

    fun showCopyPrompt(text: String, prompt: Boolean, stage: Stage) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        if (prompt) {
            showConfirm("Do you want to copy this to your clipboard?") {
                content.putString(text)
                clipboard.setContent(content)
                toast("Copied", stage)
            }
        } else {
            content.putString(text)
            clipboard.setContent(content)
            toast("Copied", stage)
        }
    }

    fun showLinkPrompt(link: String, prompt: Boolean) {
        if (prompt) {
            showConfirm(
                """
    Do you want to open
    [$link]
    in your default browser?
    """.trimIndent()
            ) { openLink(link) }
        } else {
            openLink(link)
        }
    }

    fun showLinkPrompt(link: String, message: String, prompt: Boolean) {
        if (prompt) {
            showConfirm(message) { openLink(link) }
        } else {
            openLink(link)
        }
    }

    fun openFile(file: File?, toastOnError: Boolean) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            } else {
                throw Exception("Desktop is not supported.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println("Failed to open file. Error: " + e.localizedMessage)
            if (toastOnError) {
                Platform.runLater { Toast.makeToast(mainStage, "Failed to open file.", e) }
            }
        }
    }

    @Synchronized
    fun incrementDownloadsFinished() {
        downloadsFinishedForSession++
    }

    val updateManager = UpdateManager(this)
    var categoryUpdater: CategoryUpdater? = null

    init {
        _websiteData = JsonManager.loadWebsiteData()
        if (_websiteData == null) {
            _websiteData = WebsiteData()
            saveData()
        }
        _downloads = JsonManager.loadDownloads()
        if (_downloads == null) {
            _downloads = Downloads()
            saveDownloads()
        }
        for (i in downloadSave.downloads.indices) {
            downloadSave.downloads[i].updateProgress()
        }
        saveDownloads()
        downloadHistory = JsonManager.loadHistory()
        if (downloadHistory == null) {
            downloadHistory = History()
            saveSeriesHistory()
        }
        _settings = JsonManager.loadSettings()
        if (_settings == null) {
            _settings = Settings()
            settings.loadDefaultSettings()
        }
        settings.checkForNewSettings()
        saveSettings()
        try {
            userAgents = Files.readAllLines(
                File(
                    "." + File.separator
                            + "resources" + File.separator + "ua.txt"
                ).toPath()
            )
            println("Successfully loaded " + userAgents.size + " user agents.")
        } catch (e: Exception) {
            println("Unable to load /resources/ua.txt (UserAgents) attempting to download them.")
            downloadUserAgents()
        }
        if (settings.getBoolean(Defaults.SILENTDRIVER) && !DEBUG_MODE) {
            System.setProperty("webdriver.chrome.silentOutput", "true")
            Logger.getLogger("org.openqa.selenium").level = Level.OFF
        }
    }

    fun settings(): Settings {
        return settings
    }

    fun history(): History {
        return historySave
    }

    fun downloads(): Downloads {
        return downloadSave
    }

    fun data(): WebsiteData {
        return data
    }

    fun setTextOutput(textArea: TextArea?) {
        val output = TextOutput(textArea)
        System.setOut(PrintStream(output))
    }

    fun getLinks(): MutableList<Episode> {
        return episodes
    }

    fun setStartButton(startButton: Button) {
        this.startButton = startButton
    }

    fun setStopButton(stopButton: Button) {
        this.stopButton = stopButton
    }

    fun toast(text: String) {
        Toast.makeToast(mainStage, text)
    }

    fun toast(text: String, stage: Stage) {
        Toast.makeToast(stage, text)
    }

    companion object {
        const val OLD_WEBSITE = "https://www.wcofun.com/"
        const val WEBSITE = "https://www.wcofun.net/"
        const val GITHUB = "https://github.com/NobilityDeviant/Wcofun.com_Downloader"
        const val EXAMPLE_SERIES = WEBSITE + "anime/ive-been-killing-slimes-for-300-years-and-maxed-out-my-level"
        const val EXAMPLE_SHOW =
            WEBSITE + "ive-been-killing-slimes-for-300-years-and-maxed-out-my-level-episode-1-english-dubbed"
        const val DEBUG_MODE = false
        const val FX_PATH = "/fx/"
        private const val IMAGE_PATH = "/images/"
        const val CSS_PATH = "/css/"
        const val DIALOG_PATH = CSS_PATH + "dialog.css"
        const val SETTINGS_ICON = IMAGE_PATH + "icon.png"
        const val MAIN_ICON = IMAGE_PATH + "icon.png"
    }
}