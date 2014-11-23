package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferProvider;
import com.wizzardo.epoll.ByteBufferWrapper;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 7/25/14
 */
public class ReadableByteBuffer extends ReadableData {

    protected ByteBufferWrapper buffer;
    protected int length, position;


    public ReadableByteBuffer(ByteBufferWrapper bufferWrapper) {
        buffer = bufferWrapper;
        length = bufferWrapper.capacity();
        position = 0;
    }

    public ReadableByteBuffer(ByteBuffer buffer) {
        this(new ByteBufferWrapper(buffer));
    }

    @Override
    public ByteBufferWrapper getByteBuffer(ByteBufferProvider bufferProvider) {
        return buffer;
    }

    @Override
    public int read(ByteBuffer bb) {
        if (bb != buffer.buffer())
            throw new IllegalStateException("can't write data to separate buffer");
        buffer.offset(position);
        int r = length - position;
        position = length;
        return r;
    }

    @Override
    public void unread(int i) {
        if (i < 0)
            throw new IllegalArgumentException("can't unread negative value: " + i);
        if (position - i < 0)
            throw new IllegalArgumentException("can't unread value bigger than position (" + position + "): " + i);
        position -= i;
    }

    @Override
    public boolean isComplete() {
        return length == position;
    }

    @Override
    public long complete() {
        return position;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long remains() {
        return length - position;
    }

    public ReadableByteBuffer copy() {
        return new ReadableByteBuffer(buffer);
    }
}
