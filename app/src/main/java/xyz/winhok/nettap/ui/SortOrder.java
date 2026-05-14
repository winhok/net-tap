package xyz.winhok.nettap.ui;

import xyz.winhok.nettap.R;

public enum SortOrder {
    NEWEST_FIRST("newest_first", R.string.sort_newest_first),
    OLDEST_FIRST("oldest_first", R.string.sort_oldest_first);

    private final String preference;
    private final int labelResId;

    SortOrder(String preference, int labelResId) {
        this.preference = preference;
        this.labelResId = labelResId;
    }

    public static SortOrder fromPreference(String value) {
        for (SortOrder order : values()) {
            if (order.preference.equals(value)) {
                return order;
            }
        }
        return NEWEST_FIRST;
    }

    public static SortOrder fromMenuId(int menuId) {
        SortOrder[] orders = values();
        if (menuId < 0 || menuId >= orders.length) {
            return NEWEST_FIRST;
        }
        return orders[menuId];
    }

    public SortOrder toggle() {
        return this == NEWEST_FIRST ? OLDEST_FIRST : NEWEST_FIRST;
    }

    public boolean isNewestFirst() {
        return this == NEWEST_FIRST;
    }

    public String toPreference() {
        return preference;
    }

    public int labelResId() {
        return labelResId;
    }
}
