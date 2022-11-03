package com.nobility.downloader.updates

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nobility.downloader.Model
import com.nobility.downloader.settings.Defaults
import com.nobility.downloader.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UpdateManager(private val model: Model) {

    private val githubLatest = "https://api.github.com/repos/NobilityDeviant/Wcofun.com_Downloader/releases/latest"
    var latestUpdate: Update? = null

    private fun fetchLatestRelease(): Resource<JsonObject> {
        try {
            removeValidation()
            val urlConnection = URL(githubLatest).openConnection() as HttpsURLConnection
            urlConnection.readTimeout = 20000
            urlConnection.connectTimeout = 20000
            urlConnection.instanceFollowRedirects = true
            urlConnection.setRequestProperty("user-agent", model.randomUserAgent)
            urlConnection.connect()
            val `in` = BufferedReader(InputStreamReader(urlConnection.inputStream))
            val stringBuilder = StringBuilder()
            var inputLine: String?
            while (`in`.readLine().also { inputLine = it } != null) {
                stringBuilder.append(inputLine).append("\n")
            }
            `in`.close()
            urlConnection.disconnect()
            return Resource.Success(
                JsonParser.parseString(stringBuilder.toString()).asJsonObject
            )
        } catch (e: Exception) {
            return Resource.Error(e)
        }
    }

    private fun parseLatestRelease(): Resource<Update> {
        val result = fetchLatestRelease()
        if (result.data != null) {
            val json = result.data
            val version = json["tag_name"].asString
            val body = json["body"].asString
            val array = json.getAsJsonArray("assets")
            if (array != null) {
                for (element in array) {
                    if (element.isJsonObject) {
                        val o = element.asJsonObject
                        if (o.has("browser_download_url")) {
                            val url = o["browser_download_url"].asString
                            if (url.endsWith(".jar")) {
                                return Resource.Success(Update(version, url, body))
                            }
                        }
                    }
                }
            }
        } else if (result.message != null) {
            return Resource.Error(result.message)
        }
        return Resource.Error("Failed to parse github api response.")
    }

    suspend fun checkForUpdates(prompt: Boolean, refresh: Boolean) = withContext(Dispatchers.IO) {
        if (latestUpdate == null || refresh) {
            val result = parseLatestRelease()
            if (result.data != null) {
                latestUpdate = result.data
            } else {
                println(result.message)
            }
        }
        if (latestUpdate == null) {
            println("Failed to find latest update details. No error found.")
            return@withContext
        }
        if (!model.settings().stringSetting(Defaults.UPDATEVERSION)
                .equals(latestUpdate!!.version, ignoreCase = true)) {
            model.settings().setSetting(Defaults.DENIEDUPDATE, false)
        }
        if (!model.settings().booleanSetting(Defaults.DENIEDUPDATE)) {
            model.settings().setSetting(Defaults.UPDATEVERSION, latestUpdate!!.version)
            val latest = isLatest(latestUpdate!!.version)
            if (latest && !prompt) {
                return@withContext
            }
            withContext(Dispatchers.JavaFx) {
                model.showUpdateConfirm(
                    (if (latest) "Updated" else "Update Available") + " - ver. "
                            + latestUpdate!!.version, false, latest
                )
            }
        }
    }

    private fun isLatest(latest: String?): Boolean {
        if (latest == null || latest == CURRENT_VERSION) {
            return true
        }
        try {
            val latestSplit = latest.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val current = CURRENT_VERSION.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (latestSplit[0].toInt() > current[0].toInt()) {
                return false
            }
            if (latestSplit[1].toInt() > current[1].toInt()) {
                return false
            }
            if (latestSplit[2].toInt() > current[2].toInt()) {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
        return false
    }

    private fun removeValidation() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(arg0: Array<X509Certificate>, arg1: String) {}
            override fun checkServerTrusted(arg0: Array<X509Certificate>, arg1: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        })
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (ignored: Exception) {
        }
    }

    companion object {
        const val CURRENT_VERSION = "1.4.9"
        const val RELEASES_LINK = "https://github.com/NobilityDeviant/Wcofun.com_Downloader/releases"
    }
}