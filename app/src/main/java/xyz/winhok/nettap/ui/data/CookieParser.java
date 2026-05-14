package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CookieParser {
    private CookieParser() {
    }

    public static List<CookieEntry> parse(CaptureUiEvent event) {
        if (event == null) {
            return Collections.emptyList();
        }
        ArrayList<CookieEntry> result = new ArrayList<>();
        parseRequestCookies(findHeader(event.getRequestHeaders(), "Cookie"), result);
        parseResponseCookies(findHeader(event.getResponseHeaders(), "Set-Cookie"), result);
        return Collections.unmodifiableList(result);
    }

    private static void parseRequestCookies(String header, List<CookieEntry> result) {
        if (header == null || header.trim().isEmpty()) {
            return;
        }
        for (String part : header.split(";")) {
            String[] pair = splitPair(part);
            if (pair[0].isEmpty()) {
                continue;
            }
            result.add(new CookieEntry(
                    pair[0],
                    pair[1],
                    CookieEntry.Source.REQUEST,
                    false,
                    false,
                    null
            ));
        }
    }

    private static void parseResponseCookies(String header, List<CookieEntry> result) {
        if (header == null || header.trim().isEmpty()) {
            return;
        }
        for (String line : header.split("\\r?\\n")) {
            String[] parts = line.split(";");
            if (parts.length == 0) {
                continue;
            }
            String[] pair = splitPair(parts[0]);
            if (pair[0].isEmpty()) {
                addRawResponseCookie(line, result);
                continue;
            }
            boolean secure = false;
            boolean httpOnly = false;
            String sameSite = null;
            for (int i = 1; i < parts.length; i++) {
                String attr = parts[i].trim();
                String lower = attr.toLowerCase(Locale.US);
                if ("secure".equals(lower)) {
                    secure = true;
                } else if ("httponly".equals(lower)) {
                    httpOnly = true;
                } else if (lower.startsWith("samesite=")) {
                    sameSite = attr.substring("SameSite=".length());
                }
            }
            result.add(new CookieEntry(
                    pair[0],
                    pair[1],
                    CookieEntry.Source.RESPONSE,
                    secure,
                    httpOnly,
                    sameSite
            ));
        }
    }

    private static String[] splitPair(String value) {
        String trimmed = value == null ? "" : value.trim();
        int equals = trimmed.indexOf('=');
        if (equals < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{
                trimmed.substring(0, equals).trim(),
                trimmed.substring(equals + 1).trim()
        };
    }

    private static void addRawResponseCookie(String line, List<CookieEntry> result) {
        String raw = line == null ? "" : line.trim();
        if (raw.isEmpty()) {
            return;
        }
        result.add(new CookieEntry(
                "raw",
                raw,
                CookieEntry.Source.RESPONSE,
                false,
                false,
                null
        ));
    }

    private static String findHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
