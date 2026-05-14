package xyz.winhok.nettap.ui;

public final class UiTabs {
    private static final String[] CAPTURE_TABS = new String[]{"Sequence", "Domains"};
    private static final String[] DETAIL_TABS = new String[]{"General", "Request", "Response", "Cookies"};

    private UiTabs() {
    }

    public static String[] captureTabs() {
        return CAPTURE_TABS.clone();
    }

    public static String captureTitle(int index) {
        return CAPTURE_TABS[index];
    }

    public static int captureCount() {
        return CAPTURE_TABS.length;
    }

    public static String[] detailTabs() {
        return DETAIL_TABS.clone();
    }

    public static String detailTitle(int index) {
        return DETAIL_TABS[index];
    }

    public static int detailCount() {
        return DETAIL_TABS.length;
    }
}
