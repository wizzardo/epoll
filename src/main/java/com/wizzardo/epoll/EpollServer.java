package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/8/14
 */
public class EpollServer<T extends Connection> extends EpollCore<T> {

    protected volatile String hostname = "0.0.0.0";
    protected volatile int port = 8080;

    public EpollServer() {
        super(100);
    }

    public EpollServer(int port) {
        this(null, port);
    }

    public EpollServer(String hostname, int port) {
        this(hostname, port, 100);
    }

    public EpollServer(String hostname, int port, int maxEvents) {
        super(maxEvents);

        this.hostname = normalizeHostname(hostname);
        this.port = port;
    }

    @Override
    public synchronized void start() {
        bind(hostname, port);
        super.start();
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public void setHostname(String hostname) {
        checkIfStarted();
        this.hostname = normalizeHostname(hostname);
    }

    public void setPort(int port) {
        checkIfStarted();
        this.port = port;
    }

    protected String normalizeHostname(String hostname) {
        return hostname == null ? "0.0.0.0" : hostname;
    }

    protected void checkIfStarted() {
        if (isStarted())
            throw new IllegalStateException("Server is already started");
    }
}
