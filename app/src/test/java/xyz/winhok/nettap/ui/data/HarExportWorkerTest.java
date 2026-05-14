package xyz.winhok.nettap.ui.data;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class HarExportWorkerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesOnWorkerExecutorAndUsesSnapshotOfEvents() throws Exception {
        ArrayList<CaptureUiEvent> events = new ArrayList<>();
        events.add(CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/users",
                200,
                null
        )));
        DeferredExecutor workerExecutor = new DeferredExecutor();
        HarExportWorker worker = new HarExportWorker(workerExecutor, Runnable::run);
        AtomicReference<HarExportResult> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        worker.export(temporaryFolder.newFolder("export"), events, new HarExportWorker.Callback() {
            @Override
            public void onSuccess(HarExportResult value) {
                result.set(value);
            }

            @Override
            public void onFailure(Exception exception) {
                failure.set(exception);
            }
        });
        events.add(CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/changed",
                201,
                null
        )));

        assertEquals(1, workerExecutor.pendingCount());
        workerExecutor.runNext();

        assertNull(failure.get());
        assertEquals(1, result.get().getEntryCount());
    }

    @Test
    public void reportsFailuresOnCallbackExecutor() {
        DeferredExecutor callbackExecutor = new DeferredExecutor();
        HarExportWorker worker = new HarExportWorker(Runnable::run, callbackExecutor);
        AtomicReference<Exception> failure = new AtomicReference<>();

        worker.export(null, Arrays.asList(), new HarExportWorker.Callback() {
            @Override
            public void onSuccess(HarExportResult value) {
            }

            @Override
            public void onFailure(Exception exception) {
                failure.set(exception);
            }
        });

        assertNull(failure.get());
        callbackExecutor.runNext();
        assertEquals("export directory unavailable", failure.get().getMessage());
    }

    private static final class DeferredExecutor implements Executor {
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pendingCount() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove(0).run();
        }
    }
}
