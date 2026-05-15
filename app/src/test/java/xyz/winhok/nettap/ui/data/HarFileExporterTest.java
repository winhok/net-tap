package xyz.winhok.nettap.ui.data;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class HarFileExporterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesHarFileWithTimestampedName() throws Exception {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/users?active=true",
                200,
                null
        ));
        File exportDir = temporaryFolder.newFolder("export");

        HarExportResult result = HarFileExporter.write(
                exportDir,
                Collections.singletonList(event),
                "20260514-010203"
        );

        assertTrue(result.getFile().getName().startsWith("nettap-20260514-010203"));
        assertTrue(result.getFile().getName().endsWith(".har"));
        String text = new String(Files.readAllBytes(result.getFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"entries\""));
        assertTrue(text.contains("https://api.example.com/v1/users?active=true"));
        assertTrue(result.getEntryCount() == 1);
    }

    @Test
    public void entryCountMatchesEntriesActuallyWritten() throws Exception {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/users?active=true",
                200,
                null
        ));
        File exportDir = temporaryFolder.newFolder("export-with-null");

        HarExportResult result = HarFileExporter.write(
                exportDir,
                Arrays.asList(event, null),
                "20260514-010204"
        );

        String text = new String(Files.readAllBytes(result.getFile().toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("\"skippedEntries\": 1"));
        assertTrue(result.getEntryCount() == 1);
    }

    @Test
    public void defaultNamesDoNotOverwriteRapidExports() throws Exception {
        CaptureUiEvent event = CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/v1/users?active=true",
                200,
                null
        ));
        File exportDir = temporaryFolder.newFolder("rapid-export");

        HarExportResult first = HarFileExporter.write(exportDir, Collections.singletonList(event));
        HarExportResult second = HarFileExporter.write(exportDir, Collections.singletonList(event));

        assertNotEquals(first.getFile().getName(), second.getFile().getName());
        assertTrue(first.getFile().exists());
        assertTrue(second.getFile().exists());
    }
}
