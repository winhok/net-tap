package xyz.winhok.nettap.ui.fragment;

import androidx.fragment.app.Fragment;

public final class DetailHostFragment {
    private static String selectedEventId;

    private DetailHostFragment() {
    }

    public static void setSelectedEventId(String eventId) {
        selectedEventId = eventId;
    }

    public static String selectedEventId() {
        return selectedEventId;
    }

    static Fragment[] detailTabs() {
        return new Fragment[]{
                new DetailGeneralFragment(),
                new DetailRequestFragment(),
                new DetailResponseFragment(),
                new DetailCookiesFragment()
        };
    }
}
