package com.nobility.downloader.utils

import com.nobility.downloader.settings.Quality

private const val LINK_KEY = "(link)"
private const val RES_KEY = "(res)"

object JavascriptHelper {

    fun changeUrlToVideoFunction(
        functionChildLink: String,
        quality: Quality
    ): String {
        return videoLoadingFunction
            .replace(LINK_KEY, functionChildLink)
            .replace(RES_KEY, quality.htmlText)
    }

    fun changeUrl(
        newUrl: String
    ): String {
        return directUrlChanger.replace(RES_KEY, newUrl)
    }

    /**
     * Get all video links using an edited version of a function found inside
     * the video frames source code.
     * Queries the url and redirects to that url so we can
     * extract it with Selenium.
     */
    private const val videoLoadingFunction = "\$.getJSON(\"$LINK_KEY\", function(response){\n" +
            "        vsd     = response.enc;\n" +
            "        vhd     = response.hd;\n" +
            "        vfhd    = response.fhd;\n" +
            "        cdn     = response.cdn;\n" +
            "        server  = response.server;\n" +
            "\t\tlocation.href = server + '/getvid?evid=' + $RES_KEY\n" +
            "});"

    private const val directUrlChanger = "location.href = '$RES_KEY'"

}