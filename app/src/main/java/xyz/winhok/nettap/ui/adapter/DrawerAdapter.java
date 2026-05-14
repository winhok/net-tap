package xyz.winhok.nettap.ui.adapter;

public final class DrawerAdapter {
    private final String[] labels = new String[]{"Capture", "Hooks", "URL Filter", "TLS Keylog", "About"};

    public int size() {
        return labels.length;
    }

    public String labelAt(int index) {
        return labels[index];
    }
}
