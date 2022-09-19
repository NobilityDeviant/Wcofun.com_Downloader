package com.nobility.downloader.settings

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import com.nobility.downloader.cache.WebsiteData
import com.nobility.downloader.downloads.Downloads
import com.nobility.downloader.history.History
import java.io.File
import java.io.IOException

object JsonManager {

    private const val settingsName = "settings.json"
    private const val downloadsName = "downloads.json"
    private const val seriesName = "series.json"
    private const val websiteDataName = "data.json"
    private const val savePath = "./resources/"

    fun saveSettings(settings: Settings?) {
        if (settings == null) {
            System.err.println("Settings cannot be null.")
            return
        }
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        try {
            mapper.writeValue(File(save.absolutePath, settingsName), settings)
        } catch (e: IOException) {
            e.printStackTrace();
            System.err.println("Failed to save settings. Error: " + e.localizedMessage)
        }
    }

    fun loadSettings(): Settings? {
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return null
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        var settings: Settings? = null
        try {
            settings = mapper.readValue(File(save.absolutePath, settingsName), Settings::class.java)
        } catch (e: IOException) {
            e.printStackTrace();
            System.err.println("Unable to find or load settings save file. Creating new one...")
        }
        return settings
    }

    fun saveDownloads(downloads: Downloads?) {
        if (downloads == null) {
            System.err.println("DownloadSave cannot be null.")
            return
        }
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        try {
            mapper.writeValue(File(save.absolutePath, downloadsName), downloads)
            //System.out.println("Successfully saved settings to: " + save.getAbsolutePath());
        } catch (e: IOException) {
            //e.printStackTrace();
            System.err.println("Failed to save downloads. Error: " + e.localizedMessage)
        }
    }

    fun loadDownloads(): Downloads? {
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return null
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        var downloads: Downloads? = null
        try {
            downloads = mapper.readValue(File(save.absolutePath, downloadsName), Downloads::class.java)
        } catch (e: IOException) {
            //e.printStackTrace();
            System.err.println("Unable to find or load downloads save file. Creating new one...")
        }
        return downloads
    }

    fun saveHistory(history: History?) {
        if (history == null) {
            System.err.println("HistorySave cannot be null.")
            return
        }
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        try {
            mapper.writeValue(File(save.absolutePath, seriesName), history)
        } catch (e: IOException) {
            //e.printStackTrace();
            System.err.println("Failed to save history. Error: " + e.localizedMessage)
        }
    }

    fun loadHistory(): History? {
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return null
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        var history: History? = null
        try {
            history = mapper.readValue(File(save.absolutePath, seriesName), History::class.java)
        } catch (e: IOException) {
            e.printStackTrace()
            System.err.println("Unable to find or load history save file. Creating new one...")
        }
        return history
    }

    fun saveWebsiteData(websiteData: WebsiteData?) {
        if (websiteData == null) {
            System.err.println("Data cannot be null.")
            return
        }
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        try {
            mapper.writeValue(File(save.absolutePath, websiteDataName), websiteData)
        } catch (e: IOException) {
            e.printStackTrace();
            System.err.println("Failed to save website data. Error: " + e.localizedMessage)
        }
    }

    fun loadWebsiteData(): WebsiteData? {
        val save = File(savePath)
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: $savePath")
            return null
        }
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerModule(ParanamerModule())
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY
        )
        var data: WebsiteData? = null
        try {
            data = mapper.readValue(File(save.absolutePath, websiteDataName), WebsiteData::class.java)
        } catch (e: IOException) {
            e.printStackTrace();
            System.err.println("Unable to find or load settings data file. Creating new one...")
        }
        return data
    }

}