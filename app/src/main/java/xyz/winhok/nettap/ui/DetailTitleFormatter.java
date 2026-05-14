package xyz.winhok.nettap.ui;

import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class DetailTitleFormatter {
    private DetailTitleFormatter() {
    }

    public static String title(CaptureUiEvent event) {
        if (event == null) {
            return "";
        }
        return event.getMethod() + " " + event.getHostLabel() + event.getPath();
    }
}
