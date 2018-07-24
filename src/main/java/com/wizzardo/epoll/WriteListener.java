package com.wizzardo.epoll;

import java.io.IOException;

public interface WriteListener<C extends Connection> {
    void onWrite(C connection, ByteBufferProvider bufferProvider) throws IOException;
}
