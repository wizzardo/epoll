package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.Connection;
import com.wizzardo.epoll.EpollServer;
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
public abstract class SizedDataServer<T extends SizedDataServerConnection> extends EpollServer<T> {
    private ConcurrentHashMap<Connection, Queue<ReadableBytes>> sending = new ConcurrentHashMap<Connection, Queue<ReadableBytes>>();
    private Map<Connection, FixedSizeWritableByteArray> reading = new ConcurrentHashMap<Connection, FixedSizeWritableByteArray>();
    protected ThreadPool threadPool;

    public SizedDataServer(int threads) {
        threadPool = new ThreadPool(threads);
    }

    @Override
    public void readyToRead(final T connection) {
//        System.out.println("readyToRead: " + connection);
        FixedSizeWritableByteArray r = reading.get(connection);
        if (r == null) {
            r = new FixedSizeWritableByteArray(4);
            reading.put(connection, r);
        }
        if (!r.isComplete()) {
            try {
                ByteBuffer bb = null;
                while ((bb == null || bb.limit() > 0) && r.remaining() > 0) {
                    bb = read(connection, r.remaining());
//                    System.out.println("remaining: "+r.remaining());
                    r.write(bb);
//                    System.out.println("read: " + bb.limit() + "\t" + r.offset() + "/" + r.size());
                    if (r.size() > 4)
                        connection.read(r.offset(), r.size());
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
    public void readyToWrite(T connection) {
        Queue<ReadableBytes> l = sending.get(connection);
        if (l == null || l.isEmpty()) {
            stopWriting(connection);
            return;
        }
        createTaskToSendData(connection, l);
    }

    public void send(T connection, ReadableBytes readable) {
        Queue<ReadableBytes> l = sending.get(connection);
        if (l == null) {
            Queue<ReadableBytes> old = sending.putIfAbsent(connection, l = new ConcurrentLinkedQueue<ReadableBytes>());
            if (old != null)
                l = old;
        }

        l.add(readable);
        createTaskToSendData(connection, l);
    }

    private void createTaskToSendData(final T connection, final Queue<ReadableBytes> l) {
        threadPool.add(new Runnable() {
            @Override
            public void run() {
                ReadableBytes readable;
                synchronized (connection) {
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
                            e.printStackTrace();
                            close(connection);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onOpenConnection(T connection) {
    }

    @Override
    public void onCloseConnection(T connection) {
        sending.remove(connection);
        reading.remove(connection);
    }

    @Override
    public void close(T connection) {
        super.close(connection);
        sending.remove(connection);
        reading.remove(connection);
    }

    protected abstract void handleData(T connection, byte[] data);
}
