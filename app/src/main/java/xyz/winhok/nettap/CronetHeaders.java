package xyz.winhok.nettap;

import java.util.LinkedHashMap;

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
}
