package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.Connection;

/**
 * @author: wizzardo
 * Date: 3/2/14
 */
public class SizedDataServerConnection extends Connection {

    public SizedDataServerConnection(int fd, int ip, int port) {
        super(fd, ip, port);
    }

    public void read(int read, int total) {
    }
}
