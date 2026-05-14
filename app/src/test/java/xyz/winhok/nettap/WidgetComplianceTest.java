package xyz.winhok.nettap;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public final class WidgetComplianceTest {
    private static final String[] WIDGETS = {
            "JsonHighlightView.java",
            "SpacedRecyclerView.java",
            "FadeSpinnerView.java",
            "OrbitView.java"
    };

    @Test
    public void widgetFilesStartWithComplianceHeader() throws Exception {
        for (String widget : WIDGETS) {
            String source = readWidget(widget);

            assertTrue(widget, source.startsWith("// Copyright (c) 2026 winhok\n"));
            assertTrue(widget, source.contains("// No third-party source code is reproduced verbatim in this file."));
        }
    }

    @Test
    public void widgetFilesUseNetTapPackageOnly() throws Exception {
        for (String widget : WIDGETS) {
            String source = readWidget(widget);

            assertTrue(widget, source.contains("package xyz.winhok.nettap.ui.widget;"));
        }
    }

    private static String readWidget(String fileName) throws Exception {
        Path path = Paths.get("src/main/java/xyz/winhok/nettap/ui/widget", fileName);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
