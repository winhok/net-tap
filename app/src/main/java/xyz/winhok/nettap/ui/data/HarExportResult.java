package xyz.winhok.nettap.ui.data;

import java.io.File;

public final class HarExportResult {
    private final File file;
    private final int entryCount;

    HarExportResult(File file, int entryCount) {
        this.file = file;
        this.entryCount = entryCount;
    }

    public File getFile() {
        return file;
    }

    public int getEntryCount() {
        return entryCount;
    }
}
