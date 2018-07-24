package com.wizzardo.epoll;

import java.io.IOException;

public interface ConnectListener<C extends Connection> {
    void onConnect(C connection, ByteBufferProvider bufferProvider) throws IOException;
}
