package xyz.winhok.nettap;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves all known {@code CronetUrlRequest} class variants reachable from a
 * given {@link ClassLoader}. Some apps repackage Chromium Cronet under their
 * own namespace (e.g. ByteDance TTNet at {@code com.ttnet.org.chromium...});
 * iterating over a small prefix list keeps the hook target-agnostic.
 *
 * <p>{@link #resolveAll} returns every variant found, in {@link #PREFIXES}
 * order, so callers can install hooks against multiple coexisting Cronet
 * stacks within a single app.
 */
public final class CronetCandidates {

    /**
     * Known prefixes for repackaged Cronet implementations. Iterated in order;
     * the first prefix that successfully resolves the suffix on the given
     * {@link ClassLoader} wins (but ALL successful resolutions are returned to
     * support coexistence in one app).
     */
    static final String[] PREFIXES = { "", "com.ttnet." };

    /** Suffix shared by all variants. */
    static final String CRONET_URL_REQUEST_SUFFIX = "org.chromium.net.impl.CronetUrlRequest";

    public static final String CRONET_URL_REQUEST_CONTEXT_SUFFIX =
            "org.chromium.net.impl.CronetUrlRequestContext";
    public static final String CRONET_BIDIRECTIONAL_STREAM_SUFFIX =
            "org.chromium.net.impl.CronetBidirectionalStream";
    public static final String URL_REQUEST_CALLBACK_SUFFIX =
            "org.chromium.net.UrlRequest$Callback";
    public static final String CRONET_UPLOAD_DATA_STREAM_SUFFIX =
            "org.chromium.net.impl.CronetUploadDataStream";

    private CronetCandidates() {
    }

    /**
     * Resolve all CronetUrlRequest classes findable via {@link #PREFIXES} on
     * the given ClassLoader. Returns an empty list if {@code classLoader} is
     * {@code null} or none resolve.
     */
    public static List<Class<?>> resolveAll(ClassLoader classLoader) {
        return resolveAllWithSuffix(classLoader, CRONET_URL_REQUEST_SUFFIX);
    }

    /**
     * Resolve all classes whose FQN is {@code prefix + suffix} for any known
     * Cronet shade prefix. Empty list if nothing resolves or classLoader is
     * null.
     */
    public static List<Class<?>> resolveAllWithSuffix(ClassLoader classLoader, String suffix) {
        List<Class<?>> resolved = new ArrayList<>();
        if (classLoader == null || suffix == null || suffix.isEmpty()) {
            return resolved;
        }
        for (int i = 0; i < PREFIXES.length; i++) {
            String fqcn = PREFIXES[i] + suffix;
            try {
                Class<?> cls = classLoader.loadClass(fqcn);
                if (cls != null && !resolved.contains(cls)) {
                    resolved.add(cls);
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return resolved;
    }
}
