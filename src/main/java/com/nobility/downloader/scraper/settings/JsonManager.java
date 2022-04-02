package com.nobility.downloader.scraper.settings;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import com.nobility.downloader.downloads.DownloadSave;
import com.nobility.downloader.history.HistorySave;

import java.io.File;
import java.io.IOException;

public class JsonManager {

    private static final String settingsName = "settings.json";
    private static final String downloadsName = "downloads.json";
    private static final String seriesName = "series.json";
    private static final String savePath = "./resources/";

    public static void saveSettings(Settings settings) {
        if (settings == null) {
            System.err.println("Settings cannot be null.");
            return;
        }
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        try {
            mapper.writeValue(new File(save.getAbsolutePath(), settingsName), settings);
            //System.out.println("Successfully saved settings to: " + save.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save settings. Error: " + e.getLocalizedMessage());
            //e.printStackTrace();
        }
    }

    public static Settings loadSettings() {
        //TypeFactory typeFactory = mapper.getTypeFactory();
        //MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Theme.class);
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        Settings settings = null;
        try {
            settings = mapper.readValue(new File(save.getAbsolutePath(), settingsName), Settings.class);
        } catch (IOException e) {
            //e.printStackTrace();
            System.err.println("Unable to find settings save file. Creating new one...");
        }
        return settings;
    }

    public static void saveDownloads(DownloadSave downloadSave) {
        if (downloadSave == null) {
            System.err.println("DownloadSave cannot be null.");
            return;
        }
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {
            mapper.writeValue(new File(save.getAbsolutePath(), downloadsName), downloadSave);
            //System.out.println("Successfully saved settings to: " + save.getAbsolutePath());
        } catch (IOException e) {
            //e.printStackTrace();
            System.err.println("Failed to save downloads. Error: " + e.getLocalizedMessage());
        }
    }

    public static DownloadSave loadDownloads() {
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        DownloadSave downloadSave = null;
        try {
            downloadSave = mapper.readValue(new File(save.getAbsolutePath(), downloadsName), DownloadSave.class);
        } catch (IOException e) {
            //e.printStackTrace();
            System.err.println("Unable to find downloads save file. Creating new one...");
        }
        return downloadSave;
    }

    public static void saveHistory(HistorySave historySave) {
        if (historySave == null) {
            System.err.println("HistorySave cannot be null.");
            return;
        }
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        try {
            mapper.writeValue(new File(save.getAbsolutePath(), seriesName), historySave);
        } catch (IOException e) {
            //e.printStackTrace();
            System.err.println("Failed to save history. Error: " + e.getLocalizedMessage());
        }
    }

    public static HistorySave loadHistory() {
        File save = new File(savePath);
        if (!save.exists() && !save.mkdirs()) {
            System.err.println("Unable to find or create save path: " + savePath);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParanamerModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        HistorySave historySave = null;
        try {
            historySave = mapper.readValue(new File(save.getAbsolutePath(), seriesName), HistorySave.class);
        } catch (IOException e) {
            //e.printStackTrace();
            System.err.println("Unable to find history save file. Creating new one...");
        }
        return historySave;
    }
}
