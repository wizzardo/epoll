package com.wizzardo.epoll.sized;

import com.wizzardo.epoll.Connection;
import com.wizzardo.epoll.EpollServer;
import com.wizzardo.epoll.IOThread;
import com.wizzardo.tools.io.BytesTools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: wizzardo
 * Date: 2/27/14
 */
public abstract class SizedDataServer<T extends SizedDataServerConnection> extends EpollServer<T> {
    private Map<Connection, FixedSizeWritableByteArray> reading = new ConcurrentHashMap<Connection, FixedSizeWritableByteArray>();

    public SizedDataServer(int port) {
        this(null, port);
    }

    public SizedDataServer(String host, int port) {
        super(host, port);
    }


    @Override
    protected T createConnection(int fd, int ip, int port) {
        return (T) new SizedDataServerConnection(fd, ip, port);
    }

    @Override
    protected IOThread createIOThread(int number, int divider) {
        return new SizedIOThread(number, divider);
    }

    protected abstract void handleData(T connection, byte[] data);

    private class SizedIOThread extends IOThread<T> {

        public SizedIOThread(int number, int divider) {
            super(number, divider);
        }

        @Override
        public void onRead(final T connection) {
//        System.out.println("onRead: " + connection);
            FixedSizeWritableByteArray r = reading.get(connection);
            if (r == null) {
                r = new FixedSizeWritableByteArray(4);
                reading.put(connection, r);
            }
            if (!r.isComplete()) {
                try {
                    ByteBuffer bb = null;
                    while ((bb == null || bb.limit() > 0) && r.remaining() > 0) {
                        bb = connection.read(r.remaining(), this);
//                    System.out.println("remaining: "+r.remaining());
                        r.write(bb);
//                    System.out.println("read: " + bb.limit() + "\t" + r.offset() + "/" + r.length());
                        if (r.length() > 4)
                            connection.read(r.offset(), r.length());
                    }
                } catch (IOException e) {
                    try {
                        connection.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
            if (r.isComplete()) {
                if (r.length() == 4) {
                    reading.put(connection, new FixedSizeWritableByteArray(BytesTools.toInt(r.getData())));
                    onRead(connection);
                } else {
                    reading.remove(connection);
                    final byte[] data = r.getData();
                    handleData(connection, data);
                }
            }
        }

        @Override
        public void onWrite(T connection) {
            connection.write(this);
        }

        @Override
        public void onConnect(T connection) {
        }

        @Override
        public void onDisconnect(T connection) {
            reading.remove(connection);
        }
    }
}
