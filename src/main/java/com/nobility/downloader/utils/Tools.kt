package com.nobility.downloader.utils

import com.nobility.downloader.Model
import java.text.CharacterIterator
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.text.StringCharacterIterator
import java.util.*

object Tools {

    fun fixTitle(title: String): String {
        return title.trim { it <= ' ' }
            .replace("[\\\\/*?\"<>|]".toRegex(), "_")
            .replace(":".toRegex(), ";")
    }

    @JvmStatic
    fun fixOldLink(link: String?): String {
        if (link == null) {
            return ""
        }
        return if (link.startsWith(Model.OLD_WEBSITE)) {
            link.replace(Model.OLD_WEBSITE, Model.WEBSITE)
        } else link
    }

    @JvmField
    val percentFormat = DecimalFormat("#.##%")

    fun bytesToKB(bytes: Long): Double {
        return (bytes / 1024L).toDouble()
    }

    @JvmStatic
    fun bytesToMB(bytes: Long): Double {
        val kb = (bytes / 1024L).toInt()
        return (kb / 1024L).toDouble()
    }

    @JvmStatic
    fun bytesToString(_bytes: Long): String {
        var bytes = _bytes
        if (-1000 < bytes && bytes < 1000) {
            return "$bytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current())
    }

    const val dateFormat = "MM/dd/yyyy hh:mm:ssa"
    @JvmStatic
    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat(dateFormat)
            return sdf.format(Date())
        }
    val date: String
        get() {
            val c = Calendar.getInstance()
            val day = c[Calendar.DAY_OF_MONTH]
            val year = c[Calendar.YEAR]
            val month = c[Calendar.MONTH] + 1
            return "$month/$day/$year"
        }
    val currentTime: String
        get() {
            val c = Calendar.getInstance()
            val hour = c[Calendar.HOUR]
            val minute = c[Calendar.MINUTE]
            return "$hour:$minute"
        }
}