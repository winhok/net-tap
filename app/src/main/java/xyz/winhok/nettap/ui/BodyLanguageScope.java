package xyz.winhok.nettap.ui;

public final class BodyLanguageScope {
    private BodyLanguageScope() {
    }

    public static String textMateScope(String language) {
        if ("json".equals(language)) {
            return "source.json";
        }
        return null;
    }
}
