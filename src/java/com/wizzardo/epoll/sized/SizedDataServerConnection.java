package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.Connection;
import com.wizzardo.epoll.readable.ReadableData;
import com.wizzardo.epoll.readable.ReadableData;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author: wizzardo
 * Date: 3/2/14
 */
public class SizedDataServerConnection extends Connection<SizedDataServer> {

    public SizedDataServerConnection(SizedDataServer epoll, int fd, int ip, int port) {
        super(epoll, fd, ip, port);
    }

    public void read(int read, int total) {
    }

    @Override
    public void write(ReadableData readable) {
        if (sending == null)
            synchronized (this) {
                if (sending == null)
                    sending = new ConcurrentLinkedQueue<ReadableData>();
            }

        sending.add(readable);
        epoll.createTaskToSendData(this);
    }
}
