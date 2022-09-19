package com.nobility.downloader.downloads

import com.fasterxml.jackson.annotation.JsonIgnore
import com.nobility.downloader.cache.Episode
import com.nobility.downloader.utils.StringChecker
import com.nobility.downloader.utils.Tools
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.io.File

class Download(var downloadPath: String, val dateAdded: String, val episode: Episode) {
    var fileSize: Long = 0

    //used for the session, doesn't need to be saved.
    @JsonIgnore
    var isDownloading = false

    @JsonIgnore
    var isQueued = false

    @get:JsonIgnore
    @JsonIgnore
    val progress: StringProperty

    init {
        progress = SimpleStringProperty("0%")
    }

    fun updateProgress() {
        if (isQueued) {
            setProgressValue("Queued")
            return
        }
        val video = File(downloadPath)
        if (video.exists()) {
            val ratio = video.length() / fileSize.toDouble()
            setProgressValue(Tools.percentFormat.format(ratio))
        } else {
            setProgressValue("File Not Found")
        }
    }

    private fun setProgressValue(value: String) {
        if (progress.value != value) {
            progress.value = value
        }
    }

    fun update(download: Download) {
        fileSize = download.fileSize
        isDownloading = download.isDownloading
        updateProgress()
    }

    @get:JsonIgnore
    val downloadFile: File?
        get() {
            if (!StringChecker.isNullOrEmpty(downloadPath)) {
                val file = File(downloadPath)
                if (file.exists()) {
                    return file
                }
            }
            return null
        }

    @get:JsonIgnore
    val isComplete: Boolean
        get() {
            if (fileSize <= 0) {
                return false
            }
            val file = downloadFile
            return if (file != null) {
                if (file.length() <= 0) {
                    false
                } else file.length() >= fileSize
            } else false
        }
}