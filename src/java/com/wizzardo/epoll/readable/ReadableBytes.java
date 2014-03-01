package com.wizzardo.epoll.readable;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public interface ReadableBytes {
    public int read(ByteBuffer byteBuffer);

    public void unread(int i);

    public boolean isComplete();
}
