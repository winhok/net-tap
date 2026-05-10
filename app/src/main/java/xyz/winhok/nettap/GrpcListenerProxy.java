package xyz.winhok.nettap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * Wraps a grpc {@code ClientCall.Listener} so we can observe
 * {@code onHeaders}/{@code onMessage}/{@code onClose}/{@code onReady} callbacks
 * without requiring a compile-time dep on grpc-core. The wrapped listener
 * delegates to the original in all cases; side effects that throw are
 * swallowed so the host path always completes.
 */
final class GrpcListenerProxy {

    private GrpcListenerProxy() {
    }

    static Object wrap(Object original, Class<?> listenerClass, GrpcCaptureState state) {
        if (original == null || listenerClass == null || state == null) {
            return original;
        }
        try {
            InvocationHandler existing = null;
            try {
                existing = Proxy.getInvocationHandler(original);
            } catch (IllegalArgumentException ignored) {
            }
            if (existing instanceof Handler) {
                return original;
            }

            ClassLoader cl = listenerClass.getClassLoader();
            if (cl == null) {
                cl = original.getClass().getClassLoader();
            }
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            return Proxy.newProxyInstance(
                    cl,
                    new Class[]{listenerClass},
                    new Handler(original, state));
        } catch (Throwable e) {
            NetTap.getXposedLogger().log("grpc listener proxy wrap failed: %s", e);
            return original;
        }
    }

    private static final class Handler implements InvocationHandler {
        private final Object original;
        private final GrpcCaptureState state;

        Handler(Object original, GrpcCaptureState state) {
            this.original = original;
            this.state = state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if ("onHeaders".equals(name) && args != null && args.length >= 1) {
                    synchronized (state.mutex) {
                        state.responseHeaders.putAll(GrpcMetadata.extractHeaders(args[0]));
                    }
                } else if ("onMessage".equals(name) && args != null && args.length >= 1) {
                    captureResponseMessage(args[0]);
                } else if ("onClose".equals(name) && args != null && args.length >= 2) {
                    captureClose(args[0]);
                    synchronized (state.mutex) {
                        state.responseHeaders.putAll(GrpcMetadata.extractHeaders(args[1]));
                    }
                    GrpcCallInstaller.recordFinal(state);
                }
            } catch (Throwable t) {
                NetTap.getXposedLogger().log("grpc listener hook side-effect failed: %s", t);
            }

            try {
                return method.invoke(original, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    throw cause;
                }
                throw e;
            }
        }

        private void captureResponseMessage(Object message) {
            if (message == null) {
                return;
            }
            synchronized (state.mutex) {
                if (state.responseBodyFirstMessage != null) {
                    return;
                }
            }
            byte[] bytes = tryToByteArray(message);
            if (bytes == null) {
                return;
            }
            int max = CaptureConfig.MAX_BODY_BYTES;
            boolean truncated = bytes.length > max;
            byte[] stored = truncated
                    ? java.util.Arrays.copyOf(bytes, max)
                    : bytes;
            synchronized (state.mutex) {
                if (state.responseBodyFirstMessage == null) {
                    state.responseBodyFirstMessage = stored;
                    state.responseBodyObserved = bytes.length;
                    state.responseBodyTruncated = truncated;
                }
            }
        }

        private void captureClose(Object status) {
            if (status == null) {
                return;
            }
            int code = -1;
            try {
                Object codeObj = Reflect.invokeNoArg(status, "getCode");
                if (codeObj != null) {
                    try {
                        Object v = Reflect.invokeNoArg(codeObj, "value");
                        if (v instanceof Number) {
                            code = ((Number) v).intValue();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            String description = null;
            try {
                Object d = Reflect.invokeNoArg(status, "getDescription");
                description = d == null ? null : d.toString();
            } catch (Throwable ignored) {
            }
            synchronized (state.mutex) {
                state.statusCode = code;
                state.statusMessage = description;
            }
        }

        private static byte[] tryToByteArray(Object message) {
            try {
                Object r = Reflect.invokeNoArg(message, "toByteArray");
                if (r instanceof byte[]) {
                    return (byte[]) r;
                }
            } catch (Throwable ignored) {
            }
            try {
                return message.toString().getBytes(StandardCharsets.UTF_8);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
