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

    private static ThreadLocal<ByteBufferWrapper> byteBuffer = new ThreadLocal<ByteBufferWrapper>() {
        @Override
        protected ByteBufferWrapper initialValue() {
            return new ByteBufferWrapper(ByteBuffer.allocateDirect(50 * 1024));
        }

        @Override
        public ByteBufferWrapper get() {
            ByteBufferWrapper bb = super.get();
            bb.clear();
            return bb;
        }
    };

    public ByteBufferWrapper getByteBuffer() {
        return getThreadLocalByteBuffer();
    }

    public static ByteBufferWrapper getThreadLocalByteBuffer() {
        return byteBuffer.get();
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
