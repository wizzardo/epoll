package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import com.wizzardo.epoll.readable.ReadableData;
import com.wizzardo.tools.io.IOTools;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author: wizzardo
 * Date: 1/6/14
 */
public class Connection implements Cloneable, Closeable {
    protected static final int EPOLLIN = 0x001;
    protected static final int EPOLLOUT = 0x004;

    protected int fd;
    protected int ip, port;
    protected volatile Deque<ReadableData> sending;
    protected volatile IOThread epoll;
    protected volatile long ssl;
    protected volatile boolean sslAccepted;
    protected final AtomicReference<ByteBufferProvider> writer = new AtomicReference<>();
    protected EpollInputStream inputStream;
    protected EpollOutputStream outputStream;
    protected volatile InputListener<Connection> inputListener;
    protected volatile OutputListener<Connection> outputListener;
    private volatile int mode = 1;
    private volatile boolean alive = true;
    volatile boolean readyToRead = true;
    private String ipString;
    private Long lastEvent;

    public Connection(int fd, int ip, int port) {
        this.fd = fd;
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        if (ipString == null)
            ipString = getIp(ip);
        return ipString;
    }

    void setIpString(String ip) {
        ipString = ip;
    }

    private String getIp(int ip) {
        StringBuilder sb = new StringBuilder();
        sb.append((ip >> 24) + (ip < 0 ? 256 : 0)).append(".");
        sb.append((ip & 16777215) >> 16).append(".");
        sb.append((ip & 65535) >> 8).append(".");
        sb.append(ip & 255);
        return sb.toString();
    }

    public int getPort() {
        return port;
    }

    public boolean isReadyToRead() {
        return readyToRead;
    }

    Long setLastEvent(Long lastEvent) {
        Long last = this.lastEvent;
        this.lastEvent = lastEvent;
        return last;
    }

    Long getLastEvent() {
        return lastEvent;
    }

    public boolean isAlive() {
        return alive;
    }

    protected synchronized void setIsAlive(boolean isAlive) {
        alive = isAlive;
    }

    public boolean write(String s, ByteBufferProvider bufferProvider) {
        try {
            return write(s.getBytes("utf-8"), bufferProvider);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean write(byte[] bytes, ByteBufferProvider bufferProvider) {
        return write(bytes, 0, bytes.length, bufferProvider);
    }

    public boolean write(byte[] bytes, int offset, int length, ByteBufferProvider bufferProvider) {
        return write(new ReadableByteArray(bytes, offset, length), bufferProvider);
    }

    public boolean write(ReadableData readable, ByteBufferProvider bufferProvider) {
        if (sending == null) {
            if (writer.compareAndSet(null, bufferProvider)) {
                try {
                    while (!readable.isComplete() && actualWrite(readable, bufferProvider)) {
                    }

                    if (!readable.isComplete()) {
                        safeCreateQueueIfNotExists();
                        sending.addFirst(readable);
                        return false;
                    }

                    readable.close();
                    readable.onComplete();
                    onWriteData(readable, false);
                } catch (Exception e) {
                    e.printStackTrace();
                    close();
                    return false;
                } finally {
                    writer.set(null);
                }
            } else {
                safeCreateQueueIfNotExists();
                sending.add(readable);
            }
            return write(bufferProvider);
        } else {
            sending.add(readable);
            return write(bufferProvider);
        }
    }

    protected void safeCreateQueueIfNotExists() {
        if (sending == null)
            synchronized (this) {
                if (sending == null)
                    sending = createSendingQueue();
            }
    }

    protected Deque<ReadableData> createSendingQueue() {
        return new ConcurrentLinkedDeque<>();
    }

    /*
     * @return true if connection is ready to write data
     */
    public boolean write(ByteBufferProvider bufferProvider) {
        Queue<ReadableData> queue = this.sending;
        if (queue == null)
            return true;

        while (!queue.isEmpty() && writer.compareAndSet(null, bufferProvider)) {
            try {
                ReadableData readable;
                while ((readable = queue.peek()) != null) {
                    while (!readable.isComplete() && actualWrite(readable, bufferProvider)) {
                    }
                    if (!readable.isComplete())
                        return false;

                    queue.poll();
                    readable.close();
                    readable.onComplete();
                    onWriteData(readable, !queue.isEmpty());
                }
            } catch (Exception e) {
                e.printStackTrace();
                close();
                return false;
            } finally {
                writer.set(null);
            }
        }
        return true;
    }

    protected int write(ByteBufferWrapper wrapper, int off, int len) throws IOException {
        return epoll.write(this, wrapper.address, off, len);
    }

    /*
     * @return true if connection is ready to write data
     */
    protected boolean actualWrite(ReadableData readable, ByteBufferProvider bufferProvider) throws IOException {
        if (!isAlive())
            return false;

        ByteBufferWrapper bb = readable.getByteBuffer(bufferProvider);
        bb.clear();
        int r = readable.read(bb);
        if (r > 0) {
//            int written = epoll.write(this, bb.address, bb.offset(), r);
            int written = write(bb, bb.offset(), r);
            bb.clear();
//            System.out.println("write: " + written + " (" + readable.complete() + "/" + readable.length() + ")" + " to " + this);
            if (written != r) {
                readable.unread(r - written);
                return false;
            }
            return true;
        }
        return false;
    }

    public int read(byte[] b, int offset, int length, ByteBufferProvider bufferProvider) throws IOException {
        ByteBuffer bb = read(length, bufferProvider);
        int r = bb.limit();
        bb.get(b, offset, r);
        return r;
    }

    public ByteBuffer read(int length, ByteBufferProvider bufferProvider) throws IOException {
        ByteBufferWrapper bb = bufferProvider.getBuffer();
        bb.clear();
        int l = Math.min(length, bb.limit());
        int r = isAlive() ? epoll.read(this, bb.address, 0, l) : -1;
        if (r > 0)
            bb.position(r);
        bb.flip();
        readyToRead = r == l;
        return bb.buffer();
    }

    public void close() {
        if (sending != null)
            for (ReadableData data : sending)
                IOTools.close(data);

        if (ssl != 0)
            EpollSSL.closeSSL(ssl);

        epoll.close(this);
    }

    public boolean isSecured() {
        return ssl != 0;
    }

    protected void enableOnWriteEvent() {
        if ((mode & EPOLLIN) != 0)
            epoll.mod(this, EPOLLIN | EPOLLOUT);
        else
            epoll.mod(this, EPOLLOUT);
    }

    protected void disableOnWriteEvent() {
        if ((mode & EPOLLIN) != 0)
            epoll.mod(this, EPOLLIN);
        else
            epoll.mod(this, 0);
    }

    protected void enableOnReadEvent() {
        if ((mode & EPOLLOUT) != 0)
            epoll.mod(this, EPOLLIN | EPOLLOUT);
        else
            epoll.mod(this, EPOLLIN);
    }

    protected void disableOnReadEvent() {
        if ((mode & EPOLLOUT) != 0)
            epoll.mod(this, EPOLLOUT);
        else
            epoll.mod(this, 0);
    }

    public void onWriteData(ReadableData readable, boolean hasMore) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Connection that = (Connection) o;

        if (fd != that.fd) return false;
        if (ip != that.ip) return false;
        if (port != that.port) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = fd;
        result = 31 * result + ip;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return fd + " " + getIp() + ":" + port;
    }

    public int read(byte[] bytes, ByteBufferProvider bufferProvider) throws IOException {
        return read(bytes, 0, bytes.length, bufferProvider);
    }

    boolean isInvalid(Long now) {
        return lastEvent.compareTo(now) <= 0;
    }

    void setMode(int mode) {
        this.mode = mode;
    }

    int getMode() {
        return mode;
    }

    public void setIOThread(IOThread IOThread) {
        epoll = IOThread;
    }

    boolean prepareSSL() {
        if (ssl == 0)
            ssl = epoll.createSSL(fd);

        if (!sslAccepted) {
            synchronized (this) {
                if (!sslAccepted)
                    sslAccepted = EpollSSL.acceptSSL(ssl);
            }
        } else
            return true;

        return sslAccepted;
    }

    public void onRead(ByteBufferProvider bufferProvider) {
        if (inputListener != null)
            inputListener.onReadyToRead(this, bufferProvider);
    }

    public void onWrite(ByteBufferProvider bufferProvider) {
        if (outputListener != null)
            outputListener.onReadyToWrite(this, bufferProvider);

        write(bufferProvider);
    }

    public void onConnect(ByteBufferProvider bufferProvider) {
        if (inputListener != null)
            inputListener.onReady(this, bufferProvider);

        if (outputListener != null)
            outputListener.onReady(this, bufferProvider);
    }

    public void onDisconnect() {
    }

    public void onError(Exception e) {
        e.printStackTrace();
    }

    public InputListener<Connection> getInputListener() {
        return inputListener;
    }

    public OutputListener<Connection> getOutputListener() {
        return outputListener;
    }

    protected EpollInputStream createInputStream(byte[] buffer, int currentOffset, int currentLimit, long contentLength) {
        return new EpollInputStream(this, buffer, currentOffset, currentLimit, contentLength);
    }

    protected EpollOutputStream createOutputStream() {
        return new EpollOutputStream(this);
    }

    public EpollInputStream getInputStream() {
        if (inputStream == null) {
            byte[] buffer = new byte[16 * 1024];
            inputStream = new EpollInputStream(this, buffer);
            inputListener = (connection, bufferProvider) -> inputStream.wakeUp();
        }

        return inputStream;
    }

    public void flushOutputStream() throws IOException {
        if (outputStream != null)
            outputStream.flush();
    }

    public EpollOutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = createOutputStream();
            outputListener = (connection, bufferProvider) -> outputStream.wakeUp();
        }

        return outputStream;
    }

    public void setInputListener(InputListener<Connection> inputListener) {
        this.inputListener = inputListener;
    }

    public void setOutputListener(OutputListener<Connection> outputListener) {
        this.outputListener = outputListener;
    }
}
