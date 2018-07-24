package com.wizzardo.epoll;

import java.io.IOException;

public interface DisconnectListener<C extends Connection> {
    void onDisconnect(C connection, ByteBufferProvider bufferProvider) throws IOException;
}
