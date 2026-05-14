package xyz.winhok.nettap.ui.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RawJsonFileExporter {
    private RawJsonFileExporter() {
    }

    public static File write(File exportDir, CaptureUiEvent event) throws IOException {
        return write(exportDir, event, timestamp());
    }

    static File write(File exportDir, CaptureUiEvent event, String timestamp) throws IOException {
        if (exportDir == null) {
            throw new IOException("export directory unavailable");
        }
        if (event == null) {
            throw new IOException("event unavailable");
        }
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("failed to create export directory");
        }
        File file = new File(exportDir, "nettap-raw-" + safeName(event.getId()) + "-" + timestamp + ".json");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file),
                StandardCharsets.UTF_8
        )) {
            writer.write(event.getRawJson());
        }
        return file;
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "event";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date())
                + "-" + Long.toString(System.nanoTime(), 36);
    }
}
