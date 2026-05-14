package xyz.winhok.nettap.ui.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HarFileExporter {
    private HarFileExporter() {
    }

    public static HarExportResult write(File exportDir, List<CaptureUiEvent> events) throws IOException {
        return write(exportDir, events, timestamp());
    }

    static HarExportResult write(
            File exportDir,
            List<CaptureUiEvent> events,
            String timestamp
    ) throws IOException {
        if (exportDir == null) {
            throw new IOException("export directory unavailable");
        }
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IOException("failed to create export directory");
        }
        File file = new File(exportDir, "nettap-" + timestamp + ".har");
        HarExporter.ExportedHar export = HarExporter.build(events, false);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file),
                StandardCharsets.UTF_8
        )) {
            writer.write(export.getJson());
        }
        return new HarExportResult(file, export.getEntryCount());
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date())
                + "-" + Long.toString(System.nanoTime(), 36);
    }
}
