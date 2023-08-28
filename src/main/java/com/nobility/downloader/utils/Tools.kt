package com.nobility.downloader.utils

import com.google.common.base.CharMatcher
import com.nobility.downloader.Main
import com.nobility.downloader.Model
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.CharacterIterator
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.text.StringCharacterIterator
import java.util.*
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection

object Tools {

    @JvmStatic
    fun extractSlugFromLink(link: String?): String {
        if (link.isNullOrEmpty()) {
            return ""
        }
        return try {
            link.substring(
                //after the https:// and after the .com/
                link.ordinalIndexOf("/", 3) + 1
            )
        } catch (_: Exception) {
            ""
        }
    }

    fun extractExtensionFromLink(link: String): String {
        return link.substringAfterLast(".").substringBefore("/")
    }

    /**
     * Extracts the domain name without the www. (because it's never needed)
     * and without the extension.
     */
    fun extractDomainFromLink(link: String): String {
        val mLink = link.replace("www.", "")
        val key1 = "https://"
        return mLink.substring(
            mLink.indexOf(key1) + key1.length,
            mLink.indexOf(".")
        )
    }

    /**
     * This used to fix strings in order to be used as files.
     * Files can't contain certain characters.
     */
    fun fixTitle(title: String, replaceDotAtStart: Boolean = false): String {
        var mTitle = title
        if (replaceDotAtStart) {
            if (mTitle.startsWith(".")) {
                mTitle = mTitle.substring(1)
            }
        }
        return mTitle.trim { it <= ' ' }
            .replace("[\\\\/*?\"<>|]".toRegex(), "_")
            .replace(":".toRegex(), ";")
            .replace("ﾒ", "'")
    }

    //used to save/retrieve images for series
    private fun stripExtraFromTitle(title: String): String {
        var mTitle = title
        val subKeyword = "English Subbed"
        val dubKeyword = "English Dubbed"
        if (mTitle.contains(subKeyword)) {
            mTitle = mTitle.substringBefore(subKeyword)
        } else if (mTitle.contains(dubKeyword)) {
            mTitle = mTitle.substringBefore(dubKeyword)
        }
        return mTitle.trim()
    }

    //used to fetch image from files for an episode
    private fun seasonNameFromEpisode(title: String): String {
        var mTitle = title
        val episodeKeyword = "Episode"
        if (mTitle.contains(episodeKeyword)) {
            mTitle = mTitle.substringBefore(episodeKeyword)
        }
        return mTitle
    }

    fun titleForImages(title: String): String {
        return seasonNameFromEpisode(
            stripExtraFromTitle(
                fixTitle(
                    title,
                    true
                )
            )
        ).trim() + ".jpg"
    }

    @JvmField
    val percentFormat = DecimalFormat("#.##%")

    fun bytesToMB(bytes: Long): Double {
        val kb = (bytes / 1024L).toInt()
        return (kb / 1024L).toDouble()
    }

    @JvmStatic
    fun bytesToString(bytes: Long): String {
        var mBytes = bytes
        if (-1000 < mBytes && mBytes < 1000) {
            return "$mBytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (mBytes <= -999950 || mBytes >= 999950) {
            mBytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", mBytes / 1000.0, ci.current())
    }

    const val dateFormat = "MM/dd/yyyy hh:mm:ssa"

    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat(dateFormat)
            return sdf.format(Date())
        }

    @JvmStatic
    fun dateFormatted(time: Long): String {
        val sdf = SimpleDateFormat(dateFormat)
        return sdf.format(time)
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

    private val episodesComparator = Comparator { e1: String, e2: String ->
        try {
            val episodeKey = "Episode"
            val episodePattern = Pattern.compile("$episodeKey \\d{0,3}")
            val e1Matcher = episodePattern.matcher(e1)
            val e2Matcher = episodePattern.matcher(e2)
            var e1Number = ""
            var e2Number = ""
            while (e1Matcher.find()) {
                e1Number = e1Matcher.group(0)
            }
            while (e2Matcher.find()) {
                e2Number = e2Matcher.group(0)
            }
            if (e1Number.isNotEmpty() && e2Number.isNotEmpty()) {
                return@Comparator Integer.parseInt(
                    CharMatcher.inRange('0', '9').retainFrom(e1Number)
                ).compareTo(Integer.parseInt(
                    CharMatcher.inRange('0', '9').retainFrom(e2Number)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@Comparator e1.compareTo(e2)
    }

    val seriesComparator = Comparator { e1: String, e2: String ->
        try {
            val seasonKey = "Season"
            val seasonPattern = Pattern.compile("$seasonKey \\d{0,3}")
            val s1Matcher = seasonPattern.matcher(e1)
            val s2Matcher = seasonPattern.matcher(e2)
            var s1Number = ""
            var s2Number = ""
            while (s1Matcher.find()) {
                s1Number = s1Matcher.group(0)
            }
            while (s2Matcher.find()) {
                s2Number = s2Matcher.group(0)
            }
            if (s1Number.isNotEmpty() && s2Number.isNotEmpty()) {
                return@Comparator Integer.parseInt(
                    CharMatcher.inRange('0', '9').retainFrom(s1Number)
                ).compareTo(Integer.parseInt(
                    CharMatcher.inRange('0', '9').retainFrom(s2Number)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@Comparator e1.compareTo(e2)
    }

    private val isSeriesComparator = Comparator { e1: String, e2: String ->
        val key = "Season"
        if (e1.contains(key) && !e2.contains(key)) {
            return@Comparator 0
        } else if (e2.contains(key) && !e1.contains(key)) {
            return@Comparator 0
        } else {
            return@Comparator -1
        }
    }

    val abcTextFlowComparator = Comparator { e1: TextFlow, e2: TextFlow ->
        if (e1.children.isEmpty() || e2.children.isEmpty()) {
            return@Comparator 0
        }
        val e1Text = (e1.children[0] as Text).text
        val e2Text = (e2.children[0] as Text).text
        return@Comparator e1Text.compareTo(e2Text)
    }

    val mainEpisodesComparator = isSeriesComparator.then(seriesComparator).then(episodesComparator)

    suspend fun loadImageFromURL(model: Model, imageLink: String, imageView: ImageView) {
        try {
            val con = URL(imageLink).openConnection() as HttpURLConnection
            con.setRequestProperty("user-agent", model.randomUserAgent)
            con.readTimeout = 15000
            con.connectTimeout = 15000
            con.connect()
            withContext(Dispatchers.JavaFx) {
                try {
                    imageView.image = Image(con.inputStream)
                    imageView.setOnMouseClicked {
                        model.showLinkPrompt(
                            imageLink,
                            "Would you like to open this image in your default browser?",
                            true
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.JavaFx) {
                        val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                        if (icon != null) {
                            imageView.image = Image(icon)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "Failed to load image for: " +
                        "$imageLink Error: ${e.localizedMessage}"
            )
            withContext(Dispatchers.JavaFx) {
                val icon = Main::class.java.getResourceAsStream(Model.NO_IMAGE_ICON)
                if (icon != null) {
                    imageView.image = Image(icon)
                }
            }
        }
    }

    suspend fun downloadFile(
        link: String,
        output: File,
        timeout: Int,
        userAgent: String
    ) = withContext(Dispatchers.IO) {
        var offset = 0L
        if (output.exists()) {
            offset = output.length()
        }
        val con = URL(link).openConnection() as HttpsURLConnection
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
        con.connectTimeout = timeout
        con.readTimeout = timeout
        con.setRequestProperty("Range", "bytes=$offset-")
        con.addRequestProperty("User-Agent", userAgent)
        //if (offset != 0L) {
          //  println("Detected incomplete images: $link - Attempting to finish it.")
        //}
        val buffer = ByteArray(2048)
        val bis = BufferedInputStream(con.inputStream)
        val fos = FileOutputStream(output, true)
        val bos = BufferedOutputStream(fos, buffer.size)
        var count: Int
        var total = offset
        while (bis.read(buffer, 0, 2048).also { count = it } != -1) {
            total += count.toLong()
            bos.write(buffer, 0, count)
        }
        bos.flush()
        bos.close()
        fos.flush()
        fos.close()
        bis.close()
        con.disconnect()
    }
}