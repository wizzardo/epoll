package com.wizzardo.epoll.readable;

import com.wizzardo.epoll.ByteBufferProvider;
import com.wizzardo.epoll.ByteBufferWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: wizzardo
 * Date: 7/25/14
 */
public class ReadableBuilder extends ReadableData {
    protected static final ReadableData[] EMPTY_ARRAY = new ReadableData[0];

    protected ReadableData[] parts;
    protected int partsCount = 0;
    protected int position = 0;

    public ReadableBuilder() {
        this(20);
    }

    public ReadableBuilder(int initSize) {
        parts = initSize > 0 ? new ReadableData[initSize] : EMPTY_ARRAY;
    }

    public ReadableBuilder(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ReadableBuilder(byte[] bytes, int offset, int length) {
        this(new ReadableByteArray(bytes, offset, length));
    }

    public ReadableBuilder(ReadableData data) {
        this();
        partsCount = 1;
        parts[0] = data;
    }

    public ReadableBuilder reset() {
        int partsCount = this.partsCount;
        ReadableData[] parts = this.parts;
        for (int i = 0; i < partsCount; i++) {
            parts[i] = null;
        }
        position = 0;
        this.partsCount = 0;
        return this;
    }

    public ReadableBuilder append(byte[] bytes) {
        return append(bytes, 0, bytes.length);
    }

    public ReadableBuilder append(byte[] bytes, int offset, int length) {
        if (partsCount + 1 >= parts.length)
            increaseSize();

        parts[partsCount] = new ReadableByteArray(bytes, offset, length);
        partsCount++;

        return this;
    }

    public ReadableBuilder append(ReadableData readableData) {
        if (partsCount + 1 >= parts.length)
            increaseSize();

        parts[partsCount] = readableData;
        partsCount++;

        return this;
    }

    protected void increaseSize() {
        ReadableData[] temp = new ReadableData[Math.max(partsCount * 3 / 2, 2)];
        System.arraycopy(parts, 0, temp, 0, partsCount);
        parts = temp;
    }

    @Override
    public ByteBufferWrapper getByteBuffer(ByteBufferProvider bufferProvider) {
        return parts[position].getByteBuffer(bufferProvider);
    }

    @Override
    public int read(ByteBuffer byteBuffer) {
        if (position >= partsCount)
            return 0;

        ReadableData[] parts = this.parts;
        ReadableData data = parts[position];
        int r = data.read(byteBuffer);
        while (position < partsCount - 1 && data.isComplete()) {
            if (data.hasOwnBuffer()) {
                position++;
                break;
            }
            data = parts[++position];
            if (!byteBuffer.hasRemaining() || data.hasOwnBuffer())
                break;
            r += data.read(byteBuffer);
        }

        return r;
    }

    @Override
    public void unread(int i) {
        ReadableData[] parts = this.parts;
        while (i > 0) {
            ReadableData part = parts[position];
            int unread = (int) Math.min(i, part.complete());
            part.unread(unread);
            i -= unread;
            if (i > 0)
                position--;
        }
    }

    @Override
    public boolean isComplete() {
        return position == partsCount - 1 && parts[position].isComplete();
    }

    @Override
    public long complete() {
        long l = 0;
        ReadableData[] parts = this.parts;
        int position = this.position;
        for (int i = 0; i <= position; i++) {
            ReadableData part = parts[i];
            if (parts[i].isComplete())
                l += parts[i].length();
            else
                l += part.complete();
        }
        return l;
    }

    @Override
    public long length() {
        long l = 0;
        ReadableData[] parts = this.parts;
        int partsCount = this.partsCount;
        for (int i = 0; i < partsCount; i++) {
            l += parts[i].length();
        }
        return l;
    }

    @Override
    public long remains() {
        long l = 0;
        ReadableData[] parts = this.parts;
        for (int i = partsCount - 1; i >= 0; i--) {
            if (parts[i].isComplete())
                break;
            l += parts[i].remains();
        }
        return l;
    }

    @Override
    public void close() throws IOException {
        ReadableData[] parts = this.parts;
        int partsCount = this.partsCount;
        for (int i = 0; i < partsCount; i++) {
            parts[i].close();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partsCount; i++) {
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
