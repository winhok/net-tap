package xyz.winhok.nettap.ui.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class HarExportWorker {
    private final Executor workerExecutor;
    private final Executor callbackExecutor;

    public HarExportWorker(Executor workerExecutor, Executor callbackExecutor) {
        this.workerExecutor = workerExecutor;
        this.callbackExecutor = callbackExecutor;
    }

    public void export(File exportDir, List<CaptureUiEvent> events, Callback callback) {
        List<CaptureUiEvent> snapshot = snapshot(events);
        workerExecutor.execute(() -> {
            try {
                HarExportResult result = HarFileExporter.write(exportDir, snapshot);
                callbackExecutor.execute(() -> callback.onSuccess(result));
            } catch (IOException | IllegalArgumentException e) {
                callbackExecutor.execute(() -> callback.onFailure(e));
            }
        });
    }

    private static List<CaptureUiEvent> snapshot(List<CaptureUiEvent> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public interface Callback {
        void onSuccess(HarExportResult result);

        void onFailure(Exception exception);
    }
}
