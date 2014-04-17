package com.wizzardo.epoll.readable;

import java.lang.*;
import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public class ReadableByteArray implements ReadableBytes {
    protected byte[] bytes;
    protected int offset, length, position;

    public ReadableByteArray(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ReadableByteArray(byte[] bytes, int offset, int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        position = offset;
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        int r = Math.min(byteBuffer.remaining(), length + offset - position);
        byteBuffer.put(bytes, position, r);
        position += r;
        return r;
    }

    @Override
    public void unread(int i) {
        if (i < 0)
            throw new IllegalArgumentException("can't unread negative value: " + i);
        if (position - i < offset)
            throw new IllegalArgumentException("can't unread value bigger than offset (" + offset + "): " + i);
        position -= i;
    }

    @Override
    public boolean isComplete() {
        return length == position - offset;
    }

    public int remains() {
        return length + offset - position;
    }

    public int processed() {
        return position - offset;
    }
}
