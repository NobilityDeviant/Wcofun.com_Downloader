package com.nobility.downloader.settings

import com.nobility.downloader.Model
import com.nobility.downloader.series.SeriesScraper
import com.nobility.downloader.utils.Tools.bytesToMB
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.File
import java.net.URL
import java.util.*

class SettingsController(private val model: Model, private val stage: Stage) : Initializable {

    @FXML
    private lateinit var buttonSaveSettings: Button

    @FXML
    private lateinit var buttonChooseDownloadFolder: Button

    @FXML
    private lateinit var buttonClearDownloads: Button

    @FXML
    private lateinit var buttonStopChrome: Button

    @FXML
    private lateinit var buttonClearHistory: Button

    @FXML
    private lateinit var viewScrapedDataButton: Button

    @FXML
    private lateinit var checkHistoryForEpisodesButton: Button

    @FXML
    private lateinit var devModeButton: Button

    @FXML
    private lateinit var fieldDownloadThreads: TextField

    @FXML
    private lateinit var fieldProxy: TextField

    @FXML
    private lateinit var fieldTimeout: TextField

    @FXML
    private lateinit var fieldDownloadFolder: TextField

    @FXML
    private lateinit var cbShowContext: CheckBox

    @FXML
    private lateinit var cbEnableProxy: CheckBox

    @FXML
    private lateinit var choiceBrowser: ChoiceBox<String>

    @FXML
    private lateinit var toastSlider: Slider

    @FXML
    private lateinit var toastButton: Button

    @FXML
    private lateinit var updateWcoButtonn: Button

    fun executeStartCommand(command: Int) {
        if (command > -1) {
            when (command) {
                0 -> setDownloadFolder()
                1 -> {}
            }
        }
    }

    override fun initialize(location: URL, resources: ResourceBundle?) {
        toastSlider.value = model.settings().doubleSetting(Defaults.TOASTTRANSPARENCY)
        toastSlider.valueProperty().addListener { _: ObservableValue<out Number>?, _: Number, _: Number ->
            //println("${toastSlider.value}")
            buttonSaveSettings.isDisable = !settingsChanged()
        }
        fieldDownloadThreads.text = model.settings().integerSetting(Defaults.DOWNLOADTHREADS).toString()
        fieldProxy.text = model.settings().stringSetting(Defaults.PROXY)
        fieldTimeout.text = model.settings().integerSetting(Defaults.TIMEOUT).toString()
        fieldDownloadFolder.text = model.settings().stringSetting(Defaults.SAVEFOLDER)
        fieldDownloadThreads.textProperty()
            .addListener { _: ObservableValue<out String>?, oldValue: String, newValue: String ->
                handleTextField(
                    fieldDownloadThreads,
                    oldValue,
                    newValue,
                    true
                )
            }
        fieldProxy.textProperty()
            .addListener { _: ObservableValue<out String>?, oldValue: String, newValue: String ->
                handleTextField(
                    fieldProxy,
                    oldValue,
                    newValue,
                    false
                )
            }
        fieldTimeout.textProperty()
            .addListener { _: ObservableValue<out String>?, oldValue: String, newValue: String ->
                handleTextField(
                    fieldTimeout,
                    oldValue,
                    newValue,
                    true
                )
            }
        fieldDownloadFolder.textProperty()
            .addListener { _: ObservableValue<out String>?, oldValue: String, newValue: String ->
                handleTextField(
                    fieldDownloadFolder,
                    oldValue,
                    newValue,
                    false
                )
            }
        buttonSaveSettings.isDisable = true // disable save button by default
        cbEnableProxy.isSelected = model.settings().booleanSetting(Defaults.ENABLEPROXY)
        fieldProxy.isDisable = !model.settings().booleanSetting(Defaults.ENABLEPROXY)
        cbEnableProxy.selectedProperty()
            .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, newValue: Boolean? ->
                buttonSaveSettings.isDisable = !settingsChanged()
                fieldProxy.isDisable = !newValue!!
            }
        cbShowContext.isSelected = model.settings().booleanSetting(Defaults.SHOWCONTEXTONCLICK)
        cbShowContext.selectedProperty()
            .addListener { _: ObservableValue<out Boolean?>?, _: Boolean?, _: Boolean? ->
                buttonSaveSettings.isDisable = !settingsChanged()
            }
        choiceBrowser.items.addAll(DriverDefaults.allDrivers())
        choiceBrowser.value = model.settings().stringSetting(Defaults.DRIVER)
        choiceBrowser.selectionModel.selectedItemProperty()
            .addListener { _: ObservableValue<out String>?, _: String?, _: String? ->
                buttonSaveSettings.isDisable = !settingsChanged()
            }
        stage.onCloseRequest = EventHandler {
            if (settingsChanged()) {
                model.showConfirm("You have unsaved changes. Would you like to save your settings?", {
                    if (saveSettings()) {
                        stage.close()
                    }
                }) { stage.close() }
            } else {
                stage.close()
            }
        }
    }

    @FXML
    fun handleClicks(event: ActionEvent) {
        when (event.source) {
            buttonClearHistory -> {
                if (model.settings().seriesBox.isEmpty) {
                    return
                }
                model.showConfirm("Would you like to clear all your series history?") {
                    val size = model.settings().seriesBox.all.size
                    model.settings().seriesBox.removeAll()
                    model.toast("Cleared $size series history(s) from your settings.", stage)
                }
            }
            buttonClearDownloads -> {
                if (model.settings().downloadBox.isEmpty) {
                    return
                }
                model.showConfirm(
                    """
        Would you like to clear all your downloads? 
        Note: This will not delete any files.
        """.trimIndent()
                ) {
                    val size = model.settings().downloadBox.all.size
                    model.settings().downloadBox.removeAll()
                    model.tableView.items.clear()
                    model.toast("Cleared $size downloads from your settings.", stage)
                }
            }
            buttonChooseDownloadFolder -> {
                setDownloadFolder()
            }
            buttonSaveSettings -> {
                saveSettings()
            }
            buttonStopChrome -> {
                stopChrome()
            }
            viewScrapedDataButton -> {
                model.openWco()
            }
            checkHistoryForEpisodesButton -> {
                if (!model.settings().seriesBox.isEmpty) {
                    model.downloadNewEpisodesForSeries(model.settings().seriesBox.all)
                    model.showMessage(content = "Launched downloader for all series history.")
                } else {
                    model.toast("You don't have any series history.", stage)
                }
            }
            devModeButton -> {
                model.showConfirm("Are you sure you want to turn on developer mode?" +
                        "\nThis is just used for certain hidden options. You will most likely not notice a difference.") {
                    model.developerMode = true
                    model.toast("Developer Mode Activated", stage)
                }
            }
            toastButton -> {
                model.toast("Preview", stage, toastSlider.value)
            }
            updateWcoButtonn -> {
                if (model.isUpdatingWco) {
                    return
                }
                if (model.isRunning) {
                    model.toast(
                        "Unable to update the wco database while the video downloader is running.",
                        stage
                    )
                    return
                }
                if (model.settings().wcoHandler.seriesBox.all.size < 1000) {
                    model.toast(
                        "Please download the wco db from github before starting this process. " +
                                "This is to ensure that only the needed series are scraped.",
                        stage
                    )
                    return
                }
                model.showConfirm("Do you really want to update the wco db? " +
                        "This is a very intensive process which requires a decent pc.") {
                    val seriesScraper = SeriesScraper(model)
                    model.taskScope.launch {
                        seriesScraper.updateWcoDb()
                    }
                    model.toast(
                        "The updater has been launched. " +
                            "This will prevent the downloader from running. " +
                            "Please keep an eye out for the consoles messages.",
                        stage
                    )
                }
            }
        }
    }

    private fun stopChrome() {
        if (model.isRunning) {
            model.showError("You can't use this while the downloader is running.")
            return
        }
        val systemType = System.getProperty("os.name").lowercase(Locale.getDefault())
        model.showConfirm(
            """
    Would you like to kill all chrome.exe and chromedriver.exe processes?
    Sometimes Selenium doesn't properly close all resources. This makes it easier to kill them all. 
    Note: This will close your actual chrome browser as well.
    """.trimIndent()
        ) {
            try {
                if (systemType.contains("win")) {
                    Runtime.getRuntime().exec("taskkill /f /im chromedriver.exe")
                    Runtime.getRuntime().exec("taskkill /f /im chrome.exe")
                } else {
                    Runtime.getRuntime().exec("pkill -f \"(chrome)?(--headless)\"")
                }
                model.toast("Successfully killed all chrome.exe and chromedriver.exe processes.", stage)
            } catch (e: Exception) {
                model.showError("Failed to close one or more chrome processes. Error: " + e.localizedMessage)
            }
        }
    }

    private fun setDownloadFolder() {
        val directoryChooser = DirectoryChooser()
        directoryChooser.title = "Select Download Folder"
        val saveFolder = File(model.settings().stringSetting(Defaults.SAVEFOLDER))
        if (saveFolder.exists()) {
            directoryChooser.initialDirectory = saveFolder
        } else {
            directoryChooser.initialDirectory = File(System.getProperty("user.home"))
        }
        val selectedDirectory = directoryChooser.showDialog(stage)
        if (selectedDirectory != null) {
            fieldDownloadFolder.text = selectedDirectory.absolutePath
            buttonSaveSettings.isDisable = !settingsChanged()
        }
    }

    /**
     * Main button save handle
     */
    private fun saveSettings(): Boolean {
        if (fieldDownloadThreads.text.isEmpty()) {
            fieldDownloadThreads.text = Defaults.DOWNLOADTHREADS.value.toString()
        }
        if (fieldTimeout.text.isEmpty()) {
            fieldTimeout.text = Defaults.TIMEOUT.value.toString()
        }
        if (fieldDownloadFolder.text.isEmpty()) {
            model.toast("The download folder can't be empty.", stage)
            setDownloadFolder()
            return false
        }
        val downloadFolder = File(fieldDownloadFolder.text)
        if (!downloadFolder.exists()) {
            model.toast("The downloader folder doesn't exist.", stage)
            setDownloadFolder()
            return false
        }
        if (!downloadFolder.canWrite()) {
            model.showError(
                """
    The download folder doesn't allow write permissions.
    If this is a USB or SD Card then disable write protection.
    Try selecting a folder in the user or home folder. Those are usually not restricted.
    """.trimIndent()
            )
            return false
        }
        if (bytesToMB(downloadFolder.usableSpace) < 150) {
            model.showError(
                """
    The download folder requires at least 150MB of free space.
    Most videos average around 100MB.
    """.trimIndent()
            )
            return false
        }
        if (!fieldProxy.text.isNullOrEmpty() && cbEnableProxy.isSelected) {
            when (isValidProxy(fieldProxy.text)) {
                2 -> {
                    model.toast("Ports can only be between 1-65535.", stage)
                    return false
                }

                -1 -> {
                    model.showError(
                        "Not a valid proxy", """
     You must input a proxy and port with : as a delimiter. Auth Proxies are not allowed.
     Example: 192.168.0.1:80 🥺
     """.trimIndent()
                    )
                    return false
                }

                1 -> {
                    model.showError(
                        "Unable to connect to proxy",
                        """
                            Proxy is not able to reach the target ${Model.WEBSITE}
                            Read the console to see the error.
                            """.trimIndent()
                    )
                    return false
                }
            }
        }
        val threads = fieldDownloadThreads.text.toInt()
        var proxyTimeout = 0
        if (threads < 1 || threads > 10) {
            model.showError(
                if (threads < 1) "Threads are too low" else "Threads are too high",
                "You are only allowed to use 1 - 10 threads."
            )
            return false
        }
        if (!fieldTimeout.text.isNullOrEmpty()) {
            proxyTimeout = fieldTimeout.text.toInt()
            if (proxyTimeout < 5 || proxyTimeout > 240) {
                model.showError(
                    if (proxyTimeout < 5) "Proxy Timeout is too low" else "Proxy Timeout is too high",
                    "You are only allowed to use 5-240 seconds for proxy timeout."
                )
                return false
            }
        }
        model.settings().setSetting(Defaults.DOWNLOADTHREADS, threads)
        model.settings().setSetting(Defaults.PROXY, fieldProxy.text)
        model.settings().setSetting(Defaults.TIMEOUT, proxyTimeout)
        model.settings().setSetting(Defaults.SAVEFOLDER, fieldDownloadFolder.text)
        model.settings().setSetting(Defaults.SHOWCONTEXTONCLICK, cbShowContext.isSelected)
        model.settings().setSetting(Defaults.DRIVER, choiceBrowser.value)
        model.settings().setSetting(Defaults.ENABLEPROXY, cbEnableProxy.isSelected)
        model.settings().setSetting(Defaults.TOASTTRANSPARENCY, toastSlider.value)
        model.toast("Settings successfully saved.", stage)
        buttonSaveSettings.isDisable = true
        return true
    }

    /**
     * Determines if any setting value has been changed
     * @return Boolean - Settings have been changed
     */
    private fun settingsChanged(): Boolean {
        //println("${model.settings().doubleSetting(Defaults.TOASTTRANSPARENCY)} ; + ${toastSlider.value}")
        return model.settings().integerSetting(Defaults.DOWNLOADTHREADS).toString() != fieldDownloadThreads.text
                || model.settings().booleanSetting(Defaults.SHOWCONTEXTONCLICK) != cbShowContext.isSelected
                || model.settings().stringSetting(Defaults.PROXY) != fieldProxy.text
                || model.settings().stringSetting(Defaults.SAVEFOLDER) != fieldDownloadFolder.text
                || model.settings().booleanSetting(Defaults.ENABLEPROXY) != cbEnableProxy.isSelected
                || model.settings().integerSetting(Defaults.TIMEOUT).toString() != fieldTimeout.text
                || model.settings().stringSetting(Defaults.DRIVER) != choiceBrowser.value
                || model.settings().doubleSetting(Defaults.TOASTTRANSPARENCY) != toastSlider.value
    }

    /**
     * Makes sure the user is only able to enter a number on the textfield
     * @param field - TextField
     * @param oldValue - String the value before a new character is entered
     * @param newValue - String the value after the new character is entered
     */
    private fun handleTextField(field: TextField, oldValue: String, newValue: String, numbers: Boolean) {
        if (numbers) {
            if (!newValue.matches("\\d*".toRegex())) {
                field.text = newValue.replace("\\D".toRegex(), "0")
            }
            try {
                if (field.text.isNotEmpty()) {
                    val value = newValue.toInt()
                    if (field === fieldDownloadThreads) {
                        if (field.text.length == 1) {
                            if (value < 1) {
                                field.text = "1"
                            }
                        } else {
                            if (value > 10) {
                                field.text = "10"
                            }
                        }
                    } else if (field === fieldTimeout) {
                        if (field.text.length == 1) {
                            if (value < 1) {
                                field.text = "1"
                            }
                        } else {
                            if (value > 120) {
                                field.text = "120"
                            }
                        }
                    }
                }
            } catch (e: NumberFormatException) {
                field.text = oldValue
            }
        }
        buttonSaveSettings.isDisable = !settingsChanged()
    }

    private fun isValidProxy(s: String): Int {
        model.toast("Checking proxy...")
        val split: Array<String>
        val ip: String
        val port: Int
        try {
            split = s.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            ip = split[0]
            port = split[1].toInt()
            if (port < 0 || port > 65535) {
                return 2
            }
        } catch (ignored: Exception) {
            return -1
        }
        return try {
            val response = Jsoup.connect(Model.WEBSITE)
                .timeout(model.settings().integerSetting(Defaults.TIMEOUT) * 1000)
                .proxy(ip, port)
                .execute()
            if (response.statusCode() == 200) {
                0
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }
}