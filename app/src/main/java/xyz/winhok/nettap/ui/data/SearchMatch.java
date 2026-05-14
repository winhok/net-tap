package xyz.winhok.nettap.ui.data;

public final class SearchMatch {
    private final int start;
    private final int end;

    SearchMatch(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
