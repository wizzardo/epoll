package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferWrapper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public abstract class ReadableData implements Closeable {

    public ByteBufferWrapper getByteBuffer() {
        return null;
    }

    public int getByteBufferOffset() {
        return 0;
    }

    public void close() throws IOException {
    }

    public abstract int read(ByteBuffer byteBuffer);

    public abstract void unread(int i);

    public abstract boolean isComplete();

    public abstract long complete();

    public abstract long length();

    public abstract long remains();
}
