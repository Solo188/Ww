package com.parentcontrol;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {

    private static final List<String> logs = new ArrayList<>();
    private static final int MAX_LOGS = 200;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public static synchronized void log(String message) {
        String entry = sdf.format(new Date()) + " " + message;
        logs.add(entry);
        if (logs.size() > MAX_LOGS) logs.remove(0);
        android.util.Log.d("StatusBot", message);
    }

    public static synchronized String getLogs() {
        StringBuilder sb = new StringBuilder();
        for (int i = logs.size() - 1; i >= 0; i--) {
            sb.append(logs.get(i)).append("\n");
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
