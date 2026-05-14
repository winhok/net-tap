package io.grpc.internal;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class ClientCallImpl {
    private final MethodDescriptor methodDescriptor = new MethodDescriptor();
    private final String authority = "example.com";

    public void start(ClientCall.Listener listener, Metadata metadata) {
    }

    public void sendMessage(Object message) {
    }
}
