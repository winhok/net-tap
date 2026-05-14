package io.grpc;

public class Metadata {
    private final Object[] namesAndValues = new Object[] {
            "content-type", "application/grpc",
            "trace-bin", new byte[] { 1, 2, 3 }
    };
}
