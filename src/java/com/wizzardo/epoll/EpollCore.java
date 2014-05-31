package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableBytes;

import java.io.*;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.wizzardo.epoll.Utils.readInt;
import static com.wizzardo.epoll.Utils.readShort;

/**
 * @author: wizzardo
 * Date: 11/5/13
 */
public class EpollCore<T extends Connection> extends Thread {
    //  gcc -m32 -shared -fpic -o ../../../../../libepoll-core_x32.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  gcc      -shared -fpic -o ../../../../../libepoll-core_x64.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  javah -jni com.wizzardo.epoll.EpollCore

    private static final Pattern IP_PATTERN = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    private volatile boolean running = true;
    private volatile long scope;
    private long ttl = 30000;
    private T[] connections;
    private LinkedHashMap<Long, T> timeouts = new LinkedHashMap<Long, T>();

    private static ThreadLocal<ByteBufferWrapper> byteBuffer = new ThreadLocal<ByteBufferWrapper>() {
        @Override
        protected ByteBufferWrapper initialValue() {
            return new ByteBufferWrapper(ByteBuffer.allocateDirect(50 * 1024));
        }

        @Override
        public ByteBufferWrapper get() {
            ByteBufferWrapper bb = super.get();
            bb.buffer.clear();
            return bb;
        }
    };
    private ByteBuffer events;

    static {
        try {
            loadLib("libepoll-core");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public EpollCore() {
        this(100);
    }

    public EpollCore(int maxEvents) {
        events = ByteBuffer.allocateDirect((maxEvents + 500) * 11);
        scope = init(maxEvents, events);
    }

//    protected AtomicInteger eventCounter = new AtomicInteger(0);

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
        byte[] newConnections = new byte[this.events.capacity()];

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
                    T connection = null;
                    switch (event) {
                        case 0: {
                            now = acceptConnections(newConnections, now);
                            continue;
                        }
                        case 1: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                close(fd);
                                continue;
                            } else
                                onRead(connection);
                            break;
                        }
                        case 2: {
                            connection = getConnection(fd);
                            if (connection == null) {
                                close(fd);
                                continue;
                            } else
                                onWrite(connection);
                            break;
                        }
                        case 3: {
                            connection = getConnection(fd);
                            deleteConnection(fd);
                            if (connection == null)
                                continue;
                            connection.setIsAlive(false);
                            close(fd);
                            onDisconnect(connection);
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

    public void setTTL(long milliseconds) {
        ttl = milliseconds;
    }

    public long getTTL() {
        return ttl;
    }

    public void stopServer() {
        running = false;
        stopListening(scope);
    }

    protected void putConnection(T connection) {
        if (connections == null || connections.length <= connection.fd) {
            T[] array = (T[]) Array.newInstance(connection.getClass(), connection.fd * 3 / 2);
            if (connections != null)
                System.arraycopy(connections, 0, array, 0, connections.length);
            connections = array;
        }
        connections[connection.fd] = connection;
    }

    private Long acceptConnections(byte[] buffer, Long eventTime) {
        events.position(0);
        int k = acceptConnections(scope);
        events.limit(k);
        events.get(buffer, 0, k);
//        eventCounter.addAndGet(k / 10);
        for (int j = 0; j < k; j += 10) {
            int fd = readInt(buffer, j);
            T connection = createConnection(fd, readInt(buffer, j + 4), readShort(buffer, j + 8));
            putConnection(connection);
            onConnect(connection);
            connection.setLastEvent(eventTime);
            timeouts.put(eventTime++, connection);
        }
        return eventTime;
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
            if (entry.getValue().isInvalid(eventTime)) {
                connection = deleteConnection(entry.getValue().fd);
                if (connection != null)
                    close(connection);
            }
        }
    }

    private T getConnection(int fd) {
        return connections[fd];
    }

    private T deleteConnection(int fd) {
        T connection = connections[fd];
        connections[fd] = null;
        return connection;
    }

    public T connect(String host, int port) throws UnknownHostException {
        boolean resolve = !IP_PATTERN.matcher(host).matches();
        if (resolve) {
            InetAddress address = InetAddress.getByName(host);
            host = address.getHostAddress();
        }
        T connection = createConnection(connect(scope, host, port), 0, port);
        connection.setIpString(host);
        putConnection(connection);
        onConnect(connection);
        return connection;
    }

    protected boolean bind(String host, int port) {
        scope = listen(scope, host, String.valueOf(port));
        return true;
    }

    protected int waitForEvents(int timeout) {
        return waitForEvents(scope, timeout);
    }

    protected int waitForEvents() {
        return waitForEvents(scope, -1);
    }

    void startWriting(Connection connection) {
        if (connection.isAlive() && !connection.isWritingMode()) {
            startWriting(scope, connection.fd);
            connection.setWritingMode(true);
        }
    }

    void stopWriting(Connection connection) {
        if (connection.isAlive() && connection.isWritingMode()) {
            stopWriting(scope, connection.fd);
            connection.setWritingMode(false);
        }
    }

    public void close(T connection) {
        connection.setIsAlive(false);
        close(connection.fd);
        onDisconnect(connection);
    }

    public int read(T connection, byte[] b, int offset, int length) throws IOException {
        ByteBuffer bb = read(connection, length);
        int r = bb.limit();
        bb.get(b, offset, r);
        return r;
    }

    public ByteBuffer read(T connection, int length) throws IOException {
        ByteBufferWrapper bb = byteBuffer.get();
        int l = Math.min(length, bb.limit());
        int r = connection.isAlive() ? read(connection.fd, bb.address, 0, l) : -1;
        if (r > 0)
            bb.position(r);
        bb.flip();
        return bb.buffer;
    }

    public int write(T connection, byte[] b, int offset, int length) throws IOException {
        int written = -1;
        if (connection.isAlive()) {
            ByteBufferWrapper bb = byteBuffer.get();
            int l = Math.min(length, bb.limit());
            bb.put(b, offset, l);
            synchronized (connection) {
                if (connection.isAlive())
                    written = write(connection.fd, bb.address, 0, l);
            }
        }
        return written;
    }

    int write(T connection, ReadableBytes readable) throws IOException {
        ByteBufferWrapper bb = byteBuffer.get();
        int r = readable.read(bb.buffer);
        int written = -1;
        if (connection.isAlive()) {
            written = write(connection.fd, bb.address, 0, r);
            if (written != r)
                readable.unread(r - written);
        }

        return written;
    }

    public void onRead(T connection) {
    }

    public void onWrite(T connection) {
        connection.write();
    }

    public void onConnect(T connection) {
    }

    public void onDisconnect(T connection) {
    }

    //    protected abstract T createConnection(int fd, int ip, int port);
    protected T createConnection(int fd, int ip, int port) {
        return (T) new Connection(this, fd, ip, port);
    }

    private native long init(int maxEvents, ByteBuffer events);

    private native long listen(long scope, String host, String port);

    private native boolean stopListening(long scope);

    private native int waitForEvents(long scope, int timeout);

    private native int acceptConnections(long scope);

    private native int connect(long scope, String host, int port);

    private native void close(int fd);

    private native void stopWriting(long scope, int fd);

    private native void startWriting(long scope, int fd);

    private native int read(int fd, long bbPointer, int off, int len) throws IOException;

    private native int write(int fd, long bbPointer, int off, int len) throws IOException;

    native static long getAddress(ByteBuffer buffer);

    private static void loadLib(String name) {
        String arch = System.getProperty("os.arch");
        name = name + (arch.contains("64") ? "_x64" : "_x32") + ".so";
        // have to use a stream
        InputStream in = EpollCore.class.getResourceAsStream("/" + name);

        File fileOut = null;
        try {
            if (in == null) {
                in = new FileInputStream(name);
            }
            fileOut = File.createTempFile(name, "lib");
            OutputStream out = new FileOutputStream(fileOut);
            int r;
            byte[] b = new byte[1024];
            while ((r = in.read(b)) != -1) {
                out.write(b, 0, r);
            }
            in.close();
            out.close();
            System.load(fileOut.toString());
            fileOut.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
