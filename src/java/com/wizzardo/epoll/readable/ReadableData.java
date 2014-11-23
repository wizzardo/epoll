package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferProvider;
import com.wizzardo.epoll.ByteBufferWrapper;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public abstract class ReadableData implements Closeable {

    public ByteBufferWrapper getByteBuffer(ByteBufferProvider bufferProvider) {
        return bufferProvider.getBuffer();
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
