package com.nobility.downloader.settings

import com.nobility.downloader.driver.DriverDefaults

/**
 * An enum used for easy settings management.
 * The type is no longer needed with the new system.
 * Settings only support numbers, strings and booleans.
 * Custom saves must be implemented inside SettingsMeta
 * @see com.nobility.downloader.entities.settings.SettingsMeta
 */
enum class Defaults(
    val key: String,
    val value: Any
) {
    BYPASSFREESPACECHECK("bypass_fs_check", false),
    DENIEDUPDATE("deniedupdate", false),
    UPDATEVERSION("version", "1.0"),
    PROXY("proxy", ""),
    TIMEOUT("timeout", 30),
    ENABLEPROXY("enable_proxy", false),
    SHOWCONTEXTONCLICK("show_context", true),
    SILENTDRIVER("silent_driver", true),
    SAVEFOLDER("save_folder", System.getProperty("user.home")),
    DOWNLOADTHREADS("download_threads", 2),
    LASTDOWNLOAD("last_dl", ""),
    TOASTTRANSPARENCY("toast_trans", 50.toDouble()),
    DRIVER("driver", DriverDefaults.CHROME.name),
    DEBUGMESSAGES("debug_messages", false),
    DOMAIN("wco_website", "wcofun"), //used to combat the constantly changing domain
    EXTENSION("wco_ext", "org"), //used to combat the constantly changing domain
    QUALITY("video_quality", Quality.LOW.tag),
    NEW_DB_WARNING("new_db_warn", false)

}