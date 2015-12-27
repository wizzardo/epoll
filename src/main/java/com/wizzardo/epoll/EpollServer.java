package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/8/14
 */
public class EpollServer<T extends Connection> extends EpollCore<T> {

    private volatile String host = "0.0.0.0";
    private volatile int port = 8080;

    public EpollServer() {
        super(100);
    }

    public EpollServer(int port) {
        this(null, port);
    }

    public EpollServer(String host, int port) {
        this(host, port, 100);
    }

    public EpollServer(String host, int port, int maxEvents) {
        super(maxEvents);

        this.host = normalizeHost(host);
        this.port = port;
    }

    @Override
    public synchronized void start() {
        bind(host, port);
        super.start();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void setHost(String host) {
        checkIfStarted();
        this.host = normalizeHost(host);
    }

    public void setPort(int port) {
        checkIfStarted();
        this.port = port;
    }

    protected String normalizeHost(String host) {
        return host == null ? "0.0.0.0" : host;
    }

    protected void checkIfStarted() {
        if (started)
            throw new IllegalStateException("Server is already started");
    }
}
