package com.wizzardo.epoll;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author: wizzardo
 * Date: 8/3/14
 */
public class EpollInputStream extends InputStream {

    protected Connection connection;
    protected int offset;
    protected int limit;
    protected byte[] buffer;
    protected long contentLength = -1;
    protected long read = 0;
    protected volatile boolean waiting = false;
    protected volatile Thread thread;

    public EpollInputStream(Connection connection, byte[] buffer) {
        this(connection, buffer, 0, 0, -1);
    }

    public EpollInputStream(Connection connection, byte[] buffer, int currentOffset, int currentLimit) {
        this(connection, buffer, currentOffset, currentLimit, -1);
    }

    public EpollInputStream(Connection connection, byte[] buffer, int currentOffset, int currentLimit, long contentLength) {
        this.connection = connection;
        this.buffer = buffer;
        offset = currentOffset;
        limit = currentLimit;
        this.contentLength = contentLength;
        thread = Thread.currentThread();
    }

    public boolean isFinished() {
        return (contentLength > 0 && read >= contentLength) || limit == -1 || !connection.isAlive();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        if (isFinished())
            return -1;

        if (available() == 0)
            fillBuffer();

        if (limit < 0)
            return -1;

        if (limit == 0)
            return 0;

        int l = Math.min(len, limit - offset);
        System.arraycopy(buffer, offset, b, off, l);
        offset += l;

        read += l;

        return l;
    }

    @Override
    public int available() {
        return limit - offset;
    }

    @Override
    public int read() throws IOException {
        if (available() == 0)
            fillBuffer();

        if (available() == 0)
            return -1;

        read++;
        return buffer[offset++] & 0xff;
    }

    protected void fillBuffer() throws IOException {
        ByteBufferProvider bufferProvider = ByteBufferProvider.current();
        fillBuffer(bufferProvider);
        waitForData(bufferProvider);
    }

    private int fillBuffer(ByteBufferProvider bufferProvider) throws IOException {
        if (contentLength > 0)
            limit = connection.read(buffer, 0, Math.min(buffer.length, (int) (contentLength - read)), bufferProvider);
        else
            limit = connection.read(buffer, bufferProvider);
        offset = 0;

        bufferProvider.getBuffer().clear();
        return limit;
    }

    protected void waitForData(ByteBufferProvider bufferProvider) throws IOException {
        if (limit == 0) {
            if (Thread.currentThread() instanceof IOThread)
                throw new IllegalStateException("IOThread cannot be used in " + this.getClass().getSimpleName());

            if (waiting = fillBuffer(bufferProvider) == 0) {
                if (!connection.isAlive())
                    throw new IOException();

                synchronized (this) {
                    while (waiting = fillBuffer(bufferProvider) == 0) {
                        try {
//                            System.out.println("waitForData.wait " + connection + ", remains: " + (contentLength - read));
                            this.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }
    }

    public void wakeUp() {
        synchronized (this) {
            if (waiting) {
                waiting = false;
                this.notifyAll();
            }
        }
    }
}
