package com.nobility.downloader

import com.nobility.downloader.entities.BoxStoreHandler
import com.nobility.downloader.entities.Download
import com.nobility.downloader.entities.Episode
import com.nobility.downloader.entities.Series
import com.nobility.downloader.scraper.BuddyHandler
import com.nobility.downloader.series.SeriesDetailsController
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.settings.SettingsController
import com.nobility.downloader.updates.UpdateManager
import com.nobility.downloader.utils.Option
import com.nobility.downloader.utils.TextOutput
import com.nobility.downloader.utils.Toast
import com.nobility.downloader.utils.Tools
import com.nobility.downloader.wco.WcoController
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
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
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import javax.net.ssl.HttpsURLConnection
import kotlin.system.exitProcess


class Model {

    lateinit var mainStage: Stage
    val taskScope = CoroutineScope(Dispatchers.Default)
    private val uiScope = CoroutineScope(Dispatchers.JavaFx)
    private val userAgents = ArrayList<String>()
    var isRunning = false
        private set

    //it is needed here for other download checks across the app.
    val episodes: MutableList<Episode> = Collections.synchronizedList(ArrayList())
    private lateinit var downloadList: ObservableList<Download>
    var isClientUpdating = false
    var developerMode = false
    var isUpdatingWco = false
    val updateManager = UpdateManager(this)

    @Volatile
    var downloadsFinishedForSession = 0
        private set

    @Volatile
    var downloadsInProgressForSession = 0
        private set
    lateinit var tableView: TableView<Download>
        private set

    val runningDrivers: MutableList<WebDriver?> = Collections.synchronizedList(ArrayList())

    @FXML
    lateinit var urlTextField: TextField

    @FXML
    private lateinit var stopButton: Button

    @FXML
    private lateinit var startButton: Button

    private val store = BoxStoreHandler(this)

    private fun canStart(checkUrl: Boolean): Boolean {
        if (isRunning) {
            return false
        }
        if (isUpdatingWco) {
            showError("You can't download videos while the wco db is being updated.")
            return false
        }
        val downloadFolder = File(store.stringSetting(Defaults.SAVEFOLDER))
        if (!downloadFolder.exists()) {
            showError("The downloader folder in your settings doesn't exist. You must set it up before downloading videos.")
            openSettings(0)
            return false
        }
        try {
            if (!downloadFolder.canWrite()) {
                showError(
                    "The download folder in your settings doesn't allow write permissions." +
                            "\nIf this is a USB or SD Card then disable write protection." +
                            "\nTry selecting a folder in the user or home folder. Those are usually not restricted."
                )
                openSettings(0)
                return false
            }
        } catch (e: Exception) {
            showError("Failed to check for write permissions.", e)
            return false
        }
        if (!store.booleanSetting(Defaults.BYPASSFREESPACECHECK)) {
            val root = downloadFolder.toPath().root
            val usableSpace = root.toFile().usableSpace
            //println("Found ${Tools.bytesToMB(usableSpace)}MBs in root.")
            if (usableSpace != 0L) {
                if (Tools.bytesToMB(usableSpace) < 150) {
                    showError(
                        "The download folder in your settings requires at least 150MB of free space." +
                                "\nMost videos average around 100MB." +
                                "\nIf you are having issues with this, open the settings (CTRL + S) and enable Bypass Disk Space Check"
                    )
                    openSettings(0)
                    return false
                }
            } else {
                val freeSpace = root.toFile().freeSpace
                if (freeSpace != 0L) {
                    if (Tools.bytesToMB(freeSpace) < 150) {
                        showError(
                            "The download folder in your settings requires at least 150MB of free space." +
                                    "\nMost videos average around 100MB." +
                                    "\nIf you are having issues with this, open the settings (CTRL + S) and enable Bypass Disk Space Check"
                        )
                        openSettings(0)
                        return false
                    }
                } else {
                    println("[WARNING] Failed to check for free space. Make sure you have enough space to download videos. (150MB+)")
                }
            }
        }
        if (checkUrl) {
            val url = urlTextField.text
            if (url.isNullOrEmpty()) {
                showError(
                    "You must input a series or show link first." +
                            "\n\nExamples:" +
                            "\nSeries: $exampleSeries\n" +
                            "\nEpisode: $exampleEpisode"
                )
                return false
            }
            try {
                URL(url).toURI()
            } catch (_: Exception) {
                showError("This is not a valid URL.")
                return false
            }
        }
        if (store.integerSetting(Defaults.DOWNLOADTHREADS) < 1) {
            showError("Your download threads must be higher than 0.")
            return false
        }
        if (store.integerSetting(Defaults.DOWNLOADTHREADS) > 10) {
            showError("Your download threads must be lower than 10.")
            return false
        }
        return true
    }

    fun downloadNewEpisodesForSeries(
        seriesList: List<Series>
    ) {
        taskScope.launch {
            println("Checking ${seriesList.size} series for new episodes.")
            var updated = 0
            val buddyHandler = BuddyHandler(this@Model)
            seriesList.forEach {
                val result = buddyHandler.checkForNewEpisodes(it)
                if (result.data != null) {
                    it.updateEpisodes(result.data.updatedEpisodes)
                    println("Added ${result.data.newEpisodes.size} new episodes to series ${it.name}")
                    updated++
                }
            }
            if (updated > 0) {
                println("Successfully added new updates for $updated series history.")
            }
        }
    }

    fun start() {
        if (!canStart(true)) {
            return
        }
        uiScope.launch {
            startButton.isDisable = true
            stopButton.isDisable = false
        }
        val url = urlTextField.text
        isRunning = true
        store.setSetting(Defaults.LASTDOWNLOAD, url)
        episodes.clear()
        downloadsFinishedForSession = 0
        downloadsInProgressForSession = 0
        taskScope.launch {
            val buddyHandler = BuddyHandler(this@Model)
            try {
                buddyHandler.update(url)
            } catch (e: Exception) {
                buddyHandler.kill()
                stop()
                e.printStackTrace()
                withContext(Dispatchers.JavaFx) {
                    if (e.localizedMessage.contains("unknown error: cannot find")
                        || e.localizedMessage.contains("Unable to find driver executable")
                    ) {
                        showError(
                            "Failed to read episodes from $url" +
                                    "\nError: Failed to find your browsers binary." +
                                    "\nThis usually means that the browser you are using is not installed or there are problems reading it." +
                                    "\nTry a different browser or try again. If this problem persists, there might be permission issues with your folders."
                        )
                    } else {
                        showError(
                            "Failed to read episodes from $url" +
                                    "\nError: " + e.localizedMessage
                        )
                    }
                }
                return@launch
            }
            store.setSetting(Defaults.LASTDOWNLOAD, "")
            buddyHandler.launch()
        }
    }

    fun softStart() {
        uiScope.launch {
            startButton.isDisable = true
            stopButton.isDisable = false
        }
        isRunning = true
        downloadsFinishedForSession = 0
        downloadsInProgressForSession = 0
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        uiScope.launch {
            startButton.isDisable = false
            stopButton.isDisable = true
        }
        isRunning = false
    }

    private suspend fun loadUserAgents() = withContext(Dispatchers.IO) {
        try {
            userAgents.addAll(
                Files.readAllLines(
                    File("./resources/$USER_AGENTS_FILE_NAME").toPath()
                )
            )
            //println("Successfully loaded " + userAgents.size + " user agents.")
        } catch (e: Exception) {
            println("Failed to load /resources/$USER_AGENTS_FILE_NAME attempting to download them.")
            downloadUserAgents()
        }
    }

    private suspend fun downloadUserAgents() = withContext(Dispatchers.IO) {
        val resources = File("./resources/")
        if (!resources.exists()) {
            if (!resources.mkdir()) {
                println("Failed to find or create resources folder. Unable to download user agents.")
                return@withContext
            }
        }
        val userAgentsFile = File("${resources.absolutePath}/$USER_AGENTS_FILE_NAME")
        val buffer = ByteArray(2048)
        try {
            BufferedInputStream(
                URL(USER_AGENTS_LINK).openStream()
            ).use { input ->
                FileOutputStream(userAgentsFile).use { fos ->
                    BufferedOutputStream(fos, buffer.size).use { bos ->
                        var bytesRead: Int
                        while (input.read(buffer, 0, 2048).also { bytesRead = it } != -1) {
                            bos.write(buffer, 0, bytesRead)
                        }
                        println("Successfully downloaded user agents.")
                    }
                }
                try {
                    userAgents.addAll(Files.readAllLines(userAgentsFile.toPath()))
                    println("Successfully loaded " + userAgents.size + " user agents.")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Failed to read downloaded user agents file. Defaulting to using one.")
                }
            }
        } catch (e: IOException) {
            println("Failed to download user agents.")
            println("Download them manually and place them in the resources folder.")
            println("Download Link: $USER_AGENTS_LINK")
        }
    }

    private val confirmStage = Stage()

    @FXML
    fun showUpdateConfirm(title: String, required: Boolean, upToDate: Boolean) {
        if (updateManager.latestUpdate == null) {
            println("Failed to show update window. Latest update is null.")
            return
        }
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "update.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (
                        con.parameterCount == 4
                        && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                        && con.parameterTypes[2] == Boolean::class.java
                        && con.parameterTypes[3] == Boolean::class.java
                    ) {
                        return@Callback con.newInstance(
                            this,
                            confirmStage,
                            required,
                            upToDate
                        )
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
            val scene = Scene(root)
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
            println("Failed to show update confirm window. Error: ${e.localizedMessage}")
        }
    }

    private var detailsStage: Stage = Stage()

    fun openSeriesDetails(seriesSlug: String) {
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "series_details.fxml"))
        try {
            val root = loader.load<Parent>()
            val scene = Scene(root)
            scene.stylesheets.add(
                Main::class.java.getResource(
                    CSS_PATH + "contextmenu.css"
                )?.toString() ?: ""
            )
            detailsStage.title = "Series Details"
            val icon = Main::class.java.getResourceAsStream(SETTINGS_ICON)
            if (icon != null) {
                detailsStage.icons.add(Image(icon))
            }
            detailsStage.isResizable = true
            detailsStage.toFront()
            detailsStage.scene = scene
            detailsStage.sizeToScene()
            detailsStage.show()
            loader.getController<SeriesDetailsController>()?.setup(this, detailsStage, seriesSlug)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to show video details window. Error: ${e.localizedMessage}")
        }
    }

    fun openSeriesDetails(episode: Episode? = null, series: Series? = null) {
        val slug: String = if (series != null) {
            series.slug
        } else if (episode != null) {
            episode.seriesSlug
        } else {
            ""
        }
        if (slug.isEmpty()) {
            toast("This episode doesn't have a series link.")
            return
        }
        openSeriesDetails(slug)
    }

    private val settingsStage: Stage = Stage()

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
                    if (
                        con.parameterCount == 2
                        && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                    ) {
                        return@Callback con.newInstance(this, settingsStage)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                println("Failed to load SettingsController. Error: ${e.localizedMessage}")
                e.printStackTrace()
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

    private val downloadConfirmStage: Stage = Stage()

    fun openDownloadConfirm(series: Series, episode: Episode?) {
        var mSeries = series
        if (mSeries.episodes.isEmpty()) {
            val wcoSeries = store.wcoHandler.seriesForSlug(series.slug)
            if (wcoSeries != null && wcoSeries.episodes.isNotEmpty()) {
                mSeries = wcoSeries
            }
        }
        downloadConfirmStage.title = "Download Series"
        val icon = Main::class.java.getResourceAsStream(MAIN_ICON)
        if (icon != null) {
            downloadConfirmStage.icons.add(Image(icon))
        }
        downloadConfirmStage.isResizable = true
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "download_confirm.fxml"))
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (
                        con.parameterCount == 4
                        && con.parameterTypes[0] == Model::class.java
                        && con.parameterTypes[1] == Stage::class.java
                        && con.parameterTypes[2] == Series::class.java
                        && con.parameterTypes[3] == Episode::class.java
                    ) {
                        return@Callback con.newInstance(
                            this,
                            downloadConfirmStage,
                            mSeries,
                            episode
                        )
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                println("Failed to load DownloadConfirmController. Error: ${e.localizedMessage}")
                e.printStackTrace()
                return@Callback null
            }
        }
        val layout: Parent
        try {
            layout = loader.load()
            val scene = Scene(layout)
            downloadConfirmStage.toFront()
            downloadConfirmStage.scene = scene
            downloadConfirmStage.sizeToScene()
            downloadConfirmStage.show()
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to open download confirm window. Error: ${e.localizedMessage}")
        }
    }

    private val wcoStage: Stage = Stage()

    fun openWco() {
        wcoStage.title = "All Series"
        val icon = Main::class.java.getResourceAsStream(SETTINGS_ICON)
        if (icon != null) {
            wcoStage.icons.add(Image(icon))
        }
        wcoStage.isResizable = true
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "wco.fxml"))
        val layout: Parent
        try {
            layout = loader.load()
            val scene = Scene(layout)
            scene.stylesheets.add(
                Main::class.java.getResource(
                    CSS_PATH + "contextmenu.css"
                )?.toString() ?: ""
            )
            wcoStage.toFront()
            wcoStage.scene = scene
            wcoStage.sizeToScene()
            wcoStage.show()
            loader.getController<WcoController>()?.setup(this, wcoStage)
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to open wco window. Error: ${e.localizedMessage}")
        }
    }

    private val choiceStage: Stage = Stage()

    fun showChoice(title: String, content: String, vararg options: Option) {
        choiceStage.title = title
        val icon = Main::class.java.getResourceAsStream(SETTINGS_ICON)
        if (icon != null) {
            choiceStage.icons.add(Image(icon))
        }
        choiceStage.isResizable = true
        val loader = FXMLLoader(Main::class.java.getResource(FX_PATH + "choice.fxml"))
        val listOption = ArrayList<Option>()
        for (o in options) {
            listOption.add(o)
        }
        loader.controllerFactory = Callback { controllerType: Class<*> ->
            try {
                for (con in controllerType.constructors) {
                    if (
                        con.parameterCount == 4
                        && con.parameterTypes[0] == Stage::class.java
                        && con.parameterTypes[1] == String::class.java
                        && con.parameterTypes[2] == String::class.java
                        && con.parameterTypes[3] == List::class.java
                    ) {
                        return@Callback con.newInstance(choiceStage, title, content, listOption)
                    }
                }
                return@Callback controllerType.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                println("Failed to load ChoiceController. Error: ${e.localizedMessage}")
                e.printStackTrace()
                return@Callback null
            }
        }
        val layout: Parent
        try {
            layout = loader.load()
            val scene = Scene(layout)
            choiceStage.toFront()
            choiceStage.scene = scene
            choiceStage.sizeToScene()
            choiceStage.showAndWait()
        } catch (e: IOException) {
            println("Failed to open choice window. Error: ${e.localizedMessage}")
            e.printStackTrace()
        }
    }

    fun setTableView(tableView: TableView<Download>, dateColumn: TableColumn<Download, String>) {
        this.tableView = tableView
        downloadList = FXCollections.observableArrayList(store.downloadBox.all)
        tableView.items = downloadList
        //done this way because setting new items removes the sort order
        tableView.sortOrder.add(dateColumn)
        //tableView.items.addAll(store.downloadBox.all)
        for (download in tableView.items) {
            download.updateProgress()
            download.updateFileSizeProperty()
            download.updateDateProperty()
        }
        tableView.sort()
    }

    @Synchronized
    fun addDownload(download: Download) {
        val index = indexForDownload(download)
        if (index == -1) {
            download.updateProgress()
            downloadList.add(download)
            store.downloadBox.put(download)
            tableView.sort()
        } else {
            //push it to the top of the list
            download.dateAdded = System.currentTimeMillis()
            updateDownloadInDatabase(download, true)
            tableView.sort()
        }
    }

    fun removeDownload(download: Download) {
        //tableView.items.remove(download)
        downloadList.remove(download)
        store.downloadBox.remove(download)
        //tableView.sort()
    }

    fun updateDownloadProgress(download: Download) {
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].updateProgress()
        }
    }

    fun updateDownloadFileSize(download: Download) {
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].updateFileSizeProperty()
        }
    }

    fun updateDownloadInDatabase(download: Download, updateProperties: Boolean) {
        store.downloadBox.put(download)
        //used to keep the downloads inside the table in sync
        val index = indexForDownload(download)
        if (index != -1) {
            downloadList[index].update(download, updateProperties)
        }
    }

    private fun indexForDownload(download: Download): Int {
        for ((index, d) in downloadList.withIndex()) {
            if (d.matches(download)) {
                return index
            }
        }
        return -1
    }

    val randomUserAgent: String
        get() = if (userAgents.isNotEmpty()) {
            userAgents[ThreadLocalRandom.current().nextInt(userAgents.size)]
        } else {
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1"
        }

    private fun isEpisodeInQueue(episode: Episode): Boolean {
        for (e in episodes) {
            if (e.matches(episode)) {
                return true
            }
        }
        return false
    }

    fun addEpisodeToQueue(episode: Episode): Boolean {
        if (!isEpisodeInQueue(episode)) {
            episodes.add(episode)
            return true
        }
        return false
    }

    fun addEpisodesToQueue(episodesToAdd: List<Episode>): Int {
        var added = 0
        for (episode in episodesToAdd) {
            if (!isEpisodeInQueue(episode)) {
                episodes.add(episode)
                added++
            }
        }
        return added
    }

    //fun addSeriesToQueue(series: Series): Int {
      //  return addEpisodesToQueue(series.episodes)
    //}

    @get:Synchronized
    val nextEpisode: Episode?
        get() {
            if (episodes.isEmpty()) {
                return null
            }
            val link = episodes.first()
            episodes.removeAt(0)
            return link
        }

    @Suppress("UNUSED")
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
            exitProcess(-1)
        }
        if (isRunning) {
            showConfirm(
                "You are currently downloading videos right now. Shutting down will stop and possibly corrupt " +
                        "any incomplete video. Do you wish to continue?"
            ) {
                stop()
                val runningDrivers = ArrayList(runningDrivers)
                if (runningDrivers.isNotEmpty()) {
                    for (driver in runningDrivers) {
                        if (driver != null) {
                            try {
                                driver.quit()
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
                shutdownExecuted = true
                exitProcess(0)
            }
        } else {
            val runningDrivers = ArrayList(runningDrivers)
            if (runningDrivers.isNotEmpty()) {
                for (driver in runningDrivers) {
                    if (driver != null) {
                        try {
                            driver.quit()
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
            shutdownExecuted = true
            exitProcess(0)
        }
    }

    fun showMessage(title: String = "", content: String) {
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
        val mContent = content?: "Message Unavailable"
        val copy = ButtonType("Copy Error", ButtonBar.ButtonData.OK_DONE)
        val close = ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE)
        val alert = Alert(
            Alert.AlertType.ERROR,
            mContent,
            copy,
            close
        )
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
        val result = alert.showAndWait()
        if (result.get() == close) {
            alert.close()
        } else if (result.get() == copy) {
            showCopyPrompt(
                "Would you like to copy this error?" +
                        "\nNote: Please paste the error before closing the app. " +
                        "It will be removed from your clipboard when closed.",
                prettyError(mContent),
                true,
                mainStage
            )
        }
    }

    fun showError(content: String?) {
        showError("Error", content)
    }

    fun showError(content: String, e: Exception) {
        val mContent = """
               $content
               Error: ${e.localizedMessage}
               """.trimIndent()
        val copy = ButtonType("Copy Error", ButtonBar.ButtonData.OK_DONE)
        val close = ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE)
        val alert = Alert(
            Alert.AlertType.ERROR,
            mContent,
            copy,
            close
        )
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
        val result = alert.showAndWait()
        if (result.get() == close) {
            alert.close()
        } else if (result.get() == copy) {
            showCopyPrompt(
                "Would you like to copy this error?" +
                        "\nNote: Please paste the error before closing the app. " +
                        "It will be removed from your clipboard when closed.",
                prettyError(mContent),
                true,
                mainStage
            )
        }
    }

    private fun prettyError(error: String): String {
        val builder = StringBuilder()
        val split = error.split(" ")
        var end = 0
        for (s in split) {
            builder.append(s).append(" ")
            end++
            if (end >= 15) {
                builder.append("\n")
                end = 0
            }
        }
        return builder.toString()
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
        if (prompt) {
            showConfirm("Do you want to copy this to your clipboard?") {
                copyToClipboard(text)
                toast("Copied", stage)
            }
        } else {
            copyToClipboard(text)
            toast("Copied", stage)
        }
    }

    private fun showCopyPrompt(message: String, text: String, prompt: Boolean, stage: Stage) {
        if (prompt) {
            showConfirm(message) {
                copyToClipboard(text)
                toast("Copied", stage)
            }
        } else {
            copyToClipboard(text)
            toast("Copied", stage)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }

    fun showLinkPrompt(link: String, prompt: Boolean) {
        System.err.println("PROMPT: $link")
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
                Platform.runLater { toast("Failed to open file.", e, mainStage) }
            }
        }
    }

    @Synchronized
    fun incrementDownloadsFinished() {
        downloadsFinishedForSession++
    }

    @Synchronized
    fun incrementDownloadsInProgress() {
        downloadsInProgressForSession++
    }

    @Synchronized
    fun decrementDownloadsInProgress() {
        downloadsInProgressForSession--
    }

    fun debugErr(s: String, e: Exception? = null) {
        if (!settings().booleanSetting(Defaults.DEBUGMESSAGES)) {
            return
        }
        System.err.println("[${Tools.date}][${Tools.currentTime}][DEBUG ERROR]\n$s")
        if (e != null) {
            System.err.println("Stacktrace: ")
            e.printStackTrace()
        }
    }

    fun debugNote(s: String) {
        if (!settings().booleanSetting(Defaults.DEBUGMESSAGES)) {
            return
        }
        System.err.println("[${Tools.date}][${Tools.currentTime}][DEBUG NOTE]\n$s")
    }

    fun debugWriteErrorToFile(s: String, fileName: String) {
        if (!settings().booleanSetting(Defaults.DEBUGMESSAGES)) {
            return
        }
        val debugPath = File("./debug/")
        if (!debugPath.exists()) {
            if (!debugPath.mkdir()) {
                debugErr("Failed to write error to file. Unable to find/create the debug folder.")
                return
            }
        }
        val debugFile = File(debugPath.absolutePath + "/$fileName.txt")
        var bufferedWriter: BufferedWriter? = null
        try {
            bufferedWriter = BufferedWriter(FileWriter(debugFile, true))
            bufferedWriter.newLine()
            bufferedWriter.newLine()
            bufferedWriter.write("[${Tools.date}][${Tools.currentTime}][DEBUG ERROR]")
            bufferedWriter.newLine()
            bufferedWriter.write(s)
            bufferedWriter.flush()
            debugNote("Successfully wrote error to file: ${debugFile.absolutePath}")
        } catch (e: Exception) {
            debugErr("Failed to write error to file.", e)
        } finally {
            bufferedWriter?.close()
        }
    }

    fun settings(): BoxStoreHandler {
        return store
    }

    fun setTextOutput(textArea: TextArea) {
        val output = TextOutput(textArea)
        System.setOut(PrintStream(output))
    }

    fun setStartButton(startButton: Button) {
        this.startButton = startButton
    }

    fun setStopButton(stopButton: Button) {
        this.stopButton = stopButton
    }

    fun toast(text: String) {
        Toast.makeToast(mainStage, text, store.doubleSetting(Defaults.TOASTTRANSPARENCY))
    }

    fun toast(text: String, stage: Stage, transparency: Double) {
        Toast.makeToast(stage, text, transparency)
    }

    fun toast(text: String, stage: Stage) {
        Toast.makeToast(stage, text, store.doubleSetting(Defaults.TOASTTRANSPARENCY))
    }

    fun toast(text: String, e: Exception, stage: Stage) {
        Toast.makeToast(stage, text, e, store.doubleSetting(Defaults.TOASTTRANSPARENCY))
    }

    /**
     * This is used to update the wcofun url in the settings.
     * First we iterate through all the urls found inside the repository
     * and if the url changed, then we update the domain name and extension.
     * I have decided to use an external (and easily updatable) text file just in case
     * the website changes drastically.
     */
    suspend fun updateWcoUrl() = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        try {
            BufferedReader(
                InputStreamReader(URL(WEBSITE_URLS_LINK).openStream())
            ).use { input ->
                while (true) {
                    val line = input.readLine()
                    if (!line.isNullOrEmpty()) {
                        urls.add(line)
                    } else {
                        break
                    }
                }
            }
        } catch (e: IOException) {
            urls.addAll(
                listOf(
                    "https://wcofun.com",
                    "https://wcofun.org"
                )
            )
        }
        for (u in urls) {
            var con: HttpsURLConnection? = null
            try {
                con = URL(u).openConnection() as HttpsURLConnection
                con.addRequestProperty(
                    "Accept",
                    "text/html"
                )
                con.instanceFollowRedirects = true
                con.connectTimeout = 30_000
                con.readTimeout = 30_000
                con.addRequestProperty("User-Agent", randomUserAgent)
                con.connect()
                val url = con.url.toString()
                val domainWithoutExtension = Tools.extractDomainFromLink(url)
                val extension = Tools.extractExtensionFromLink(url)
                var updated = false
                if (store.stringSetting(Defaults.DOMAIN) != domainWithoutExtension) {
                    store.setSetting(Defaults.DOMAIN, domainWithoutExtension)
                    updated = true
                }
                if (store.stringSetting(Defaults.EXTENSION) != extension) {
                    store.setSetting(Defaults.EXTENSION, extension)
                    updated = true
                }
                if (updated) {
                    urlTextField.promptTextProperty().value = exampleSeries
                    println("Successfully updated main url to: ${con.url}")
                }
                break
            } catch (e: Exception) {
                debugErr("Failed to check for updated website url with: $u")
            } finally {
                con?.disconnect()
            }
        }
    }

    val wcoUrl: String get() {
        return "https://" +
                settings().stringSetting(Defaults.DOMAIN) +
                "." +
                settings().stringSetting(Defaults.EXTENSION) +
                "/"
    }

    fun linkForSlug(slug: String): String {
        return wcoUrl + slug
    }

    private val exampleEpisode: String get() = "$wcoUrl$EXAMPLE_EPISODE"

    val exampleSeries: String get() = "$wcoUrl$EXAMPLE_SERIES"

    fun episodeForSlug(
        series: Series,
        slug: String
    ): Episode? {
        series.episodes.forEach {
            if (it.slug == slug) {
                return it
            }
        }
        return null
    }

    fun hasEpisode(series: Series, episode: Episode?): Boolean {
        if (episode == null) {
            return false
        }
        series.episodes.forEach {
            if (it.slug == episode.slug) {
                return true
            }
        }
        return false
    }

    init {
        taskScope.launch(Dispatchers.IO) {
            store.loadSettings()
            updateWcoUrl()
            loadUserAgents()
            if (store.booleanSetting(Defaults.SILENTDRIVER) && !DEBUG_MODE) {
                System.setProperty("webdriver.chrome.silentOutput", "true")
                Logger.getLogger("org.openqa.selenium").level = Level.OFF
            }
            if (!store.booleanSetting(Defaults.NEW_DB_WARNING)) {
                println("[WARNING] Version 2.0 and higher is incompatible with all old series, episode and download data." +
                        "\nAll old data will be updated to be compatible, but some might not work." +
                        "\nIf you are having any issues, please download the new package and new series data from Github and use it seperately from your current one.")
                store.setSetting(Defaults.NEW_DB_WARNING, true)
            }
        }
    }

    companion object {
        const val GITHUB_URL = "https://github.com/NobilityDeviant/Wcofun.com_Downloader"
        const val EXAMPLE_SERIES = "anime/negima"
        const val EXAMPLE_EPISODE =
            "strange-planet-episode-1-the-flying-machine"
        const val DEBUG_MODE = false
        const val FX_PATH = "/fx/"
        private const val IMAGE_PATH = "/images/"
        const val CSS_PATH = "/css/"
        const val DIALOG_PATH = CSS_PATH + "dialog.css"
        const val SETTINGS_ICON = IMAGE_PATH + "icon.png"
        const val MAIN_ICON = IMAGE_PATH + "icon.png"
        const val NO_IMAGE_ICON = IMAGE_PATH + "no-image.png"
        const val USER_AGENTS_LINK = "https://raw.githubusercontent.com/NobilityDeviant/Wcofun.com_Downloader/master/useragents.txt"
        const val USER_AGENTS_FILE_NAME = "useragents.txt"
        const val WEBSITE_URLS_LINK = "https://raw.githubusercontent.com/NobilityDeviant/Wcofun.com_Downloader/master/wcourls.txt"
    }
}