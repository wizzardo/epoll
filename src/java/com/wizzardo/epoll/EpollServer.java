package com.wizzardo.epoll;

import com.wizzardo.epoll.readable.ReadableBytes;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: wizzardo
 * Date: 11/5/13
 */
public abstract class EpollServer<T extends Connection> extends Thread {
    //  gcc -m32 -shared -fpic -o ../../../../../libepoll-server_x32.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollServer.c
    //  gcc      -shared -fpic -o ../../../../../libepoll-server_x64.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollServer.c
    //  javah -jni com.wizzardo.epoll.EpollServer

    private volatile boolean running = true;
    private volatile long scope;
    private long ttl = 30000;
    private ConcurrentHashMap<Integer, T> connections = new ConcurrentHashMap<Integer, T>();
    private LinkedList<T> timeouts = new LinkedList<T>();
    private static ThreadLocal<ByteBuffer> byteBuffer = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocateDirect(50 * 1024);
        }

        @Override
        public ByteBuffer get() {
            ByteBuffer bb = super.get();
            bb.clear();
            return bb;
        }
    };

    static {
        try {
            loadLib("libepoll-server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadLib(String name) {
        String arch = System.getProperty("os.arch");
        name = name + (arch.contains("64") ? "_x64" : "_x32") + ".so";
        // have to use a stream
        InputStream in = EpollServer.class.getResourceAsStream("/" + name);

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                long now = System.currentTimeMillis();
                int[] descriptors = waitForEvents(500);
                for (int i = 0; i < descriptors.length; i += 2) {
                    final int fd = descriptors[i];
                    int event = descriptors[i + 1];
                    T connection = null;
                    switch (event) {
                        case 0: {
                            connection = createConnection(fd, descriptors[i + 2], descriptors[i + 3]);
                            putConnection(connection);
                            onOpenConnection(connection);
                            i += 2;
                            break;
                        }
                        case 1: {
                            connection = getConnection(fd);
                            readyToRead(connection);
                            break;
                        }
                        case 2: {
                            connection = getConnection(fd);
                            readyToWrite(connection);
                            break;
                        }
                        case 3: {
                            connection = getConnection(fd);
                            onCloseConnection(connection);
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
                        onCloseConnection(connection);
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

    private void putConnection(T connection) {
        connections.put(connection.fd, connection);
    }

    private T getConnection(int fd) {
        return connections.get(fd);
    }

    private T deleteConnection(int fd) {
        return connections.remove(fd);
    }

    abstract protected T createConnection(int fd, int ip, int port);

    public abstract void readyToRead(T connection);

    public abstract void readyToWrite(T connection);

    public abstract void onOpenConnection(T connection);

    public abstract void onCloseConnection(T connection);

    public boolean bind(int port, int maxEvents) {
        scope = listen(String.valueOf(port), maxEvents);
        return true;
    }

    public boolean bind(int port) {
        return bind(port, 100);
    }

    private native long listen(String port, int maxEvents);

    private native boolean stopListening(long scope);

    private native int[] waitForEvents(long scope, int timeout);

    public int[] waitForEvents(int timeout) {
        return waitForEvents(scope, timeout);
    }

    public int[] waitForEvents() {
        return waitForEvents(scope, -1);
    }

    public void startWriting(T connection) {
        startWriting(scope, connection.fd);
    }

    private native void startWriting(long scope, int fd);

    public void stopWriting(T connection) {
        stopWriting(scope, connection.fd);
    }

    private native void stopWriting(long scope, int fd);

    public void close(T connection) {
        close(connection.fd);
    }

    private native void close(int fd);

    public int read(T connection, byte[] b, int offset, int length) throws IOException {
        ByteBuffer bb = read(connection, length);
        int r = bb.limit();
        bb.get(b, offset, r);
        return r;
    }

    public ByteBuffer read(T connection, int length) throws IOException {
        ByteBuffer bb = byteBuffer.get();
        int l = Math.min(length, bb.limit());
        int r = read(connection.fd, bb, 0, l);
        if (r > 0)
            bb.position(r);
        bb.flip();
        return bb;
    }

    public int write(T connection, byte[] b, int offset, int length) throws IOException {
        ByteBuffer bb = byteBuffer.get();
        int l = Math.min(length, bb.limit());
        bb.put(b, offset, l);
        return write(connection.fd, bb, 0, l);
    }

    public int write(T connection, ReadableBytes readable) throws IOException {
        ByteBuffer bb = byteBuffer.get();
        int r = readable.read(bb);
        int written = write(connection.fd, bb, 0, r);
        if (written != r)
            readable.unread(r - written);

        return written;
    }

    private native int read(int fd, ByteBuffer b, int off, int len) throws IOException;

    private native int write(int fd, ByteBuffer b, int off, int len) throws IOException;

}
