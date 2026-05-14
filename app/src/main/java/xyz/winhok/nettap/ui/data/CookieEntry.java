package xyz.winhok.nettap.ui.data;

public final class CookieEntry {
    public enum Source {
        REQUEST,
        RESPONSE
    }

    private final String name;
    private final String value;
    private final Source source;
    private final boolean secure;
    private final boolean httpOnly;
    private final String sameSite;

    CookieEntry(
            String name,
            String value,
            Source source,
            boolean secure,
            boolean httpOnly,
            String sameSite
    ) {
        this.name = name == null ? "" : name;
        this.value = value == null ? "" : value;
        this.source = source;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.sameSite = sameSite;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Source getSource() {
        return source;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public String getSameSite() {
        return sameSite;
    }
}
