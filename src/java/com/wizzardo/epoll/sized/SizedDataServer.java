package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.*;
import com.wizzardo.epoll.readable.ReadableBytes;
import com.wizzardo.epoll.threadpool.ThreadPool;
import com.wizzardo.tools.io.BytesTools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public class SizedDataServer extends EpollServer<Connection> {
    private ConcurrentHashMap<Connection, Queue<ReadableBytes>> sending = new ConcurrentHashMap<Connection, Queue<ReadableBytes>>();
    private Map<Connection, FixedSizeWritableByteArray> reading = new ConcurrentHashMap<Connection, FixedSizeWritableByteArray>();
    private ThreadPool threadPool;

    public SizedDataServer(int threads) {
        threadPool = new ThreadPool(threads);
    }

    @Override
    protected Connection createConnection(int fd, int ip, int port) {
        return new Connection(fd, ip, port);
    }

    @Override
    public void readyToRead(final Connection connection) {
        FixedSizeWritableByteArray r = reading.get(connection);
        if (r == null) {
            r = new FixedSizeWritableByteArray(4);
        }
        if (!r.isComplete()) {
            try {
                ByteBuffer bb = null;
                while ((bb == null || bb.limit() > 0) && r.getRemaining() > 0) {
                    bb = read(connection, r.getRemaining());
                    r.write(bb);
                }
            } catch (IOException e) {
                close(connection);
            }
        }
        if (r.isComplete()) {
            if (r.size() == 4) {
                reading.put(connection, new FixedSizeWritableByteArray(BytesTools.toInt(r.getData())));
                readyToRead(connection);
            } else {
                reading.remove(connection);
                final byte[] data = r.getData();
                threadPool.add(new Runnable() {
                    @Override
                    public void run() {
                        handleData(connection, data);
                    }
                });
            }
        }
    }

    @Override
    public void readyToWrite(Connection connection) {
        Queue<ReadableBytes> l = sending.get(connection);
        if (l == null || l.isEmpty()) {
            stopWriting(connection);
            return;
        }
        createTaskToSendData(connection, l);
    }

    public void send(Connection connection, ReadableBytes readable) {
        Queue<ReadableBytes> l = sending.get(connection);
        if (l == null) {
            Queue<ReadableBytes> old = sending.putIfAbsent(connection, l = new ConcurrentLinkedQueue<ReadableBytes>());
            if (old != null)
                l = old;
        }

        l.add(readable);
        createTaskToSendData(connection, l);
    }

    private void createTaskToSendData(final Connection connection, final Queue<ReadableBytes> l) {
        threadPool.add(new Runnable() {
            @Override
            public void run() {
                ReadableBytes readable;
                while ((readable = l.peek()) != null) {
                    try {
                        while (!readable.isComplete() && write(connection, readable) > 0) {
                        }
                        if (!readable.isComplete()) {
                            startWriting(connection);
                            return;
                        }

                        l.poll();
                    } catch (IOException e) {
                        close(connection);
                    }
                }
            }
        });
    }

    @Override
    public void onOpenConnection(Connection connection) {
    }

    @Override
    public void onCloseConnection(Connection connection) {
        sending.remove(connection);
        reading.remove(connection);
    }

    @Override
    public void close(Connection connection) {
        super.close(connection);
        sending.remove(connection);
        reading.remove(connection);
    }

    protected void handleData(Connection connection, byte[] data) {
    }
}
