package com.nobility.downloader.updates

import com.nobility.downloader.Main
import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Tools
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import java.io.*
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.system.exitProcess

/**
 * Controller for updates.
 * Just messing with kotlin here, I'm sure that this is pretty badly coded, but it works. :)
 * @author Nobility
 */
@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ConfirmController(private val model: Model) : Initializable {


    @FXML
    private lateinit var updateLog: TextArea

    @FXML
    private lateinit var btnUpdate: Button

    @FXML
    private lateinit var btnCancel: Button

    @FXML
    private lateinit var downloadProgressBar: ProgressBar

    @FXML
    private lateinit var downloadProgressLabel: Label

    @FXML
    private lateinit var downloadLink: Label

    private lateinit var stage: Stage
    private val uiScope = CoroutineScope(Dispatchers.JavaFx)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var required = false
    private var upToDate = false
    private var canceled = false

    override fun initialize(url: URL, rb: ResourceBundle?) {
        downloadProgressLabel.managedProperty().bind(downloadProgressLabel.visibleProperty())
        downloadProgressBar.managedProperty().bind(downloadProgressBar.visibleProperty())
    }

    fun setStage(stage: Stage, required: Boolean, upToDate: Boolean) {
        this.stage = stage
        this.required = required
        this.upToDate = upToDate
        stage.setOnCloseRequest {
            run {
                cancel()
            }
        }
        if (!required) {
            btnCancel.text = "Cancel"
        }
        updateLog.text = model.updateManager.latestUpdate?.updateDescription ?: "No description found."
        if (upToDate) {
            btnUpdate.text = "Updated"
            btnUpdate.isDisable = true
        }
    }

    @FXML
    fun update() {
        if (model.isClientUpdating) {
            return
        }
        downloadProgressLabel.isVisible = true
        downloadProgressBar.isVisible = true
        btnUpdate.text = "Updating"
        btnUpdate.isDisable = true
        downloadLink.text = "Downloading: ${
            model.updateManager.latestUpdate?.downloadLink ?: "No download link found."
        }"
        updateClientAndLaunch(required)
    }

    @FXML
    fun cancel() {
        if (model.isClientUpdating) {
            model.showConfirm(
                "The new update is currently downloading. " +
                        "Would you like to close this window and cancel the process?"
            ) {
                if (required) {
                    model.showError("You must update your client to continue. Shutting down...")
                    exitProcess(0)
                } else {
                    if (!upToDate) {
                        if (!model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                            println("Update has been denied. You will no longer receive a notification about it until the next update.")
                            model.settings().setBoolean(Defaults.DENIEDUPDATE, true)
                            model.saveSettings()
                        }
                    }
                    close()
                }
            }
            return
        }
        if (required) {
            model.showError("You must update your client to continue. Shutting down...")
            exitProcess(0)
        } else {
            if (!upToDate) {
                if (!model.settings().getBoolean(Defaults.DENIEDUPDATE)) {
                    println("Update has been denied. You will no longer receive a notification about it until the next update.")
                    model.settings().setBoolean(Defaults.DENIEDUPDATE, true)
                    model.saveSettings()
                }
            }
            close()
        }
    }

    private fun close() {
        canceled = true
        model.isClientUpdating = false
        uiScope.cancel()
        ioScope.cancel()
        stage.close()
    }

    private fun updateClientAndLaunch(required: Boolean) {
        ioScope.launch {
            val downloadedClient = File(System.getProperty("user.home") + "/TWCD.jar")
            var con: HttpsURLConnection? = null
            var inputStream: BufferedInputStream? = null
            var fos: FileOutputStream? = null
            var bos: BufferedOutputStream? = null
            try {
                model.isClientUpdating = true
                if (downloadedClient.exists()) {
                    downloadedClient.delete()
                }
                downloadProgressBar.progress = 0.0
                con = URL(model.updateManager.latestUpdate!!.downloadLink).openConnection() as HttpsURLConnection
                con.addRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
                )
                con.addRequestProperty("Accept-Encoding", "gzip, deflate, br")
                con.addRequestProperty("Accept-Language", "en-US,en;q=0.9")
                con.addRequestProperty("Connection", "keep-alive")
                con.addRequestProperty("Sec-Fetch-Dest", "document")
                con.addRequestProperty("Sec-Fetch-Mode", "navigate")
                con.addRequestProperty("Sec-Fetch-Site", "cross-site")
                con.addRequestProperty("Sec-Fetch-User", "?1")
                con.addRequestProperty("Upgrade-Insecure-Requests", "1")
                con.addRequestProperty("User-Agent", model.randomUserAgent)
                con.connectTimeout = 30000
                con.readTimeout = 30000
                inputStream = BufferedInputStream(con.inputStream)
                val completeFileSize = con.contentLengthLong
                uiScope.launch {
                    downloadProgressLabel.text = "0/" + Tools
                        .bytesToString(completeFileSize)
                }
                val buffer = ByteArray(2048)
                fos = FileOutputStream(downloadedClient, true)
                bos = BufferedOutputStream(fos, buffer.size)
                var count: Int
                var total = 0L
                while (inputStream.read(buffer, 0, 2048).also { count = it } != -1) {
                    if (canceled) {
                        throw Exception("Canceled download process.")
                    }
                    total += count.toLong()
                    val finalTotal = total
                    uiScope.launch {
                        downloadProgressLabel.text = Tools.bytesToString(finalTotal) + "/" + Tools
                            .bytesToString(completeFileSize)
                        downloadProgressBar.progress = finalTotal / completeFileSize.toDouble()
                    }
                    bos.write(buffer, 0, count)
                }
                model.isClientUpdating = false
                System.err.println(
                    "Client downloaded successfully! Size: " + Tools.bytesToString(completeFileSize)
                            + " | Path: " + downloadedClient.absolutePath
                )
            } catch (e: Exception) {
                model.isClientUpdating = false
                uiScope.launch {
                    if (required) {
                        model.showError(
                            "Failed to download the client. The update link has been printed " +
                                    "to the command prompt or terminal. Please manually download and update it " +
                                    "yourself.", e
                        )
                        System.err.println("Update Link: ${UpdateManager.RELEASES_LINK}")
                        exitProcess(-1)

                    } else {
                        model.showError(
                            "Failed to download the client. The update link has been printed " +
                                    "to the console. Please manually download and update it " +
                                    "yourself or try again with Help > Check For Updates.", e
                        )
                        println("Update Link: ${UpdateManager.RELEASES_LINK}")
                        close()
                    }
                }
                return@launch
            } finally {
                bos?.close()
                fos?.close()
                con?.disconnect()
                inputStream?.close()
            }
            if (!canceled) {
                uiScope.launch {
                    try {
                        val alert = Alert(Alert.AlertType.INFORMATION)
                        val stage = alert.dialogPane.scene.window as Stage
                        alert.dialogPane.stylesheets.add(Main::class.java.getResource(Model.DIALOG_PATH).toString())
                        alert.dialogPane.styleClass.add("dialog")
                        stage.icons.add(Image(Main::class.java.getResource(Model.MAIN_ICON).toString()))
                        alert.title = "Download Complete!"
                        alert.headerText = "Please excuse this tedious update process. (-_-)/"
                        alert.contentText = """
                The new client has been downloaded. It can be found in your User folder. 
                Please copy it into the main folder.
                Path: ${downloadedClient.absolutePath}
                Close this window to shutdown and open the folder.
                """.trimIndent()
                        alert.showAndWait()
                        model.openFile(downloadedClient.parentFile, false)
                        exitProcess(0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        model.openFile(downloadedClient.parentFile, false)
                        exitProcess(0)
                    }
                }
            }
        }
    }
}