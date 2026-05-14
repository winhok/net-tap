package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaptureNdjsonParser {
    static final int MAX_LINE_CHARS = 2 * 1024 * 1024;

    private CaptureNdjsonParser() {
    }

    public static List<CaptureUiEvent> parseLines(String ndjson) {
        if (ndjson == null || ndjson.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<CaptureUiEvent> result = new ArrayList<>();
        String[] lines = ndjson.split("\\r?\\n");
        for (String line : lines) {
            CaptureUiEvent event = parseLineOrNull(line);
            if (event != null) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static CaptureUiEvent parseLineOrNull(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        if (line.length() > MAX_LINE_CHARS) {
            return null;
        }
        try {
            return CaptureUiEvent.fromJson(line.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
