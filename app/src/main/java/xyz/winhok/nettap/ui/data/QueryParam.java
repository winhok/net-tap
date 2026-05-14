package xyz.winhok.nettap.ui.data;

public final class QueryParam {
    private final String name;
    private final String value;

    QueryParam(String name, String value) {
        this.name = name == null ? "" : name;
        this.value = value == null ? "" : value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
