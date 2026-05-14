# Net-Tap — Universal HTTP Capture for Android (Xposed)

An Xposed / LSPosed module that captures HTTP(S) traffic from **any Android app** at the application layer first, then emits TLS key material for encrypted transports that cannot be fully decoded in-process. Net-Tap does **not** disable certificate validation or certificate pinning.

| Layer | What it covers |
|---|---|
| **OkHttp 3 / 4** | Stock `okhttp3.*` and apps where OkHttp has been R8-obfuscated or relocated — the installer falls back through four hook strategies until one sticks |
| **Cronet** | Stock `org.chromium.net.impl.CronetUrlRequest` in the canonical Chromium namespace; request + response body closure via `CronetUploadDataStream` and `CronetBidirectionalStream` |
| **gRPC-over-OkHttp** | Hooks `io.grpc.internal.ClientCallImpl.start` / `sendMessage` and wraps the listener with a dynamic proxy so response messages, trailers and status codes are captured alongside the request metadata |
| **`HttpURLConnection`** | Tees `getOutputStream()` / `getInputStream()` / `getErrorStream()` for Firebase, GMS, Facebook SDK, and legacy code paths |
| **Higher-level open clients** | Volley, Fuel, Apache HttpClient 5, Ktor CIO, and AndroidAsync where they bypass the core stacks above |
| **TLS key log** | Conscrypt/JSSE key log plus canonical Cronet/BoringSSL `ssl_key_log_file` injection |

It also tracks per-layer install and capture counts via `MetricsReporter` so you can diagnose "which layer didn't land" on any given host.

Net-Tap keeps vendor-private SDK layers out of the default capture surface. It does not install hooks for app-specific API clients, private Retrofit-style stacks, or vendor-prefixed Cronet packages.

## What it captures per request

* Method, URL, duration
* Request + response headers captured verbatim, **including `authorization` / `cookie` / `x-api-key`** — no redaction is performed. The capture file sits under the host app's private data dir; anyone with root can read it, so treat it as sensitive material.
* Request + response body up to `CaptureConfig.MAX_BODY_BYTES`, truncation flagged explicitly
* Response status code + message
* gRPC method name (`/package.Service/Method`), authority, status code, description

## Output

* JSON Lines at `/data/data/<pkg>/files/_okhttp_capture.jsonl` — one file per hooked app. Authoritative capture source (logcat drops chunks on large payloads, this file does not). When a record is written, bodies are captured up to `CaptureConfig.MAX_BODY_BYTES`; oversized or unsupported bodies (one-shot, duplex, opaque binary, unknown content type) are marked truncated or omitted with an explicit reason. Under sustained file-writer backpressure the oldest pending records may be dropped — watch for `filelogger backpressure: N lines dropped so far` in logcat.
* NSS TLS key log at `/data/data/<pkg>/files/_tls_keylog.log` when Conscrypt/JSSE/Cronet key logging surfaces are reachable. Use this with a pcap in Wireshark for apps that use custom CAs, certificate pinning, or QUIC without disabling their TLS checks.
* Chunked `CAPTURE_JSON` messages in logcat with the `NetTap` tag. Best-effort only — Android's per-uid logcat rate limiter silently drops chunks on large payloads, so don't try to reconstruct complete bodies from logcat. Records over `LOGCAT_MAX_JSON_CHARS` are skipped on this channel entirely; the JSONL file still receives them.

To tail the capture file live (LSPosed implies root):

```bash
adb shell 'su -c "tail -F /data/data/<pkg>/files/_okhttp_capture.jsonl"' | jq .
```

To tail TLS secrets live:

```bash
adb shell 'su -c "tail -F /data/data/<pkg>/files/_tls_keylog.log"'
```

## Hook installation order

Per host app, on `handleLoadPackage`:

1. **Shaded-OkHttp discovery** — `ShadedOkHttpDiscovery` runs a two-step ladder:
   1. Stock fast path — load canonical `okhttp3.*` FQNs.
   2. Pivot on `okhttp3.internal.http.RealInterceptorChain` (which survives heavy R8) to recover `Request` / `Response` / other OkHttp types by reflection.
2. **OkHttp hooks** (four-level fallback, first-one-wins):
   1. `okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain$okhttp` (OkHttp 4)
   2. `okhttp3.RealCall.getResponseWithInterceptorChain` (OkHttp 3)
   3. `RealInterceptorChain.proceed(Request)` (survives rename — works even on fully obfuscated builds)
   4. `Exchange.writeRequestHeaders` (request-only telemetry)
3. **Cronet** — `CronetUrlRequestHook` + `CronetBidirectionalStreamHook` + `CronetUploadDataProviderHook`, each matching canonical `org.chromium.net.impl.*` classes.
4. **gRPC** — `GrpcCallInstaller` hooks `ClientCallImpl.start` / `sendMessage`; the listener is wrapped via `GrpcListenerProxy`.
5. **`HttpURLConnection`** — `HttpURLConnectionHook` tees the I/O streams.
6. **Higher-level open clients** — Volley, Fuel, Apache HttpClient 5, Ktor CIO, and AndroidAsync install independently when their classes are present.
7. **TLS key log** — Conscrypt/JSSE and canonical Cronet/BoringSSL key log hooks install independently. Cronet key log injection uses the same canonical Cronet resolver as the Cronet app-layer hooks.

Any layer that doesn't apply is logged and skipped; the others install independently.

## Build

```
./gradlew :app:assembleDebug
```

Requires JDK 17+ and a modern Android SDK (compileSdk set in `app/build.gradle`). The APK lands in `app/build/outputs/apk/debug/`.

## Install

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

Enable the module in LSPosed and add the target app(s) to its scope. The module declares `xposed_scope = *`, so you choose the scope from LSPosed.

Signed release builds need a keystore — use `generate-keystore-config.sh` with env vars `KEYSTORE` (base64), `STORE_PASSWORD`, `KEY_PASSWORD`, then `./gradlew :app:assembleRelease`.

## Test

```
./gradlew :app:testDebugUnitTest
```

Unit tests cover the pure-Java parts (body accumulators, header extraction, shaded-OkHttp discovery, metrics, tee streams, gRPC metadata parsing). Xposed-linked hook installers are covered only indirectly on the JVM since the Xposed API is `compileOnly`.

## Known limits

* **gRPC streaming** — only the first request message and first response message are captured; additional messages in client-streaming or server-streaming calls are ignored.
* **Proto payloads** — emitted as base64 blobs (`[grpc-proto base64:...]`) with no schema decoding.
* **Application-layer socket protocols** — anything that isn't HTTP/HTTP/2 (protobuf-over-TCP frameworks, XMPP, SIP, WebRTC signaling, etc.) is out of scope.
* **HTTP/2 SSLSocket fallback** — intentionally not implemented; the cost/benefit vs the existing layers doesn't justify the complexity of HPACK + h2 frame reassembly.
* **TLS bypass** — intentionally not implemented by default. If a target app pins certificates, prefer the app-layer hooks and `_tls_keylog.log`; generic SSL bypass changes target behavior and still will not decode custom application encryption.
