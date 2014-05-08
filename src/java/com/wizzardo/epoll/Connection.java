package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableBytes;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author: wizzardo
 * Date: 1/6/14
 */
public class Connection<T extends EpollCore> {
    protected final int fd;
    protected final int ip, port;
    protected volatile Queue<ReadableBytes> sending;
    protected T epoll;
    private String ipString;
    private long lastEvent;
    private volatile boolean alive = true;

    public Connection(T epoll, int fd, int ip, int port) {
        this.fd = fd;
        this.ip = ip;
        this.port = port;
        this.epoll = epoll;
    }

    public String getIp() {
        if (ipString == null)
            ipString = getIp(ip);
        return ipString;
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

    void setLastEvent(long lastEvent) {
        this.lastEvent = lastEvent;
    }

    long getLastEvent() {
        return lastEvent;
    }

    public boolean isAlive() {
        return alive;
    }

    void setIsAlive(boolean isAlive) {
        alive = isAlive;
    }

    public void write(ReadableBytes readable) {
        if (sending == null)
            synchronized (this) {
                if (sending == null)
                    sending = new ConcurrentLinkedQueue<ReadableBytes>();
            }

        sending.add(readable);
        write();
    }

    public void write() {
        if (sending == null)
            return;

        synchronized (this) {
            ReadableBytes readable;
            try {
                while ((readable = sending.peek()) != null) {
                    while (!readable.isComplete() && epoll.write(this, readable) > 0) {
                    }
                    if (!readable.isComplete()) {
                        epoll.startWriting(this);
                        return;
                    }

                    sending.poll();
                }
            } catch (IOException e) {
                e.printStackTrace();
                epoll.close(this);
            }

            epoll.stopWriting(this);
        }

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
}
