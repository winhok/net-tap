package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/**
 * Extracts header entries from an {@code io.grpc.Metadata} instance without
 * linking against the grpc API. Uses reflection against the
 * {@code namesAndValues} field layout shared by grpc-core 1.x.
 *
 * <p>Net-Tap 仅作为内部调试 / 学习抓包工具使用，JSONL 与 logcat 需要保留
 * authorization、cookie、x-api-key 等原始请求头的真实值以便分析请求，
 * 因此本类不做任何脱敏处理（与 OkHttp / Cronet / HttpURLConnection 三条
 * 路径保持一致）。请勿在生产环境或对外分发的场景中启用。
 */
final class GrpcMetadata {

    private GrpcMetadata() {
    }

    static LinkedHashMap<String, String> extractHeaders(Object metadata) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (metadata == null) {
            return result;
        }

        Object[] namesAndValues = findNamesAndValues(metadata);
        if (namesAndValues == null) {
            return result;
        }

        try {
            for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
                Object nameObj = namesAndValues[i];
                Object valueObj = namesAndValues[i + 1];
                String name = asString(nameObj);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String value;
                if (name.endsWith("-bin")) {
                    byte[] bytes = asBytes(valueObj);
                    value = bytes == null ? "" : "[bin " + bytes.length + "B]";
                } else {
                    value = asString(valueObj);
                }
                if (value == null) {
                    value = "";
                }
                result.put(name, value);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static Object[] findNamesAndValues(Object metadata) {
        Class<?> c = metadata.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("namesAndValues");
                f.setAccessible(true);
                Object v = f.get(metadata);
                if (v instanceof Object[]) {
                    return (Object[]) v;
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof byte[]) {
            byte[] bytes = (byte[]) o;
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return o.toString();
    }

    private static byte[] asBytes(Object o) {
        if (o instanceof byte[]) {
            return (byte[]) o;
        }
        if (o instanceof String) {
            return ((String) o).getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }
}
