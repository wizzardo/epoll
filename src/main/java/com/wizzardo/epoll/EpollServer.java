package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/8/14
 */
public class EpollServer<T extends Connection> extends EpollCore<T> {

    protected volatile String networkInterface = "0.0.0.0";
    protected volatile int port = 8080;

    public EpollServer() {
        super(100);
    }

    public EpollServer(int port) {
        this(null, port);
    }

    public EpollServer(String networkInterface, int port) {
        this(networkInterface, port, 100);
    }

    public EpollServer(String networkInterface, int port, int maxEvents) {
        super(maxEvents);

        this.networkInterface = normalizeNetworkInterface(networkInterface);
        this.port = port;
    }

    @Override
    public synchronized void start() {
        bind(networkInterface, port);
        super.start();
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    public int getPort() {
        return port;
    }

    public void setNetworkInterface(String networkInterface) {
        checkIfStarted();
        this.networkInterface = normalizeNetworkInterface(networkInterface);
    }

    public void setPort(int port) {
        checkIfStarted();
        this.port = port;
    }

    protected String normalizeNetworkInterface(String networkInterface) {
        return networkInterface == null ? "0.0.0.0" : networkInterface;
    }

    protected void checkIfStarted() {
        if (started)
            throw new IllegalStateException("Server is already started");
    }
}
