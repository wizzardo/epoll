package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableByteArray;
import com.wizzardo.epoll.readable.ReadableData;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author: wizzardo
 * Date: 1/6/14
 */
public class Connection implements Cloneable {
    protected final int fd;
    protected final int ip, port;
    protected volatile Queue<ReadableData> sending;
    protected volatile IOThread epoll;
    private String ipString;
    private Long lastEvent;
    private volatile boolean writingMode = false;
    private volatile boolean alive = true;

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

    synchronized void setIsAlive(boolean isAlive) {
        alive = isAlive;
    }

    public void write(String s) {
        try {
            write(s.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    public void write(byte[] bytes, int offset, int length) {
        write(new ReadableByteArray(bytes, offset, length));
    }

    public void write(ReadableData readable) {
        if (sending == null)
            synchronized (this) {
                if (sending == null)
                    sending = new ConcurrentLinkedQueue<ReadableData>();
            }

        sending.add(readable);
        write();
    }

    public void write() {
        if (sending == null)
            return;

        synchronized (this) {
            ReadableData readable;
            try {
                while ((readable = sending.peek()) != null) {
                    while (!readable.isComplete() && epoll.write(this, readable) > 0) {
                    }
                    if (!readable.isComplete()) {
                        enableOnWriteEvent();
                        return;
                    }

                    onWriteData(sending.poll(), !sending.isEmpty());
                }
                disableOnWriteEvent();
            } catch (Exception e) {
                e.printStackTrace();
                close();
            }
        }
    }

    public void close() {
        epoll.close(this);
    }

    protected void enableOnWriteEvent() {
        epoll.startWriting(this);
    }

    protected void disableOnWriteEvent() {
        epoll.stopWriting(this);
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

    public int read(byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    public int read(byte[] bytes, int offset, int length) throws IOException {
        return epoll.read(this, bytes, offset, length);
    }

    boolean isInvalid(Long now) {
        return lastEvent.compareTo(now) <= 0;
    }

    boolean isWritingMode() {
        return writingMode;
    }

    void setWritingMode(boolean enabled) {
        writingMode = enabled;
    }

    public void setIOThread(IOThread IOThread) {
        epoll = IOThread;
    }
}
