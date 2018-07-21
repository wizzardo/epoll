package com.wizzardo.epoll;

public interface OutputListener<C extends Connection> {
    void onReadyToWrite(C connection, ByteBufferProvider bufferProvider);

    default void onReady(C connection, ByteBufferProvider bufferProvider) {
    }
}
