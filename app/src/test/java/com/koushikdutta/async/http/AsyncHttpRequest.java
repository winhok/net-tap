package com.koushikdutta.async.http;

import java.net.URI;
import java.util.Collections;

public class AsyncHttpRequest {
    public URI getUri() {
        return URI.create("https://example.com/async");
    }

    public String getMethod() {
        return "POST";
    }

    public Headers getHeaders() {
        return new Headers();
    }

    public static final class Headers {
        public java.util.Map<String, java.util.Collection<String>> getMultiMap() {
            return Collections.singletonMap("X-Async", Collections.singletonList("yes"));
        }
    }
}
