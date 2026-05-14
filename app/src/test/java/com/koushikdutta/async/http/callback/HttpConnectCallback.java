package com.koushikdutta.async.http.callback;

public interface HttpConnectCallback {
    void onConnectCompleted(Exception error, Object response);
}
