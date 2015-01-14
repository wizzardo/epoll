package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.readable.ReadableByteArray;
import com.wizzardo.tools.io.BytesTools;

import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 3/1/14
 */
public class ReadableByteArrayWithSize extends ReadableByteArray {

    private ReadableByteArray size;

    public ReadableByteArrayWithSize(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ReadableByteArrayWithSize(byte[] bytes, int offset, int length) {
        super(bytes, offset, length);
        size = new ReadableByteArray(BytesTools.toBytes(length));
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        if (size.isComplete())
            return super.read(byteBuffer);

        int r = size.read(byteBuffer);
        if (!size.isComplete())
            return r;

        return r + super.read(byteBuffer);
    }

    @Override
    public void unread(int i) {
        if (i <= position)
            super.unread(i);
        else {
            i -= position;
            super.unread(position);
            size.unread(i);
        }
    }
}
