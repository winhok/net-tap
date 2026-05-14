package xyz.winhok.nettap.ui.data;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QueryParamParser {
    private QueryParamParser() {
    }

    public static List<QueryParam> parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<QueryParam> params = new ArrayList<>();
        String[] pairs = rawQuery.split("&", -1);
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                params.add(new QueryParam(decode(pair), ""));
            } else {
                params.add(new QueryParam(
                        decode(pair.substring(0, equals)),
                        decode(pair.substring(equals + 1))
                ));
            }
        }
        return Collections.unmodifiableList(params);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return value;
        }
    }
}
