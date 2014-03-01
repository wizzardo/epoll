package com.wizzardo.epoll.sized;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public class FixedSizeWritableByteArray implements Writable {
    private byte[] data;
    private int offset = 0;

    public FixedSizeWritableByteArray(int size) {
        data = new byte[size];
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        if (length + this.offset > data.length)
            throw new IllegalArgumentException("too much data: " + length);

        System.arraycopy(bytes, offset, data, this.offset, length);
        this.offset += length;
    }

    public void write(ByteBuffer bb) {
        if (bb.limit() + this.offset > data.length)
            throw new IllegalArgumentException("too much data: " + bb.limit());
        int l = bb.limit();
        bb.get(data, offset, l);
        offset += l;
    }

    public boolean isComplete() {
        return offset == data.length;
    }

    public int getRemaining() {
        return data.length - offset;
    }

    public int size() {
        return data.length;
    }

    public byte[] getData() {
        return data;
    }
}
