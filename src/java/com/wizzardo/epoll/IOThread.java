package com.wizzardo.epoll;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wizzardo.epoll.Utils.readInt;

/**
 * @author: wizzardo
 * Date: 6/25/14
 */
public class IOThread<T extends Connection> extends EpollCore<T> {
    private final int number;
    private final int divider;
    private T[] connections = (T[]) new Connection[0];
    private Map<Integer, T> newConnections = new ConcurrentHashMap<Integer, T>();
    private LinkedHashMap<Long, T> timeouts = new LinkedHashMap<Long, T>();
    private AtomicInteger connectionsCounter = new AtomicInteger();

    public IOThread(int number, int divider) {
        this.number = number;
        this.divider = divider;
        setName("IOThread-" + number);
    }

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
//        System.out.println("start new ioThread");

        while (running) {
            this.events.position(0);
            Long now = System.nanoTime() * 1000;
            int r = waitForEvents(500);
//                System.out.println("events length: "+r);
            this.events.limit(r);
            this.events.get(events, 0, r);
            int i = 0;
            while (i < r) {
                int event = events[i];
                i++;
                int fd = readInt(events, i);
//                    System.out.println("event on fd " + fd + ": " + event);
                i += 4;
                T connection = getConnection(fd);
                if (connection == null) {
                    close(fd);
                    continue;
                }

                try {
                    switch (event) {
                        case 1: {
                            onRead(connection);
                            break;
                        }
                        case 2: {
                            onWrite(connection);
                            break;
                        }
                        case 3: {
                            connection.close();
                            continue;
                        }
                        default:
                            throw new IllegalStateException("this thread only for read/write/close events, event: " + event);
                    }
                } catch (Exception e) {
                    onError(connection, e);
                }

                Long key = connection.setLastEvent(now);
//                System.out.println("update timeout for " + connection + " set " + now + " at " + System.currentTimeMillis());
                timeouts.put(now++, connection);
                if (key != null)
                    timeouts.remove(key);
            }

            handleTimeOuts(now);
        }
    }

    private void handleTimeOuts(Long eventTime) {
        eventTime -= ttl * 1000000L * 1000;
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
                if (connection != null)
                    try {
//                        System.out.println("close by timeout: " + connection + "\t\tnow: " + eventTime + "\t" + System.currentTimeMillis());
//                        System.out.println("closed: " + entry.getValue() + "\tlast event was: " + entry.getValue().getLastEvent());
                        connection.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
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

    protected void putConnection(T connection, Long eventTime) {
        newConnections.put(connection.fd, connection);
        connection.setIOThread(this);
        connection.setLastEvent(eventTime);
        if (attach(scope, connection.fd)) {
            safeOnConnect(connection);
            connectionsCounter.incrementAndGet();
        } else {
            newConnections.remove(connection.fd);
            close(connection.fd);
        }
    }

    void close(T connection) {
        connection.setIsAlive(false);
        close(connection.fd);
        connectionsCounter.decrementAndGet();
        onDisconnect(connection);
        deleteConnection(connection.fd);
    }

    public int getConnectionsCount() {
        return connectionsCounter.get();
    }

    private void safeOnConnect(T connection) {
        try {
            onConnect(connection);
        } catch (Exception e) {
            onError(connection, e);
        }
    }

    public void onError(T connection, Exception e) {
        e.printStackTrace();
    }

    public void onRead(T connection) {
    }

    public void onWrite(T connection) {
        connection.write(getBuffer());
    }

    public void onConnect(T connection) {
    }

    public void onDisconnect(T connection) {
    }
}
