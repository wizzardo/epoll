package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferWrapper;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public abstract class ReadableData {

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
        return byteBuffer.get();
    }

    public static ByteBufferWrapper getThreadLocalByteBuffer() {
        return byteBuffer.get();
    }

    public int getByteBufferOffset() {
        return 0;
    }

    public abstract int read(ByteBuffer byteBuffer);

    public abstract void unread(int i);

    public abstract boolean isComplete();

    public abstract long complete();

    public abstract long length();

    public abstract long remains();
}
