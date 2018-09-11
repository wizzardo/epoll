package com.wizzardo.epoll;

import com.wizzardo.tools.misc.Unchecked;
import com.wizzardo.tools.reflection.FieldReflection;
import com.wizzardo.tools.reflection.FieldReflectionFactory;
import com.wizzardo.tools.reflection.UnsafeTools;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

import static com.wizzardo.epoll.Utils.readInt;
import static com.wizzardo.epoll.Utils.readShort;

/**
 * @author: wizzardo
 * Date: 11/5/13
 */
public class EpollCore<T extends Connection> extends Thread implements ByteBufferProvider {
    //  gcc -m32 -shared -fpic -o ../../../../../libepoll-core_x32.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  gcc      -shared -fpic -o ../../../../../libepoll-core_x64.so -I /home/moxa/soft/jdk1.6.0_45/include/ -I /home/moxa/soft/jdk1.6.0_45/include/linux/ EpollCore.c
    //  javah -jni com.wizzardo.epoll.EpollCore

    public static final boolean SUPPORTED;

    ByteBuffer events;
    volatile long scope;
    volatile long sslContextPointer;
    protected volatile boolean running = true;
    protected volatile boolean started = false;
    protected final ByteBufferWrapper buffer = new ByteBufferWrapper(ByteBuffer.allocateDirect(16 * 1024));
    protected static final Pattern IP_PATTERN = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    protected int ioThreadsCount = Runtime.getRuntime().availableProcessors();
    protected SslConfig sslConfig;
    protected long ttl = 30000;

    private IOThread[] ioThreads;

    static {
        boolean supported = false;
        try {
            Utils.loadLib("libepoll-core");
            supported = true;
            System.out.println("epoll-core lib loaded");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("epoll is not supported");
        }
        SUPPORTED = supported;
    }

    public EpollCore() {
        this(100);
    }

    public EpollCore(int maxEvents) {
        initEpoll(maxEvents);
    }

    protected void initEpoll(int maxEvents) {
        events = ByteBuffer.allocateDirect((maxEvents + 500) * 11);
        scope = init(maxEvents, events);
    }

    public void setIoThreadsCount(int ioThreadsCount) {
        this.ioThreadsCount = ioThreadsCount;
    }

    public boolean isStarted() {
        return started;
    }

//    protected AtomicInteger eventCounter = new AtomicInteger(0);

    @Override
    public void run() {
        started = true;
        System.out.println("io threads count: " + ioThreadsCount);
        ioThreads = new IOThread[ioThreadsCount];
        for (int i = 0; i < ioThreadsCount; i++) {
            ioThreads[i] = createIOThread(i, ioThreadsCount);
            ioThreads[i].setTTL(ttl);
            ioThreads[i].loadCertificates(sslConfig);
            ioThreads[i].start();
        }

        ByteBuffer eventsBuffer = this.events;
        byte[] events = new byte[eventsBuffer.capacity()];
        byte[] newConnections = new byte[eventsBuffer.capacity()];

        if (ioThreadsCount == 0) {
            IOThread<? extends T> ioThread = createIOThread(1, 1);
            ioThreadsCount = 1;
            ioThread.scope = scope;
            ioThreads = new IOThread[]{ioThread};
            ioThreads[0].setTTL(ttl);
            ioThreads[0].loadCertificates(sslConfig);

            long prev = System.nanoTime();
            while (running) {
                try {
                    eventsBuffer.position(0);
                    long now = System.nanoTime() * 1000;
                    long nowMinusSecond = now - 1_000_000_000_000L; // 1 sec
                    int r = waitForEvents(500);
                    eventsBuffer.limit(r);
                    eventsBuffer.get(events, 0, r);
                    int i = 0;
                    while (i < r) {
                        int event = events[i];
                        if (event == 0) {
                            acceptConnections(newConnections);
                        } else {
                            int fd = readInt(events, i + 1);
                            ioThread.handleEvent(fd, event, now, nowMinusSecond);
                        }
                        i += 5;
                    }
                    if (nowMinusSecond > prev) {
                        ioThread.handleTimeOuts(now);
                        prev = now;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        while (running) {
            try {
                eventsBuffer.position(0);
                int r = waitForEvents(500);
                eventsBuffer.limit(r);
                eventsBuffer.get(events, 0, r);
                int i = 0;
                while (i < r) {
                    int event = events[i];
                    i += 5;
                    if (event == 0)
                        acceptConnections(newConnections);
                    else
                        throw new IllegalStateException("this thread only for accepting new connections, event: " + event);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < ioThreads.length; i++) {
            ioThreads[i].close();
        }
    }

    public void setTTL(long milliseconds) {
        ttl = milliseconds;
    }

    public long getTTL() {
        return ttl;
    }

    public void close() {
        synchronized (this) {
            if (running) {
                running = false;
                stopListening(scope);
                try {
                    join();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void acceptConnections(byte[] buffer) throws IOException {
        events.position(0);
        int k = acceptConnections(scope);
        events.limit(k);
        events.get(buffer, 0, k);
//        eventCounter.addAndGet(k / 10);
        for (int j = 0; j < k; j += 10) {
            int fd = readInt(buffer, j);
            T connection = createConnection(fd, readInt(buffer, j + 4), readShort(buffer, j + 8));
            putConnection(connection);
        }
    }

    private void putConnection(T connection) throws IOException {
        ioThreads[connection.fd % ioThreadsCount].putConnection(connection);
    }

    public T connect(String host, int port) throws IOException {
        return connect(host, port, this::createConnection);
    }

    public T connect(String host, int port, ConnectionFactory<T> factory) throws IOException {
        boolean resolve = !IP_PATTERN.matcher(host).matches();
        if (resolve) {
            InetAddress address = InetAddress.getByName(host);
            host = address.getHostAddress();
        }
        T connection;
        if (Thread.currentThread() instanceof IOThread) {
            IOThread ioThread = (IOThread) Thread.currentThread();
            int fd = ioThread.connect(scope, host, port, ioThread.divider, ioThread.number);
            connection = factory.create(fd, 0, port);
            ioThread.putConnection(connection);
        } else {
            connection = factory.create(connect(scope, host, port, 1, 0), 0, port);
            putConnection(connection);
        }
        connection.setIpString(host);
        return connection;
    }

    protected boolean bind(String host, int port) {
        try {
            listen(scope, host, String.valueOf(port));
        } catch (Exception e) {
            throw Unchecked.rethrow(e);
        }
        return true;
    }

    protected int waitForEvents(int timeout) {
        return waitForEvents(scope, timeout);
    }

    protected int waitForEvents() {
        return waitForEvents(scope, -1);
    }

    void mod(Connection connection, int mode) {
        if (connection.isAlive() && connection.getMode() != mode) {
            synchronized (connection) {
                if (connection.isAlive() && connection.getMode() != mode)
                    if (!mod(scope, connection.fd, mode))
                        connection.close();
                    else
                        connection.setMode(mode);
            }
        }
    }

    @Override
    public ByteBufferWrapper getBuffer() {
        return buffer;
    }

    protected T createConnection(int fd, int ip, int port) {
        return (T) new Connection(fd, ip, port);
    }

    protected IOThread<? extends T> createIOThread(int number, int divider) {
        return new IOThread<T>(number, divider);
    }

    long createSSL(int fd) {
        return EpollSSL.createSSL(sslContextPointer, fd);
    }

    public void loadCertificates(String certFile, String keyFile) {
        loadCertificates(new SslConfig(certFile, keyFile));
    }

    public void loadCertificates(SslConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    protected boolean isSecured() {
        return sslConfig != null;
    }

    native void close(int fd);

    native boolean attach(long scope, int fd);

    private native long init(int maxEvents, ByteBuffer events);

    private native void listen(long scope, String host, String port) throws IOException;

    private native boolean stopListening(long scope);

    private native int waitForEvents(long scope, int timeout);

    private native int acceptConnections(long scope);

    native int connect(long scope, String host, int port, int divider, int number);

    private native boolean mod(long scope, int fd, int mode);

    native int read(int fd, long bbPointer, int off, int len) throws IOException;

    native int write(int fd, long bbPointer, int off, int len) throws IOException;

    private native static long getAddress(ByteBuffer buffer);

    private static final FieldReflection byteBufferAddressReflection = getByteBufferAddressReflection();

    private static FieldReflection getByteBufferAddressReflection() {
        try {
            return new FieldReflectionFactory().create(Buffer.class, "address", true);
        } catch (NoSuchFieldException e) {
            throw Unchecked.rethrow(e);
        }
    }

    static long address(ByteBuffer buffer) {
        return SUPPORTED ? getAddress(buffer) : byteBufferAddressReflection.getLong(buffer);
    }

    public static void arraycopy(ByteBuffer src, int srcPos, ByteBuffer dest, int destPos, int length) {
        if (length < 0)
            throw new IndexOutOfBoundsException("length must be >= 0. (length = " + length + ")");
        if (srcPos < 0)
            throw new IndexOutOfBoundsException("srcPos must be >= 0. (srcPos = " + srcPos + ")");
        if (destPos < 0)
            throw new IndexOutOfBoundsException("destPos must be >= 0. (destPos = " + destPos + ")");
        if (srcPos + length > src.capacity())
            throw new IndexOutOfBoundsException("srcPos + length must be <= src.capacity(). (srcPos = " + srcPos + ", length = " + length + ", capacity = " + src.capacity() + ")");
        if (destPos + length > dest.capacity())
            throw new IndexOutOfBoundsException("destPos + length must be <= dest.capacity(). (destPos = " + destPos + ", length = " + length + ", capacity = " + dest.capacity() + ")");

        if (SUPPORTED) {
            copy(src, srcPos, dest, destPos, length);
        } else {
            UnsafeTools.getUnsafe().copyMemory(address(src) + srcPos, address(dest) + destPos, length);
        }
    }

    private native static void copy(ByteBuffer src, int srcPos, ByteBuffer dest, int destPos, int length);

    public static void copy(ByteBufferWrapper src, int srcPos, ByteBufferWrapper dest, int destPos, int length) {
        copyMemory(src.address + srcPos, dest.address + destPos, length);
    }

    private static native void copyMemory(long srcPos, long dest, int length);

    public static void copyInto(ByteBufferWrapper dest, int destPos,
                                ByteBufferWrapper src1,
                                ByteBufferWrapper src2,
                                ByteBufferWrapper src3,
                                ByteBufferWrapper src4,
                                ByteBufferWrapper src5
    ) {
        copyInto5(dest.address + destPos,
                src1.address, src1.capacity(),
                src2.address, src2.capacity(),
                src3.address, src3.capacity(),
                src4.address, src4.capacity(),
                src5.address, src5.capacity()
        );
    }

    public static void copyInto(ByteBufferWrapper dest, int destPos,
                                ByteBufferWrapper src1,
                                ByteBufferWrapper src2,
                                ByteBufferWrapper src3,
                                ByteBufferWrapper src4) {
        copyInto4(dest.address + destPos,
                src1.address, src1.capacity(),
                src2.address, src2.capacity(),
                src3.address, src3.capacity(),
                src4.address, src4.capacity());
    }

    public static void copyInto(ByteBufferWrapper dest, int destPos,
                                ByteBufferWrapper src1,
                                ByteBufferWrapper src2,
                                ByteBufferWrapper src3) {
        copyInto3(dest.address + destPos,
                src1.address, src1.capacity(),
                src2.address, src2.capacity(),
                src3.address, src3.capacity());
    }

    public static void copyInto(ByteBufferWrapper dest, int destPos,
                                ByteBufferWrapper src1,
                                ByteBufferWrapper src2) {
        copyInto2(dest.address + destPos,
                src1.address, src1.capacity(),
                src2.address, src2.capacity());
    }

    private static native void copyInto5(long dest, long s1, int l1, long s2, int l2, long s3, int l3, long s4, int l4, long s5, int l5);

    private static native void copyInto4(long dest, long s1, int l1, long s2, int l2, long s3, int l3, long s4, int l4);

    private static native void copyInto3(long dest, long s1, int l1, long s2, int l2, long s3, int l3);

    private static native void copyInto2(long dest, long s1, int l1, long s2, int l2);
}
