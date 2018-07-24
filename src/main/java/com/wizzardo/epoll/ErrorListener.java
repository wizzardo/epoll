package com.wizzardo.epoll;

import java.io.IOException;

public interface ErrorListener<C extends Connection> {
    void onError(C connection, Exception e, ByteBufferProvider bufferProvider) throws IOException;
}
