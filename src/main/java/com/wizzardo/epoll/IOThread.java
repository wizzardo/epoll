package com.wizzardo.epoll;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.wizzardo.epoll.Utils.readInt;

/**
 * @author: wizzardo
 * Date: 6/25/14
 */
public class IOThread<T extends Connection> extends EpollCore<T> {
    protected final int number;
    protected final int divider;
    protected T[] connections = (T[]) new Connection[1000];
    protected Map<Integer, T> newConnections = new ConcurrentHashMap<Integer, T>();
    protected LinkedHashMap<Long, T> timeouts = new LinkedHashMap<Long, T>();
    protected Queue<Integer> closingConnections = new ConcurrentLinkedQueue<>();

    public IOThread(int number, int divider) {
        this.number = number;
        this.divider = divider;
        setName("IOThread-" + number);
    }

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
//        System.out.println("start new ioThread");

        long prev = System.nanoTime();
        while (running) {
            this.events.position(0);
            long now = System.nanoTime() * 1000;
            long nowMinusSecond = now - 1_000_000_000_000L;// 1 sec
            int r = waitForEvents(100);
//                System.out.println("events length: "+r);
            this.events.limit(r);
            this.events.get(events, 0, r);
            int i = 0;
            while (i < r) {
                int event = events[i];
                int fd = readInt(events, i + 1);
//                    System.out.println("event on fd " + fd + ": " + event);
                now = handleEvent(fd, event, now, nowMinusSecond);
                i += 5;
            }

            if (nowMinusSecond > prev) {
                handleTimeOuts(now);
                prev = now;
            }

            handleClosingConnections();
        }
        stopListening(scope);
    }

    protected long handleEvent(int fd, int event, long now, long eventTimeoutThreshold) {
        T connection = getConnection(fd);
        if (connection == null) {
            if (event == 3) {
                return now;
            }
            detach(scope, fd);
            close(fd);
            return now;
        }

        try {
            if (connection.getLastEvent() == 0) {
                onAttach(connection);
            }
            if (event == 1) {
                connection.readyToRead = true;
                onRead(connection);
            } else if (event == 4) {
                onWrite(connection);
            } else if (event == 5) {
                onWrite(connection);
                connection.readyToRead = true;
                onRead(connection);
            } else if (event == 3) {
                if (connection.clientMode)
                    onRead(connection);

                connection.close();
                return now;
            } else
                throw new IllegalStateException("this thread only for read/write/close events, event: " + event);
        } catch (Exception e) {
            try {
                onError(connection, e);
            } catch (IOException ignored) {
            }
        }

        if (connection.getLastEvent() > eventTimeoutThreshold)
            return now;

        long key = connection.setLastEvent(now);
//                System.out.println("update timeout for " + connection + " set " + now + " at " + System.currentTimeMillis());
        timeouts.remove(key);
        timeouts.put(now++, connection);
        return now;
    }

    protected void handleTimeOuts(long eventTime) {
        eventTime -= ttl * 1_000_000_000L;
        T connection;
        Map.Entry<Long, T> entry;

        Iterator<Map.Entry<Long, T>> iterator = timeouts.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = iterator.next();
            if (entry.getKey() > eventTime)
                break;

            iterator.remove();
            if (entry.getValue().isAlive() && entry.getValue().isInvalid(eventTime)) {
                connection = deleteConnection(entry.getValue().fd);
                if (connection != null) {
//                        System.out.println("close by timeout: " + connection + "\t\tnow: " + eventTime + "\t" + System.currentTimeMillis());
//                        System.out.println("closed: " + entry.getValue() + "\tlast event was: " + entry.getValue().getLastEvent());
                    connection.close();
                }
            }
        }
    }

    protected void handleClosingConnections() {
        Integer fd;
        while ((fd = closingConnections.poll()) != null) {
            T connection = deleteConnection(fd);
            if (connection != null)
                connection.close();
        }
    }

    protected T getConnection(int fd) {
        T t;
        int index = fd / divider;
        if (index >= connections.length || (t = connections[index]) == null) {
            t = newConnections.remove(fd);
            if (t != null)
                putIntoConnections(t);
        }
        return t;
    }

    private T deleteConnection(int fd) {
        int index = fd / divider;
        T connection = connections[index];
        if (connection != null)
            connections[index] = null;

        return connection;
    }

    protected void putIntoConnections(T connection) {
        int index = connection.fd / divider;
        if (connections.length <= index) {
            int length = Math.max(index * 3 / 2, 16);
            T[] array = (T[]) Array.newInstance(connection.getClass(), length);
            System.arraycopy(connections, 0, array, 0, connections.length);
            connections = array;
        }
        connections[index] = connection;
    }

    protected void putConnection(T connection) {
        newConnections.put(connection.fd, connection);
        connection.setIOThread(this);
        if (!attach(scope, connection.fd)) {
            newConnections.remove(connection.fd);
            connection.close();
        }
    }

    void close(T connection) {
        if (!connection.isAlive())
            return;

        if (connection.clientMode) {
            Thread currentThread = Thread.currentThread();
            if (currentThread != this && (!(currentThread instanceof EpollCore) || ((EpollCore<?>) currentThread).scope != connection.epoll.scope)) {
                closingConnections.add(connection.fd);
                return;
            }
        }

        connection.setIsAlive(false);

        if (connection.ssl != 0) {
            EpollSSL.closeSSL(connection.ssl);
            connection.ssl = 0;
        }

        if (deleteConnection(connection.fd) != null) {
            detach(scope, connection.fd);
        }

        close(connection.fd);
        try {
            onDisconnect(connection);
        } catch (Exception e) {
            try {
                onError(connection, e);
            } catch (IOException ignored) {
            }
        }
    }

    protected void onAttach(T connection) {
        try {
            onConnect(connection);
        } catch (Exception e) {
            try {
                onError(connection, e);
            } catch (IOException ignored) {
            }
        }
    }

    public void onError(T connection, Exception e) throws IOException {
        connection.onError(this, e);
    }

    public void onRead(T connection) throws IOException {
        connection.onRead(this);
    }

    public void onWrite(T connection) throws IOException {
        connection.onWrite(this);
    }

    public void onConnect(T connection) throws IOException {
        connection.onConnect(this);
    }

    public void onDisconnect(T connection) throws IOException {
        connection.onDisconnect(this);
    }

    @Override
    public void close() {
        if (isSecured())
            EpollSSL.releaseSslContext(sslContextPointer);
        super.close();
    }

    protected int write(Connection connection, long bbPointer, int off, int len) throws IOException {
        if (isSecured() || connection.isSecured()) {
            if (!connection.prepareSSL())
                return 0;
            return EpollSSL.writeSSL(connection.fd, bbPointer, off, len, connection.ssl);
        } else
            return write(connection.fd, bbPointer, off, len);
    }

    protected int read(Connection connection, long bbPointer, int off, int len) throws IOException {
        if (isSecured() || connection.isSecured()) {
            if (!connection.prepareSSL())
                return 0;
            return EpollSSL.readSSL(connection.fd, bbPointer, off, len, connection.ssl);
        } else
            return read(connection.fd, bbPointer, off, len);
    }

    @Override
    public void loadCertificates(SslConfig sslConfig) {
        super.loadCertificates(sslConfig);
        if (isSecured()) {
            sslContextPointer = EpollSSL.initSSL();
            EpollSSL.loadCertificates(sslContextPointer, sslConfig.getCertFile(), sslConfig.getKeyFile());
        }
    }
}
