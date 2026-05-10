package xyz.winhok.nettap;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

import android.app.Application;
import android.content.Context;

import static xdroid.core.Global.getContext;
import static xdroid.core.ObjectUtils.notNull;


/* Internal storage file logger. */
public class FileLogger {
    private String filename;
    private boolean rawOnly;
    private boolean shutdownHookRegistered;
    private FileOutputStream fos;

    public FileLogger(String filename) {
        this(filename, false);
    }

    public FileLogger(String filename, boolean rawOnly) {
        this.filename = filename;
        this.rawOnly = rawOnly;
    }

    /**
     * Open file on first call.
     */
    private void initialize() {
        if (this.fos == null) {
            try {
                Application app = (Application) notNull(getContext());
                this.fos = app.openFileOutput(this.filename, Context.MODE_APPEND);
                registerShutdownHook();
            } catch(Exception e) {
                NetTap.getXposedLogger().log("failed to start logger: %s", e);
            }
        }
    }

    private void registerShutdownHook() {
        if (this.shutdownHookRegistered) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                FileLogger.this.closeQuietly();
            }
        });
        this.shutdownHookRegistered = true;
    }

    /**
     * Close file on shutdown.
     */
    public synchronized void closeQuietly() {
        if (this.fos == null) {
            return;
        }
        try {
            this.fos.flush();
            this.fos.close();
        } catch(Exception e) {
        } finally {
            this.fos = null;
        }
    }

    /**
     * Append to log file in internal storage of the hooked app.
     * 
     * @param String message        Message will be formatted if additional arguments passed.
     * @param Object[] objects      
     */
    public synchronized void log(String message, Object ... objects) {
        if (objects.length > 0) {
            message = String.format(message, (Object[]) objects);
        }

        if (this.rawOnly) {
            NetTap.getXposedLogger().log(message);
            return;
        }

        try {
            initialize();
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            this.fos.write(String.format("%-31s %s\n", timestamp, message).getBytes(StandardCharsets.UTF_8));
            this.fos.flush();
        } catch(Exception e) {
            NetTap.getXposedLogger().log("failed to log request URL: " + e);
        }
    }

    /**
     * Append an unmodified line to the log file in internal storage of the hooked app.
     */
    public synchronized void logRawLine(String line) {
        try {
            initialize();
            this.fos.write((String.valueOf(line) + "\n").getBytes(StandardCharsets.UTF_8));
            this.fos.flush();
        } catch(Exception e) {
            NetTap.getXposedLogger().log("failed to log raw line: %s", e);
        }
    }
}
