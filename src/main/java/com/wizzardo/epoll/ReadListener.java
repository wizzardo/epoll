package com.wizzardo.epoll;

import java.io.IOException;

public interface ReadListener<C extends Connection> {
    void onRead(C connection, ByteBufferProvider bufferProvider) throws IOException;
}
