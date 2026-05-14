package xyz.winhok.nettap.ui.data;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RawJsonFileExporterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesRawJsonWithTimestampedName() throws Exception {
        String json = CaptureUiEventTest.sampleJson("https://api.example.com/raw", 200, null);
        CaptureUiEvent event = CaptureUiEvent.fromJson(json);
        File exportDir = temporaryFolder.newFolder("export");

        File file = RawJsonFileExporter.write(exportDir, event, "20260514-010203");

        assertTrue(file.getName().startsWith("nettap-raw-request-1-20260514-010203"));
        assertTrue(file.getName().endsWith(".json"));
        assertEquals(json, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void writeRejectsMissingExportDirectory() throws Exception {
        String json = CaptureUiEventTest.sampleJson("https://api.example.com/raw", 200, null);
        CaptureUiEvent event = CaptureUiEvent.fromJson(json);

        try {
            RawJsonFileExporter.write(null, event, "20260514-010203");
            fail("expected IOException");
        } catch (java.io.IOException expected) {
            assertEquals("export directory unavailable", expected.getMessage());
        }
    }

    @Test
    public void writeRejectsMissingEvent() throws Exception {
        File exportDir = temporaryFolder.newFolder("export");

        try {
            RawJsonFileExporter.write(exportDir, null, "20260514-010203");
            fail("expected IOException");
        } catch (java.io.IOException expected) {
            assertEquals("event unavailable", expected.getMessage());
        }
    }

    @Test
    public void writeSanitizesBlankAndUnsafeIds() throws Exception {
        CaptureUiEvent blankId = CaptureUiEvent.fromJson(
                CaptureUiEventTest.sampleJson("https://api.example.com/raw", 200, null)
                        .replace("\"id\":\"request-1\"", "\"id\":\"   \""));
        CaptureUiEvent unsafeId = CaptureUiEvent.fromJson(
                CaptureUiEventTest.sampleJson("https://api.example.com/raw", 200, null)
                        .replace("\"id\":\"request-1\"", "\"id\":\"a/b:c\""));
        File exportDir = temporaryFolder.newFolder("export");

        File blankFile = RawJsonFileExporter.write(exportDir, blankId, "20260514-010203");
        File unsafeFile = RawJsonFileExporter.write(exportDir, unsafeId, "20260514-010203");

        assertTrue(blankFile.getName().startsWith("nettap-raw-event-20260514-010203"));
        assertTrue(unsafeFile.getName().startsWith("nettap-raw-a_b_c-20260514-010203"));
    }

    @Test
    public void defaultNamesDoNotOverwriteRapidShares() throws Exception {
        String json = CaptureUiEventTest.sampleJson("https://api.example.com/raw", 200, null);
        CaptureUiEvent event = CaptureUiEvent.fromJson(json);
        File exportDir = temporaryFolder.newFolder("rapid-share");

        File first = RawJsonFileExporter.write(exportDir, event);
        File second = RawJsonFileExporter.write(exportDir, event);

        assertNotEquals(first.getName(), second.getName());
        assertTrue(first.exists());
        assertTrue(second.exists());
    }
}
