package io.grpc;

public class ClientCall {
    public interface Listener {
        default void onHeaders(Metadata metadata) {
        }

        default void onMessage(Object message) {
        }

        default void onClose(Object status, Metadata trailers) {
        }
    }
}
