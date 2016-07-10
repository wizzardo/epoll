package com.wizzardo.epoll;

import java.io.*;
import java.net.InetAddress;
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
    protected volatile boolean running = true;
    protected volatile boolean started = false;
    protected final ByteBufferWrapper buffer = new ByteBufferWrapper(ByteBuffer.allocateDirect(16 * 1024));
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    private int ioThreadsCount = Runtime.getRuntime().availableProcessors();
    protected SslConfig sslConfig;
    long ttl = 30000;

    private IOThread[] ioThreads;

    static {
        boolean supported = false;
        try {
            loadLib("libepoll-core");
            supported = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        SUPPORTED = supported;
    }

    public EpollCore() {
        this(100);
    }

    public EpollCore(int maxEvents) {
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
                    i += 5;
                    switch (event) {
                        case 0: {
                            acceptConnections(newConnections, now);
                            break;
                        }
                        default:
                            throw new IllegalStateException("this thread only for accepting new connections, event: " + event);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < ioThreads.length; i++) {
            ioThreads[i].stopEpoll();
        }
    }

    public void setTTL(long milliseconds) {
        ttl = milliseconds;
    }

    public long getTTL() {
        return ttl;
    }

    public void stopEpoll() {
        running = false;
        stopListening(scope);
        try {
            join();
        } catch (InterruptedException ignored) {
        }
    }

    private Long acceptConnections(byte[] buffer, Long eventTime) throws IOException {
        events.position(0);
        int k = acceptConnections(scope);
        events.limit(k);
        events.get(buffer, 0, k);
//        eventCounter.addAndGet(k / 10);
        for (int j = 0; j < k; j += 10) {
            int fd = readInt(buffer, j);
            T connection = createConnection(fd, readInt(buffer, j + 4), readShort(buffer, j + 8));
            putConnection(connection, eventTime++);
        }
        return eventTime;
    }

    private void putConnection(T connection, Long eventTime) throws IOException {
        ioThreads[connection.fd % ioThreadsCount].putConnection(connection, eventTime);
    }

    public T connect(String host, int port) throws IOException {
        boolean resolve = !IP_PATTERN.matcher(host).matches();
        if (resolve) {
            InetAddress address = InetAddress.getByName(host);
            host = address.getHostAddress();
        }
        T connection = createConnection(connect(scope, host, port), 0, port);
        connection.setIpString(host);
        synchronized (this) {
            putConnection(connection, System.nanoTime() * 1000);
        }
        return connection;
    }

    protected boolean bind(String host, int port) {
        listen(scope, host, String.valueOf(port));
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
                        try {
                            connection.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
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

    protected IOThread<T> createIOThread(int number, int divider) {
        return new IOThread<T>(number, divider);
    }

    long createSSL(int fd) {
        return createSSL(scope, fd);
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

    native void initSSL(long scope);

    native long createSSL(long scope, int fd);

    native void closeSSL(long ssl);

    native boolean acceptSSL(long ssl);

    native void releaseSslContext(long scope);

    native void loadCertificates(long scope, String certFile, String keyFile);

    private native long init(int maxEvents, ByteBuffer events);

    private native void listen(long scope, String host, String port);

    private native boolean stopListening(long scope);

    private native int waitForEvents(long scope, int timeout);

    private native int acceptConnections(long scope);

    private native int connect(long scope, String host, int port);

    private native boolean mod(long scope, int fd, int mode);

    native int read(int fd, long bbPointer, int off, int len) throws IOException;

    native int write(int fd, long bbPointer, int off, int len) throws IOException;

    native int readSSL(int fd, long bbPointer, int off, int lenm, long ssl) throws IOException;

    native int writeSSL(int fd, long bbPointer, int off, int len, long ssl) throws IOException;

    native static long getAddress(ByteBuffer buffer);

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

        copy(src, srcPos, dest, destPos, length);
    }

    private native static void copy(ByteBuffer src, int srcPos, ByteBuffer dest, int destPos, int length);

    private static void loadLib(String name) {
        String arch = System.getProperty("os.arch");
        name = name + (arch.contains("64") ? "_x64" : "_x32") + ".so";
        // have to use a stream
        InputStream in = EpollCore.class.getResourceAsStream("/" + name);

        File fileOut = null;
        try {
            if (in == null) {
                File file = new File(name);
                if (file.exists())
                    in = new FileInputStream(file);
                else
                    in = new FileInputStream(new File("build/" + name));

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
