package xyz.winhok.nettap;

import java.io.*;
import java.util.stream.Collectors;
import android.util.Log;
import de.robv.android.xposed.XposedBridge;


/* XposedBridge logger with prefix. */
public class XposedLogger {
    private static final String LOGCAT_TAG = "NetTap";
    private String prefix;

    public XposedLogger(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Log to Xposed log. View through adb logcat.
     *
     * @param message Message will be formatted if additional arguments passed.
     * @param objects formatting arguments
     */
    public void log(String message, Object ... objects) {
        if (objects.length > 0) {
            message = String.format(message, objects);
        }
        String prefixedMessage = formatPrefixed(this.prefix, message);
        try {
            XposedBridge.log(prefixedMessage);
        } catch (Throwable ignored) {
        }
        mirrorToLogcat(prefixedMessage);
    }

    static String formatPrefixed(String prefix, String message) {
        return new BufferedReader(new StringReader(message)).lines()
            .collect(Collectors.joining("\n"+prefix+": ", prefix+": ", ""));
    }

    private static void mirrorToLogcat(String message) {
        try {
            for (String line : message.split("\\r?\\n")) {
                Log.i(LOGCAT_TAG, line);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Log-and-swallow variant used by hook paths where the log attempt itself
     * must never propagate (e.g. reflection throws on a mangled XposedBridge).
     */
    public void logSafe(String message, Object ... objects) {
        try {
            log(message, objects);
        } catch (Throwable ignored) {
        }
    }
}
