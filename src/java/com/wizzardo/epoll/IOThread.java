package com.wizzardo.epoll;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wizzardo.epoll.Utils.readInt;

/**
 * @author: wizzardo
 * Date: 6/25/14
 */
public class IOThread extends EpollCore<Connection> {
    private static AtomicInteger number = new AtomicInteger();

    private volatile boolean running = true;
    private long ttl = 30000;
    private Connection[] connections;
    private LinkedHashMap<Long, Connection> timeouts = new LinkedHashMap<Long, Connection>();
    private AtomicInteger connectionsCounter = new AtomicInteger();
    private EpollCore parent;

    public IOThread(EpollCore parent) {
        this.parent = parent;
        setName("IOThread_" + number.incrementAndGet());
    }

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
        System.out.println("start new ioThread");

        while (running) {
            try {
                this.events.position(0);
                Long now = System.nanoTime() * 1000;
                int r = waitForEvents(500);
//                System.out.println("events length: "+r);
                this.events.limit(r);
                this.events.get(events, 0, r);
                int i = 0;
//                eventCounter.addAndGet(r / 5);
                while (i < r) {
                    int event = events[i];
                    i++;
                    int fd = readInt(events, i);
//                    System.out.println("event on fd " + fd + ": " + event);
                    i += 4;
                    Connection connection = null;
                    switch (event) {
                        case 1: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                connection.close();
                                continue;
                            } else
                                parent.onRead(connection);
                            break;
                        }
                        case 2: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                connection.close();
                                continue;
                            } else
                                parent.onWrite(connection);
                            break;
                        }
                        case 3: {
                            connection = getConnection(fd);
                            deleteConnection(fd);
                            if (connection == null)
                                continue;
                            connection.close();
                            continue;
                        }
                    }
                    Long key = connection.setLastEvent(now);
                    timeouts.put(now++, connection);
                    if (key != null)
                        timeouts.remove(key);
                }

                handleTimeOuts(now);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private void handleTimeOuts(Long eventTime) {
        eventTime -= ttl * 1000000L * 1000;
        Connection connection;
        Map.Entry<Long, Connection> entry;

        Iterator<Map.Entry<Long, Connection>> iterator = timeouts.entrySet().iterator();
        while (iterator.hasNext()) {
            entry = iterator.next();
            if (entry.getKey() > eventTime)
                break;

            iterator.remove();
            if (entry.getValue().isInvalid(eventTime)) {
                connection = deleteConnection(entry.getValue().fd);
                if (connection != null)
                    close(connection);
            }
        }
    }

    private Connection getConnection(int fd) {
        return connections[fd];
    }

    private Connection deleteConnection(int fd) {
        Connection connection = connections[fd];
        connections[fd] = null;
        return connection;
    }

    protected void putConnection(Connection connection, Long eventTime) {
        if (connections == null || connections.length <= connection.fd) {
//            Connection[] array = (Connection[]) Array.newInstance(connection.getClass(), connection.fd * 3 / 2);
            Connection[] array = new Connection[connection.fd * 3 / 2];
            if (connections != null)
                System.arraycopy(connections, 0, array, 0, connections.length);
            connections = array;
        }
        connections[connection.fd] = connection;
        connectionsCounter.incrementAndGet();

        connection.setIOThread(this);
        attach(scope, connection.fd);
        connection.setLastEvent(eventTime);
    }

    public void close(Connection connection) {
        connection.setIsAlive(false);
        close(connection.fd);
        connectionsCounter.decrementAndGet();
        parent.onDisconnect(connection);
    }

    public int getConnectionsCount() {
        return connectionsCounter.get();
    }
}
