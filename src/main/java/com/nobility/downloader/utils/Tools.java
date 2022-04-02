package com.nobility.downloader.utils;

import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Calendar;
import java.util.Date;

public class Tools {

    public static final DecimalFormat percentFormat = new DecimalFormat("#.##%");

    public static double bytesToKB(long bytes) {
        return (double) (bytes / 1024L);
    }

    public static double bytesToMB(long bytes) {
        int kb = (int) (bytes / 1024L);
        return (double) (kb / 1024L);
    }

    public static String bytesToString(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    public final static String dateFormat = "MM/dd/yyyy hh:mm:ssa";

    public static String getDateFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(new Date());
    }

    public static String getDate() {
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_MONTH);
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1;
        return month + "/" + day + "/" + year;
    }

    public static String getCurrentTime() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        int minute = c.get(Calendar.MINUTE);
        return hour + ":" + minute;
    }
}
