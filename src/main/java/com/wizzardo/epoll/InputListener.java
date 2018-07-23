package com.wizzardo.epoll;

import java.io.Closeable;
import java.io.IOException;

public interface InputListener<C extends Connection> extends Closeable {
    void onReadyToRead(C connection, ByteBufferProvider bufferProvider) throws IOException;

    default void onReady(C connection, ByteBufferProvider bufferProvider) throws IOException {
    }

    default void close() throws IOException {
    }
}
