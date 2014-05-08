package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableBytes;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import static com.wizzardo.epoll.Utils.readInt;
import static com.wizzardo.epoll.Utils.readShort;

/**
 * @author: wizzardo
 * Date: 11/5/13
 */
public abstract class EpollCore<T extends Connection> extends Thread {
    //  gcc -m32 -shared -fpic -o ../../../../../libepoll-core_x32.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  gcc      -shared -fpic -o ../../../../../libepoll-core_x64.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  javah -jni com.wizzardo.epoll.EpollCore

    private volatile boolean running = true;
    private volatile long scope;
    private long ttl = 30000;
    private T[] connections;
    private LinkedList<T> timeouts = new LinkedList<T>();
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

    @Override
    public void run() {
        byte[] events = new byte[this.events.capacity()];
        while (running) {
            try {
                this.events.position(0);
                long now = System.currentTimeMillis();
                int r = waitForEvents(500);
//                System.out.println("events length: "+r);
                this.events.limit(r);
                this.events.get(events, 0, r);
                int i = 0;
                while (i < r) {
                    int event = events[i];
                    i++;
                    final int fd = readInt(events, i);
                    i += 4;
                    T connection = null;
                    switch (event) {
                        case 0: {
                            connection = createConnection(fd, readInt(events, i), readShort(events, i + 4));
                            putConnection(connection);
                            onConnect(connection);
                            i += 6;
                            break;
                        }
                        case 1: {
                            connection = getConnection(fd);
                            onRead(connection);
                            break;
                        }
                        case 2: {
                            connection = getConnection(fd);
                            onWrite(connection);
                            break;
                        }
                        case 3: {
                            connection = getConnection(fd);
                            connection.setIsAlive(false);
                            onDisconnect(connection);
                            deleteConnection(fd);
                            break;
                        }
                    }
                    connection.setLastEvent(now);
                    timeouts.add(connection);
                }

                now -= ttl;
                T connection;
                while ((connection = timeouts.peekFirst()) != null && connection.getLastEvent() < now) {
                    connection = deleteConnection(timeouts.removeFirst().fd);
                    if (connection != null) {
                        close(connection);
                        onDisconnect(connection);
                    }
                }

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

    private T getConnection(int fd) {
        return connections[fd];
    }

    private T deleteConnection(int fd) {
        T connection = connections[fd];
        connections[fd] = null;
        return connection;
    }

    public T connect(String host, int port) {
        return createConnection(connect(scope, host, port), 0, port);
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

    public void startWriting(T connection) {
        if (connection.isAlive())
            startWriting(scope, connection.fd);
    }

    public void stopWriting(T connection) {
        if (connection.isAlive())
            stopWriting(scope, connection.fd);
    }

    public void close(T connection) {
        connection.setIsAlive(false);
        close(connection.fd);
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
        ByteBufferWrapper bb = byteBuffer.get();
        int l = Math.min(length, bb.limit());
        bb.put(b, offset, l);
        return write(connection.fd, bb.address, 0, l);
    }

    public int write(T connection, ReadableBytes readable) throws IOException {
        ByteBufferWrapper bb = byteBuffer.get();
        int r = readable.read(bb.buffer);
        int written = connection.isAlive() ? write(connection.fd, bb.address, 0, r) : -1;
        if (written != r)
            readable.unread(r - written);

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

    protected abstract T createConnection(int fd, int ip, int port);

    private native long init(int maxEvents, ByteBuffer events);

    private native long listen(long scope, String host, String port);

    private native boolean stopListening(long scope);

    private native int waitForEvents(long scope, int timeout);

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
