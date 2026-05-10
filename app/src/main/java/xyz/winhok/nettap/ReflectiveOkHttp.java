package xyz.winhok.nettap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

// FIXME(follow-up): consolidate returnTypesEquivalent with Reflect.typesEquivalent in a future cleanup step.
public final class ReflectiveOkHttp {
    private static final String REQUEST_BODY_UNAVAILABLE = "request body unavailable";
    private static final String RESPONSE_BODY_UNAVAILABLE = "peekBody unavailable";

    private ReflectiveOkHttp() {
    }

    public static String method(Object request) {
        try {
            return stringValue(methodOrFieldValue(request, "method"));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String url(Object request) {
        try {
            return stringValue(methodOrFieldValue(request, "url"));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static LinkedHashMap<String, String> headers(Object headersOrOwner) {
        try {
            return headersInternal(headersOrOwner);
        } catch (Throwable ignored) {
            return new LinkedHashMap<>();
        }
    }

    public static Object requestFromResponse(Object response) {
        try {
            return methodOrFieldValue(response, "request");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int responseCode(Object response) {
        try {
            return intValue(methodOrFieldValue(response, "code"), 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static String responseMessage(Object response) {
        try {
            return stringValue(methodOrFieldValue(response, "message"));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static CaptureBody requestBody(Object request) {
        try {
            return requestBodyInternal(request);
        } catch (Throwable ignored) {
            return CaptureBody.omitted(null, -1L, null, "request body capture failed");
        }
    }

    public static CaptureBody responseBody(Object response) {
        try {
            return responseBodyInternal(response);
        } catch (Throwable ignored) {
            return CaptureBody.omitted(null, -1L, null, "response body capture failed");
        }
    }

    private static LinkedHashMap<String, String> headersInternal(Object headersOrOwner)
            throws ReflectiveOperationException {
        LinkedHashMap<String, String> directHeaders = collectHeaders(headersOrOwner);
        if (!directHeaders.isEmpty() || looksLikeHeaders(headersOrOwner)) {
            return directHeaders;
        }

        Object headers = methodOrFieldValue(headersOrOwner, "headers");
        if (headers == headersOrOwner) {
            return new LinkedHashMap<>();
        }
        return collectHeaders(headers);
    }

    private static CaptureBody requestBodyInternal(Object request) {
        if (request == null) {
            return CaptureBody.omitted(null, -1L, null, REQUEST_BODY_UNAVAILABLE);
        }

        Object body = safeMethodOrFieldValue(request, "body");
        if (body == null) {
            return CaptureBody.omitted(null, -1L, contentEncoding(request), REQUEST_BODY_UNAVAILABLE);
        }

        String contentType = contentType(body);
        long contentLength = contentLength(body);
        String encoding = contentEncoding(request);

        if (safeBoolean(body, "isOneShot")) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "one-shot request body omitted");
        }
        if (safeBoolean(body, "isDuplex")) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "duplex request body omitted");
        }
        BodyCapturePolicy.Decision requestDecision = BodyCapturePolicy.classify(contentType);
        switch (requestDecision) {
            case BINARY:
                return CaptureBody.omitted(
                        contentType, contentLength, encoding, "binary request body omitted");
            case UNKNOWN:
                return CaptureBody.omitted(
                        contentType,
                        contentLength,
                        encoding,
                        "unknown request body content type omitted"
                );
            case TEXTUAL:
            default:
                break;
        }
        if (contentLength < 0L) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "unknown request body length omitted");
        }
        if (contentLength > CaptureConfig.MAX_BODY_BYTES) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "request body too large");
        }

        byte[] bytes;
        try {
            bytes = requestBodyBytes(request, body);
        } catch (Throwable ignored) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "request body capture failed");
        }
        if (bytes == null) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "request body capture failed");
        }

        try {
            DecodedBody decoded = decodeBodyBytes(bytes, encoding, CaptureConfig.MAX_BODY_BYTES);
            return CaptureBody.text(
                    contentType,
                    contentLength,
                    encoding,
                    decoded.truncated,
                    decoded.text
            );
        } catch (Throwable ignored) {
            return CaptureBody.omitted(contentType, contentLength, encoding, "request body decode failed");
        }
    }

    // omittedReason text is part of the consumer-facing JSONL contract.
    // Behavior change in Step 3: response with null/missing Content-Type now
    // reports "unknown response body content type omitted" (previously was
    // lumped into "binary response body omitted").
    private static CaptureBody responseBodyInternal(Object response) {
        if (response == null) {
            return CaptureBody.omitted(null, -1L, null, RESPONSE_BODY_UNAVAILABLE);
        }

        String encoding = contentEncoding(response);
        Object originalBody = safeMethodOrFieldValue(response, "body");
        if (originalBody == null) {
            return CaptureBody.omitted(null, -1L, encoding, "response body unavailable");
        }

        String originalContentType = contentType(originalBody);
        long originalContentLength = contentLength(originalBody);
        BodyCapturePolicy.Decision responseDecision = BodyCapturePolicy.classify(originalContentType);
        switch (responseDecision) {
            case BINARY:
                return CaptureBody.omitted(
                        originalContentType,
                        originalContentLength,
                        encoding,
                        "binary response body omitted"
                );
            case UNKNOWN:
                return CaptureBody.omitted(
                        originalContentType,
                        originalContentLength,
                        encoding,
                        "unknown response body content type omitted"
                );
            case TEXTUAL:
            default:
                break;
        }

        // Primary path: ask the response to produce a peeked copy via name-based
        // reflection (works on un-obfuscated OkHttp).
        Object copiedBody;
        try {
            copiedBody = invoke(response, "peekBody", CaptureConfig.MAX_BODY_BYTES + 1L);
        } catch (Throwable peekBodyFailure) {
            // Fallback path: probe the body through PeekBodyReader. This hits when
            // peekBody is removed/renamed by R8 shrinking but body() and source()
            // are kept (a common minify outcome since app code rarely calls peekBody).
            // Note: PeekBodyReader resolves read(Buffer, long) by signature, but
            // source()/peek() are still looked up by name (with field fallback),
            // so this fallback fails when okio itself is fully renamed.
            return responseBodyViaPeekBytesFallback(
                    originalBody, originalContentType, originalContentLength, encoding);
        }

        if (copiedBody == null) {
            return CaptureBody.omitted(originalContentType, originalContentLength, encoding, RESPONSE_BODY_UNAVAILABLE);
        }

        String contentType = contentType(copiedBody);
        if (contentType == null) {
            contentType = originalContentType;
        }

        try {
            DecodedBody decoded = decodeBodyBytes(
                    (byte[]) invoke(copiedBody, "bytes"),
                    encoding,
                    CaptureConfig.MAX_BODY_BYTES
            );
            return CaptureBody.text(
                    contentType,
                    originalContentLength,
                    encoding,
                    decoded.truncated,
                    decoded.text
            );
        } catch (Throwable ignored) {
            return CaptureBody.omitted(contentType, originalContentLength, encoding, RESPONSE_BODY_UNAVAILABLE);
        }
    }

    /**
     * Last-resort body capture used when the primary {@code peekBody} reflection
     * failed. Effective when R8 minify removed peekBody but kept body() and the
     * underlying okio Source surface; ineffective when okio itself is fully
     * renamed (in which case PeekBodyReader.peekBytes returns null and we
     * gracefully fall back to omitted).
     *
     * <p>The gzip path still works here because {@code encoding} comes from the
     * response Content-Encoding header (not from the peeked body), and
     * {@link #decodeBodyBytes} handles raw gzip bytes regardless of how they
     * were sourced.
     */
    private static CaptureBody responseBodyViaPeekBytesFallback(
            Object originalBody,
            String originalContentType,
            long originalContentLength,
            String encoding) {
        if (originalBody == null) {
            return CaptureBody.omitted(
                    originalContentType, originalContentLength, encoding, RESPONSE_BODY_UNAVAILABLE);
        }
        PeekBodyReader.ProbeResult probe;
        try {
            probe = PeekBodyReader.peekBytes(originalBody, CaptureConfig.MAX_BODY_BYTES);
        } catch (Throwable ignored) {
            probe = null;
        }
        if (probe == null || probe.getBytes() == null) {
            return CaptureBody.omitted(
                    originalContentType, originalContentLength, encoding, RESPONSE_BODY_UNAVAILABLE);
        }
        try {
            DecodedBody decoded = decodeBodyBytes(probe.getBytes(), encoding, CaptureConfig.MAX_BODY_BYTES);
            boolean truncated = probe.isTruncated() || decoded.truncated;
            return CaptureBody.text(
                    originalContentType,
                    originalContentLength,
                    encoding,
                    truncated,
                    decoded.text
            );
        } catch (Throwable ignored) {
            return CaptureBody.omitted(
                    originalContentType, originalContentLength, encoding, RESPONSE_BODY_UNAVAILABLE);
        }
    }

    private static byte[] requestBodyBytes(Object request, Object body) throws ReflectiveOperationException {
        ClassLoader classLoader = request.getClass().getClassLoader();
        if (classLoader == null) {
            classLoader = body.getClass().getClassLoader();
        }
        if (classLoader == null) {
            throw new ClassNotFoundException("okio.Buffer");
        }

        Class<?> bufferClass = classLoader.loadClass("okio.Buffer");
        Object buffer = bufferClass.getDeclaredConstructor().newInstance();
        invoke(body, "writeTo", buffer);
        return (byte[]) invoke(buffer, "readByteArray");
    }

    private static DecodedBody decodeBodyBytes(byte[] bytes, String encoding, int maxBytes)
            throws java.io.IOException {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        if (isGzipEncoding(encoding)) {
            GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(safeBytes));
            try {
                return readBoundedUtf8(gzip, maxBytes);
            } finally {
                gzip.close();
            }
        }

        int length = Math.min(safeBytes.length, maxBytes);
        return new DecodedBody(
                new String(safeBytes, 0, length, StandardCharsets.UTF_8),
                safeBytes.length > maxBytes
        );
    }

    private static DecodedBody readBoundedUtf8(java.io.InputStream inputStream, int maxBytes)
            throws java.io.IOException {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        boolean truncated = false;
        while ((read = inputStream.read(buffer)) != -1) {
            int remaining = maxBytes - outputStream.size();
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            if (read > remaining) {
                outputStream.write(buffer, 0, remaining);
                truncated = true;
                break;
            }
            outputStream.write(buffer, 0, read);
        }
        return new DecodedBody(new String(outputStream.toByteArray(), StandardCharsets.UTF_8), truncated);
    }

    private static boolean isGzipEncoding(String encoding) {
        return encoding != null && encoding.toLowerCase(java.util.Locale.US).contains("gzip");
    }

    private static final class DecodedBody {
        private final String text;
        private final boolean truncated;

        private DecodedBody(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    private static LinkedHashMap<String, String> collectHeaders(Object headers) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }

        Integer size = safeInteger(headers, "size");
        if (size != null) {
            try {
                for (int index = 0; index < size; index++) {
                    Object name = invoke(headers, "name", index);
                    Object value = invoke(headers, "value", index);
                    if (name != null) {
                        appendHeader(result, String.valueOf(name), value == null ? null : String.valueOf(value));
                    }
                }
                return result;
            } catch (Throwable ignored) {
                result.clear();
            }
        }

        try {
            Object namesAndValues = fieldValue(headers, "namesAndValues");
            if (namesAndValues instanceof String[]) {
                String[] items = (String[]) namesAndValues;
                for (int index = 0; index + 1 < items.length; index += 2) {
                    if (items[index] != null) {
                        appendHeader(result, items[index], items[index + 1]);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static void appendHeader(LinkedHashMap<String, String> headers, String name, String value) {
        if (!headers.containsKey(name)) {
            headers.put(name, value);
            return;
        }

        String existing = headers.get(name);
        String safeExisting = existing == null ? "" : existing;
        String safeValue = value == null ? "" : value;
        headers.put(name, safeExisting + "\n" + safeValue);
    }

    private static boolean looksLikeHeaders(Object value) {
        if (value == null) {
            return false;
        }
        return hasCompatibleMethod(value.getClass(), "size")
                && hasCompatibleMethod(value.getClass(), "name", Integer.valueOf(0))
                && hasCompatibleMethod(value.getClass(), "value", Integer.valueOf(0));
    }

    private static String contentEncoding(Object owner) {
        try {
            for (Map.Entry<String, String> entry : headers(owner).entrySet()) {
                if ("content-encoding".equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String contentType(Object body) {
        try {
            return stringValue(methodOrFieldValue(body, "contentType"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long contentLength(Object body) {
        try {
            return longValue(methodOrFieldValue(body, "contentLength"), -1L);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String truncateUtf8Bytes(String text, int maxBytes) {
        if (text == null) {
            return null;
        }

        if (utf8Length(text) <= maxBytes) {
            return text;
        }

        int offset = 0;
        int bytes = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = utf8Length(codePoint, text.charAt(offset));
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            offset += charCount;
        }
        return text.substring(0, offset);
    }

    private static long utf8Length(String text) {
        if (text == null) {
            return 0L;
        }
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int utf8Length(int codePoint, char firstChar) {
        if (Character.isSurrogate(firstChar) && Character.charCount(codePoint) == 1) {
            return 1;
        }
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private static Object safeMethodOrFieldValue(Object target, String name) {
        try {
            return methodOrFieldValue(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object methodOrFieldValue(Object target, String name)
            throws ReflectiveOperationException {
        try {
            return invoke(target, name);
        } catch (ReflectiveOperationException invokeFailure) {
            try {
                return fieldValue(target, name);
            } catch (ReflectiveOperationException fieldFailure) {
                Method fallback = obfuscationFallbackMethod(target, name);
                if (fallback != null) {
                    return Reflect.invoke(target, fallback);
                }
                fieldFailure.addSuppressed(invokeFailure);
                throw fieldFailure;
            }
        }
    }

    /**
     * Best-effort signature-based lookup for known no-arg getters when both the
     * direct name-based method lookup and same-named field lookup fail. This
     * exists to support R8/ProGuard-renamed OkHttp surfaces where method names
     * are mangled but signatures (return + parameter types) remain intact.
     *
     * <p>Only enabled for getters whose return type is sufficiently distinctive
     * to avoid colliding with unrelated members on the same class:
     * <ul>
     *   <li>{@code code}    : no-arg returning {@code int}</li>
     *   <li>{@code message} : no-arg returning {@code String}</li>
     *   <li>{@code url}     : no-arg returning {@code String}</li>
     *   <li>{@code method}  : no-arg returning {@code String}</li>
     * </ul>
     *
     * <p>Not enabled for {@code request}, {@code headers}, {@code body}: their
     * return types ({@code Request}/{@code Headers}/{@code RequestBody} or the
     * response equivalents) are themselves likely obfuscated and a pure
     * return-type match would too easily collide. Body discovery is instead
     * handled by the higher-level body-capture pipeline (see
     * {@link PeekBodyReader#peekBytes}).
     *
     * <p>Walks declared methods up the chain but stops at {@link Object}, so
     * inherited members like {@code hashCode()} or {@code toString()} are not
     * accidentally picked. Static methods are also skipped.
     *
     * <p>Aligned conceptually with {@link Reflect#findMethodBySignature}, but
     * intentionally walks {@code getDeclaredMethods()} directly to avoid
     * surfacing inherited {@code Object} methods (e.g. {@code hashCode()},
     * {@code toString()}) that would otherwise leak through
     * {@link Class#getMethods()}. Note that {@code getDeclaredMethods()}
     * iteration order is unspecified by the JVM; multi-match cases are
     * tied deterministically by method name in
     * {@link #findNoArgMethodByReturnType}.
     */
    private static Method obfuscationFallbackMethod(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }
        Class<?> expectedReturn;
        switch (name) {
            case "code":
                expectedReturn = int.class;
                break;
            case "message":
            case "url":
            case "method":
                expectedReturn = String.class;
                break;
            default:
                return null;
        }
        return findNoArgMethodByReturnType(target.getClass(), expectedReturn);
    }

    private static Method findNoArgMethodByReturnType(Class<?> declaringClass, Class<?> returnType) {
        if (declaringClass == null || returnType == null) {
            return null;
        }
        java.util.List<Method> matches = new java.util.ArrayList<>();
        Class<?> current = declaringClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                if (returnTypesEquivalent(returnType, method.getReturnType())) {
                    matches.add(method);
                }
            }
            current = current.getSuperclass();
        }
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(java.util.Comparator.comparing(Method::getName));
        Method picked = matches.get(0);
        picked.setAccessible(true);
        return picked;
    }

    private static boolean returnTypesEquivalent(Class<?> expected, Class<?> actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.isPrimitive()) {
            return primitiveWrapper(expected).equals(actual);
        }
        if (actual.isPrimitive()) {
            return primitiveWrapper(actual).equals(expected);
        }
        return false;
    }

    private static boolean safeBoolean(Object target, String name) {
        try {
            Object value = methodOrFieldValue(target, name);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Integer safeInteger(Object target, String name) {
        try {
            return Integer.valueOf(intValue(methodOrFieldValue(target, name), -1));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchMethodException(name);
        }

        Method method = findCompatiblePublicMethod(target.getClass(), name, args);
        if (method == null) {
            method = findCompatibleDeclaredMethod(target.getClass(), name, args);
        }
        if (method == null) {
            throw new NoSuchMethodException(name);
        }

        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static Object fieldValue(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchFieldException(name);
        }

        Field field;
        try {
            field = target.getClass().getField(name);
        } catch (NoSuchFieldException ignored) {
            field = findDeclaredField(target.getClass(), name);
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }

        field.setAccessible(true);
        return field.get(target);
    }

    private static Method findCompatiblePublicMethod(Class<?> type, String name, Object... args) {
        for (Method method : type.getMethods()) {
            if (isCompatibleMethod(method, name, args)) {
                return method;
            }
        }
        return null;
    }

    private static Method findCompatibleDeclaredMethod(Class<?> type, String name, Object... args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (isCompatibleMethod(method, name, args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findDeclaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean hasCompatibleMethod(Class<?> type, String name, Object... args) {
        return findCompatiblePublicMethod(type, name, args) != null
                || findCompatibleDeclaredMethod(type, name, args) != null;
    }

    private static boolean isCompatibleMethod(Method method, String name, Object... args) {
        if (!method.getName().equals(name)) {
            return false;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != args.length) {
            return false;
        }

        for (int index = 0; index < parameterTypes.length; index++) {
            if (!isCompatibleParameter(parameterTypes[index], args[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompatibleParameter(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }

        Class<?> argType = arg.getClass();
        if (parameterType.isPrimitive()) {
            return primitiveWrapper(parameterType).isAssignableFrom(argType);
        }
        return parameterType.isAssignableFrom(argType);
    }

    private static Class<?> primitiveWrapper(Class<?> primitiveType) {
        if (primitiveType == boolean.class) {
            return Boolean.class;
        }
        if (primitiveType == byte.class) {
            return Byte.class;
        }
        if (primitiveType == char.class) {
            return Character.class;
        }
        if (primitiveType == short.class) {
            return Short.class;
        }
        if (primitiveType == int.class) {
            return Integer.class;
        }
        if (primitiveType == long.class) {
            return Long.class;
        }
        if (primitiveType == float.class) {
            return Float.class;
        }
        if (primitiveType == double.class) {
            return Double.class;
        }
        return Void.class;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static long longValue(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
