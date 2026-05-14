package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SearchEngine {
    private SearchEngine() {
    }

    public static List<CaptureUiEvent> filter(List<CaptureUiEvent> events, String query) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
        ArrayList<CaptureUiEvent> result = new ArrayList<>();
        for (CaptureUiEvent event : events) {
            String haystack = event.searchableText();
            boolean matches = true;
            for (String token : tokens) {
                if (!haystack.contains(token)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<String> tokens(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] raw = query.toLowerCase(Locale.US).trim().split("\\s+");
        ArrayList<String> result = new ArrayList<>();
        for (String token : raw) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static List<SearchMatch> matchRanges(String text, String query) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        String lowerText = text.toLowerCase(Locale.US);
        ArrayList<SearchMatch> result = new ArrayList<>();
        for (String token : tokens) {
            int start = 0;
            while (start < lowerText.length()) {
                int found = lowerText.indexOf(token, start);
                if (found < 0) {
                    break;
                }
                result.add(new SearchMatch(found, found + token.length()));
                start = found + token.length();
            }
        }
        result.sort((left, right) -> {
            if (left.getStart() != right.getStart()) {
                return Integer.compare(left.getStart(), right.getStart());
            }
            return Integer.compare(left.getEnd(), right.getEnd());
        });
        return Collections.unmodifiableList(result);
    }
}
