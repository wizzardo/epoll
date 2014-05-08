package com.wizzardo.epoll;

/**
 * @author: wizzardo
 * Date: 5/8/14
 */
public abstract class EpollServer<T extends Connection> extends EpollCore<T> {

    public EpollServer(int port) {
        this(null, port);
    }

    public EpollServer(String host, int port) {
        this(host, port, 100);
    }

    public EpollServer(String host, int port, int maxEvents) {
        super(maxEvents);
        bind(host, port);
    }
}
