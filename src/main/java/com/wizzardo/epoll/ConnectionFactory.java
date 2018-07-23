package com.wizzardo.epoll;

public interface ConnectionFactory<T extends Connection> {
    T create(int fd, int ip, int port);
}
