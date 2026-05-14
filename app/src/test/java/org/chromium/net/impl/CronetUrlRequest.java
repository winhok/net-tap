package org.chromium.net.impl;

import java.nio.ByteBuffer;

public class CronetUrlRequest {
    public CronetUrlRequest(String url) {
    }

    public void onResponseStarted(int code, String message, String[] headers,
                                  Object unused, String protocol, String proxy) {
    }

    public void onRedirectReceived(String url, int code, String message, String[] headers,
                                   Object unused, String protocol, String proxy) {
    }

    public void onSucceeded() {
    }

    public void onError(int error, int internalError, int quicError, String message) {
    }

    public void onCanceled() {
    }

    public void onReadCompleted(ByteBuffer buffer, int bytesRead, int initialPosition,
                                int initialLimit) {
    }

    public void setHttpMethod(String method) {
    }
}
