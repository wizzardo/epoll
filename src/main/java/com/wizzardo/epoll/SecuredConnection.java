package com.wizzardo.epoll;

import java.io.IOException;

/**
 * Created by wizzardo on 07.05.15.
 */
public class SecuredConnection extends Connection {

    protected long ssl;
    protected boolean accepted;

    public SecuredConnection(int fd, int ip, int port) {
        super(fd, ip, port);
    }

    @Override
    public void close() throws IOException {
        if (ssl != 0)
            epoll.closeSSL(ssl);
        super.close();
    }

    protected int write(int fd, long bbAddress, int offset, int length) throws IOException {
        return epoll.writeSSL(fd, bbAddress, offset, length, ssl);
    }

    @Override
    protected int read(int fd, long bbAddress, int offset, int length) throws IOException {
        if (ssl == 0)
            ssl = epoll.createSSL(fd);

        if (!accepted && !(accepted = epoll.acceptSSL(ssl)))
            return 0;

        return epoll.readSSL(fd, bbAddress, offset, length, ssl);
    }
}
