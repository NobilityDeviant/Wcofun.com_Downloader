package com.nobility.downloader.utils;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;

public class TextOutput extends OutputStream {

    private final TextArea textArea;
    private final StringBuilder sb = new StringBuilder();
    private int size;

    public TextOutput(final TextArea textArea) {
        this.textArea = textArea;
        sb.append(time()).append(" ");
    }

    private String time() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        return "[" + hour + ":" + (String.valueOf(minute).length() == 1 ? "0" : "") + minute + "]";
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public void write(int b) throws IOException {

        if (b == '\r')
            return;

        if (b == '\n') {
            final String text = sb + "\n";
            SwingUtilities.invokeLater(() -> {
                Platform.runLater(() -> textArea.appendText(text));
                size++;
            });
            sb.setLength(0);
            sb.append(time()).append(" ");
            return;
        }
        if (sb.length() == 0) {
            sb.append(time()).append(" ");
        }
        sb.append((char) b);
    }

    public int getSize() {
        return size;
    }
}