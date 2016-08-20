package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import com.wizzardo.epoll.readable.ReadableData;
import com.wizzardo.tools.io.IOTools;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author: wizzardo
 * Date: 1/6/14
 */
public class Connection implements Cloneable, Closeable {
    protected static final int EPOLLIN = 0x001;
    protected static final int EPOLLOUT = 0x004;

    protected final int fd;
    protected final int ip, port;
    protected volatile Queue<ReadableData> sending;
    protected volatile IOThread epoll;
    protected volatile long ssl;
    protected volatile boolean sslAccepted;
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

    public void write(String s, ByteBufferProvider bufferProvider) {
        try {
            write(s.getBytes("utf-8"), bufferProvider);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(byte[] bytes, ByteBufferProvider bufferProvider) {
        write(bytes, 0, bytes.length, bufferProvider);
    }

    public void write(byte[] bytes, int offset, int length, ByteBufferProvider bufferProvider) {
        write(new ReadableByteArray(bytes, offset, length), bufferProvider);
    }

    public void write(ReadableData readable, ByteBufferProvider bufferProvider) {
        if (sending == null)
            synchronized (this) {
                if (sending == null)
                    sending = new ConcurrentLinkedQueue<ReadableData>();
            }

        sending.add(readable);
        write(bufferProvider);
    }

    public void write(ByteBufferProvider bufferProvider) {
        Queue<ReadableData> queue = this.sending;
        if (queue == null)
            return;

        synchronized (this) {
            ReadableData readable;
            try {
                while ((readable = queue.peek()) != null) {
                    while (!readable.isComplete() && actualWrite(readable, bufferProvider)) {
                    }
                    if (!readable.isComplete())
                        return;

                    queue.poll();
                    readable.close();
                    readable.onComplete();
                    onWriteData(readable, !queue.isEmpty());
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    /*
    * @return true if connection ready to write data
    */
    protected boolean actualWrite(ReadableData readable, ByteBufferProvider bufferProvider) throws IOException {
        ByteBufferWrapper bb = readable.getByteBuffer(bufferProvider);
        bb.clear();
        int r = readable.read(bb.buffer());
        if (r > 0 && isAlive()) {
            int written = epoll.write(this, bb.address, bb.offset(), r);
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

    public void close() throws IOException {
        if (sending != null)
            for (ReadableData data : sending)
                IOTools.close(data);

        if (ssl != 0)
            epoll.closeSSL(ssl);

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
                    sslAccepted = epoll.acceptSSL(ssl);
            }
        } else
            return true;

        return sslAccepted;
    }
}
