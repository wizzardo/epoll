package com.wizzardo.epoll;

import java.io.IOException;

public interface OutputListener<C extends Connection> {
    void onReadyToWrite(C connection, ByteBufferProvider bufferProvider) throws IOException;

    default void onReady(C connection, ByteBufferProvider bufferProvider) throws IOException {
    }
}
