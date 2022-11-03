package com.nobility.downloader.utils

import javafx.application.Platform
import javafx.scene.control.TextArea
import java.io.IOException
import java.io.OutputStream
import java.util.*
import javax.swing.SwingUtilities

class TextOutput(private val textArea: TextArea) : OutputStream() {

    private val sb = StringBuilder()
    var size = 0
        private set

    init {
        sb.append(time()).append(" ")
    }

    private fun time(): String {
        val c = Calendar.getInstance()
        val hour = c[Calendar.HOUR_OF_DAY]
        val minute = c[Calendar.MINUTE]
        return "[" + hour + ":" + (if (minute.toString().length == 1) "0" else "") + minute + "]"
    }

    override fun flush() {}
    override fun close() {}

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (b == '\r'.code) return
        if (b == '\n'.code) {
            val text = """
                $sb
                
                """.trimIndent()
            SwingUtilities.invokeLater {
                Platform.runLater { textArea.appendText(text) }
                size++
            }
            sb.setLength(0)
            sb.append(time()).append(" ")
            return
        }
        if (sb.isEmpty()) {
            sb.append(time()).append(" ")
        }
        sb.append(b.toChar())
    }
}