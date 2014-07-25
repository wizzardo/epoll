package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableData;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 3/15/14
 */
public class ByteBufferWrapper {

    private final ByteBuffer buffer;

    public final long address;

    public ByteBufferWrapper(ByteBuffer buffer) {
        if (!buffer.isDirect())
            throw new IllegalArgumentException("byte buffer must be direct");

        this.buffer = buffer;
        address = EpollCore.getAddress(buffer);
    }

    public ByteBufferWrapper(ReadableData data) {
        this.buffer = ByteBuffer.allocateDirect((int) data.length());
        address = EpollCore.getAddress(buffer);
        data.read(buffer);
        flip();
    }

    public ByteBufferWrapper(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ByteBufferWrapper(byte[] bytes, int offset, int length) {
        this.buffer = ByteBuffer.allocateDirect(length);
        address = EpollCore.getAddress(buffer);
        put(bytes, offset, length);
        flip();
    }

    public int limit() {
        return buffer.limit();
    }

    public ByteBufferWrapper position(int r) {
        buffer.position(r);
        return this;
    }

    public ByteBufferWrapper flip() {
        buffer.flip();
        return this;
    }

    public ByteBufferWrapper put(byte[] b, int offset, int l) {
        buffer.put(b, offset, l);
        return this;
    }

    public void clear() {
        buffer.clear();
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public int capacity() {
        return buffer.capacity();
    }
}
