package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferWrapper;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 7/25/14
 */
public class ReadableByteBuffer extends ReadableData {

    protected ByteBufferWrapper buffer;
    protected int offset, length, position;


    public ReadableByteBuffer(ByteBufferWrapper bufferWrapper) {
        this(bufferWrapper, 0, bufferWrapper.capacity());
    }

    public ReadableByteBuffer(ByteBufferWrapper buffer, int offset, int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        position = offset;
    }

    public ReadableByteBuffer(ByteBuffer buffer) {
        this(buffer, 0, buffer.capacity());
    }

    public ReadableByteBuffer(ByteBuffer buffer, int offset, int length) {
        this(new ByteBufferWrapper(buffer), offset, length);
    }

    @Override
    public ByteBufferWrapper getByteBuffer() {
        return buffer;
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        int r = length - (position - offset);
        position = offset + length;
        return r;
    }

    @Override
    public int getByteBufferOffset() {
        return offset + position;
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

    @Override
    public long complete() {
        return position - offset;
    }

    @Override
    public long length() {
        return length;
    }

    public long remains() {
        return length + offset - position;
    }
}
