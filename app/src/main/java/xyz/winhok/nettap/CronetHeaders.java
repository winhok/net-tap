package xyz.winhok.nettap;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CronetHeaders {
    private CronetHeaders() {
    }

    public static LinkedHashMap<String, String> fromArray(String[] headers) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }

        for (int index = 0; index + 1 < headers.length; index += 2) {
            String name = headers[index];
            if (name == null) {
                continue;
            }
            result.put(name, headers[index + 1]);
        }
        return result;
    }

    /** Return the first value in {@code headers} whose key matches {@code name} ignoring case. */
    public static String valueIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
