package com.wizzardo.epoll;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 3/15/14
 */
class ByteBufferWrapper {

    final ByteBuffer buffer;

    final long address;

    ByteBufferWrapper(ByteBuffer buffer) {
        this.buffer = buffer;
        address = EpollCore.getAddress(buffer);
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
}
