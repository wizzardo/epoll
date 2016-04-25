package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferProvider;
import com.wizzardo.epoll.ByteBufferWrapper;
import com.wizzardo.epoll.EpollCore;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 7/25/14
 */
public class ReadableByteBuffer extends ReadableData {

    protected ByteBufferWrapper buffer;
    protected int start, end, position;


    public ReadableByteBuffer(ByteBufferWrapper bufferWrapper) {
        buffer = bufferWrapper;
        end = bufferWrapper.capacity();
        position = 0;
        start = 0;
    }

    public ReadableByteBuffer(ByteBuffer buffer) {
        this(new ByteBufferWrapper(buffer));
    }

    @Override
    public ByteBufferWrapper getByteBuffer(ByteBufferProvider bufferProvider) {
        return buffer;
    }

    @Override
    public boolean hasOwnBuffer() {
        return true;
    }

    @Override
    public int read(ByteBuffer bb) {
        if (bb != buffer.buffer()) {
            if (bb.isDirect()) {
                int r = Math.min(bb.remaining(), end - position);
                EpollCore.arraycopy(buffer.buffer(), position, bb, bb.position(), r);
                bb.position(r + bb.position());
                position += r;
                return r;
            } else {
                byte[] bytes = bb.array();
                int offset = bb.arrayOffset();
                buffer.offset(position);
                int l = bytes.length - offset;
                l = Math.min(l, end - position);
                buffer.buffer().position(position);
                buffer.buffer().get(bytes, offset, l);
                position += l;
                bb.position(bb.position() + l);
                return l;
            }
        } else {
            buffer.offset(position);
            int r = end - position;
            position = end;
            return r;
        }
    }

    @Override
    public void unread(int i) {
        if (i < 0)
            throw new IllegalArgumentException("can't unread negative value: " + i);
        if (position - i < start)
            throw new IllegalArgumentException("can't unread value bigger than offset (" + start + "): " + i);
        position -= i;
    }

    @Override
    public boolean isComplete() {
        return end == position;
    }

    @Override
    public long complete() {
        return position - start;
    }

    @Override
    public long length() {
        return end - start;
    }

    @Override
    public long remains() {
        return end + start - position;
    }

    public ReadableByteBuffer subBuffer(int offset) {
        return subBuffer(offset, end - offset);
    }

    public ReadableByteBuffer subBuffer(int offset, int length) {
        ReadableByteBuffer bb = copy();
        if (offset + length + start > end)
            throw new IndexOutOfBoundsException("offset+length must be <= current length: " + (start + offset + length) + " <= " + end);
        if (offset < 0)
            throw new IllegalArgumentException("offset must be >= 0: " + offset);
        if (length < 0)
            throw new IllegalArgumentException("length must be >= 0: " + length);
        bb.start = start + offset;
        bb.position = bb.start;
        bb.end = bb.start + length;
        return bb;
    }

    public ReadableByteBuffer copy() {
        return new ReadableByteBuffer(buffer);
    }
}
