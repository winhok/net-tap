package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HarExportRequest {
    private final List<CaptureUiEvent> events;

    private HarExportRequest(List<CaptureUiEvent> events) {
        this.events = Collections.unmodifiableList(events);
    }

    public static HarExportRequest from(List<CaptureUiEvent> events) {
        if (events == null || events.isEmpty()) {
            return new HarExportRequest(Collections.emptyList());
        }
        return new HarExportRequest(new ArrayList<>(events));
    }

    public List<CaptureUiEvent> events() {
        return events;
    }
}
