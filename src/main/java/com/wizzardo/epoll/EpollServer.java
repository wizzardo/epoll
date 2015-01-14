package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/8/14
 */
public class EpollServer<T extends Connection> extends EpollCore<T> {

    private String host;
    private int port;

    public EpollServer(int port) {
        this(null, port);
    }

    public EpollServer(String host, int port) {
        this(host, port, 100);
    }

    public EpollServer(String host, int port, int maxEvents) {
        super(maxEvents);
        bind(host, port);

        this.host = host == null ? "0.0.0.0" : host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
