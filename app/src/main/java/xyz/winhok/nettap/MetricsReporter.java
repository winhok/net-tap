package xyz.winhok.nettap;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-package hook-layer metrics. Lets the operator tell at a glance which
 * hooks installed in a given host and how much traffic each layer observed.
 */
public final class MetricsReporter {

    public static final String LAYER_OKHTTP_BUILDER = "OKHTTP_BUILDER";
    public static final String LAYER_OKHTTP_REALCALL = "OKHTTP_REALCALL";
    public static final String LAYER_OKHTTP_CHAIN = "OKHTTP_CHAIN";
    public static final String LAYER_OKHTTP_EXCHANGE = "OKHTTP_EXCHANGE";
    public static final String LAYER_CRONET = "CRONET";
    public static final String LAYER_CRONET_BIDI = "CRONET_BIDI";
    public static final String LAYER_GRPC = "GRPC";
    public static final String LAYER_HURL = "HURL";

    private static final ConcurrentHashMap<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    private MetricsReporter() {
    }

    public static void incInstalled(String packageName, String layer) {
        inc(key(packageName, layer, "installed"));
    }

    public static void incCaptured(String packageName, String layer) {
        inc(key(packageName, layer, "captured"));
    }

    public static long get(String packageName, String layer, String kind) {
        AtomicLong c = COUNTERS.get(key(packageName, layer, kind));
        return c == null ? 0L : c.get();
    }

    public static String snapshot(String packageName) {
        String prefix = packageName + "::";
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> e : COUNTERS.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                sorted.put(e.getKey().substring(prefix.length()), e.getValue().get());
            }
        }
        if (sorted.isEmpty()) {
            return "no metrics recorded for " + packageName;
        }
        StringBuilder sb = new StringBuilder("metrics[").append(packageName).append("]");
        for (Map.Entry<String, Long> e : sorted.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static void resetForTesting() {
        COUNTERS.clear();
    }

    private static void inc(String k) {
        AtomicLong c = COUNTERS.get(k);
        if (c == null) {
            c = COUNTERS.computeIfAbsent(k, key -> new AtomicLong());
        }
        c.incrementAndGet();
    }

    private static String key(String packageName, String layer, String kind) {
        if (packageName == null) {
            packageName = "?";
        }
        if (layer == null) {
            layer = "?";
        }
        return packageName + "::" + layer + "::" + kind;
    }
}
